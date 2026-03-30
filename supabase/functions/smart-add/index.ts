import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

interface SmartAddRequest {
  name: string;
  list_type: string;
  brand_preference: string;
  categories: string[];
}

interface SmartAddResponse {
  category: string;
  search_tip: string;
}

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

let rateLimitedUntil = 0;

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const body: SmartAddRequest = await req.json();
    const { name, list_type, brand_preference, categories } = body;

    if (!name) {
      return new Response(
        JSON.stringify({ error: "name is required" }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
          status: 400,
        }
      );
    }

    const validCategories =
      categories && categories.length > 0
        ? categories
        : ["Produce", "Dairy", "Meat & Seafood", "Bakery", "Frozen", "Snacks", "Beverages", "Household", "Other"];

    const localCategory = localCategorize(name, validCategories);

    if (localCategory !== "Other") {
      return jsonResponse({ category: localCategory, search_tip: name });
    }

    const apiKey = Deno.env.get("GEMINI_API_KEY");
    if (!apiKey) {
      return jsonResponse({ category: "Other", search_tip: name });
    }

    const now = Date.now();
    if (now < rateLimitedUntil) {
      return jsonResponse({ category: "Other", search_tip: name });
    }

    const brandHint = buildBrandHint(brand_preference || "ALL");
    const prompt = buildPrompt(name, list_type || "GROCERY", validCategories, brandHint);

    const geminiResult = await callGemini(apiKey, prompt);

    if (!geminiResult) {
      return jsonResponse({ category: "Other", search_tip: name });
    }

    let result: SmartAddResponse;
    try {
      const jsonMatch = geminiResult.match(/\{[\s\S]*\}/);
      const jsonStr = jsonMatch ? jsonMatch[0] : geminiResult.trim();
      result = JSON.parse(jsonStr);
    } catch {
      console.error("Failed to parse Gemini JSON:", geminiResult.substring(0, 300));
      result = { category: "Other", search_tip: name };
    }

    if (!result.category || !validCategories.includes(result.category)) {
      result.category = "Other";
    }
    if (!result.search_tip) {
      result.search_tip = name;
    }

    return jsonResponse(result);
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    console.error("Smart-add error:", message);
    return new Response(
      JSON.stringify({ category: "Other", search_tip: "" }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 500,
      }
    );
  }
});

function jsonResponse(data: SmartAddResponse): Response {
  return new Response(JSON.stringify(data), {
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

async function callGemini(apiKey: string, prompt: string): Promise<string | null> {
  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1/models/gemini-3-flash-preview:generateContent?key=${apiKey}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig: {
          temperature: 0.2,
          maxOutputTokens: 100,
        },
      }),
    }
  );

  if (response.status === 429) {
    rateLimitedUntil = Date.now() + 60_000;
    console.error("Gemini 429 -- backing off for 60s");
    return null;
  }

  if (!response.ok) {
    const errBody = await response.text();
    console.error(`Gemini ${response.status}: ${errBody.substring(0, 200)}`);
    return null;
  }

  const data = await response.json();
  const text = data?.candidates?.[0]?.content?.parts?.[0]?.text || "";
  if (!text.trim()) {
    console.error("Gemini returned empty text");
    return null;
  }
  return text;
}

const CATEGORY_KEYWORDS: Record<string, string[]> = {
  "Produce": ["apple", "banana", "orange", "lettuce", "tomato", "potato", "onion", "carrot", "pepper", "cucumber", "broccoli", "celery", "grape", "berry", "berries", "lemon", "lime", "avocado", "spinach", "kale", "mushroom", "garlic", "ginger", "fruit", "vegetable", "salad", "corn", "pear", "peach", "plum", "mango", "pineapple", "melon", "watermelon", "zucchini", "squash", "cabbage", "cauliflower", "asparagus", "green bean", "peas", "radish", "beet", "sweet potato", "yam", "cilantro", "parsley", "dill", "mint", "basil", "thyme", "rosemary", "chive", "green onion"],
  "Dairy": ["milk", "cheese", "yogurt", "yoghurt", "butter", "cream", "egg", "eggs", "sour cream", "cottage", "margarine", "whip", "creamer"],
  "Meat & Seafood": ["chicken", "beef", "pork", "steak", "ground", "salmon", "shrimp", "fish", "bacon", "sausage", "turkey", "lamb", "ham", "ribs", "roast", "chop", "wing", "thigh", "breast", "mince", "tilapia", "cod", "tuna steak", "crab", "lobster", "deli meat", "pepperoni", "salami"],
  "Bakery": ["bread", "bagel", "muffin", "croissant", "bun", "tortilla", "pita", "roll", "cake", "donut", "doughnut", "pie", "pastry", "naan", "wrap", "english muffin", "crumpet"],
  "Deli": ["hummus", "deli", "sliced meat", "olive", "prepared"],
  "Frozen": ["frozen", "pizza", "ice cream", "popsicle", "freezer", "fries"],
  "Canned & Jarred": ["canned", "soup", "beans", "tuna", "tomato sauce", "salsa", "jam", "jelly", "peanut butter", "nutella", "pickles", "olives", "broth", "stock", "chili"],
  "Snacks": ["chips", "crackers", "cookies", "popcorn", "nuts", "granola bar", "chocolate", "candy", "pretzels", "trail mix", "jerky", "gummy"],
  "Beverages": ["juice", "water", "pop", "soda", "coffee", "tea", "beer", "wine", "sparkling", "lemonade", "gatorade", "energy drink", "kombucha", "smoothie"],
  "Condiments & Sauces": ["ketchup", "mustard", "mayo", "mayonnaise", "sauce", "vinegar", "dressing", "soy sauce", "hot sauce", "sriracha", "relish", "bbq", "salad dressing", "olive oil", "cooking oil", "vegetable oil", "sesame oil", "ranch", "teriyaki", "worcestershire", "hoisin", "salsa verde", "pesto", "gravy", "marinade"],
  "Pasta & Grains": ["pasta", "rice", "noodle", "spaghetti", "macaroni", "quinoa", "oat", "flour", "couscous", "barley", "lentil", "bulgur", "fettuccine", "penne", "linguine", "ramen", "udon"],
  "Cereal & Breakfast": ["cereal", "oatmeal", "granola", "pancake", "waffle", "syrup", "cheerios", "corn flakes"],
  "Baking": ["sugar", "baking", "vanilla", "cocoa", "yeast", "baking powder", "baking soda", "icing", "sprinkles", "chocolate chips", "brown sugar", "powdered sugar", "cornstarch"],
  "Spices & Seasonings": ["salt", "pepper", "cinnamon", "oregano", "cumin", "paprika", "spice", "chili powder", "curry", "turmeric", "nutmeg", "bay leaf", "garlic powder", "onion powder", "seasoning"],
  "Household": ["paper towel", "toilet paper", "dish soap", "laundry", "trash bag", "garbage bag", "cleaning", "detergent", "bleach", "sponge", "aluminum foil", "plastic wrap", "ziplock", "ziploc", "light bulb", "battery", "batteries", "paper plate", "napkin"],
  "Personal Care": ["shampoo", "soap", "toothpaste", "deodorant", "razor", "lotion", "tissue", "toothbrush", "floss", "mouthwash", "sunscreen", "band-aid", "bandage", "cotton", "q-tip"],
  "Baby": ["diaper", "formula", "baby food", "wipes", "baby"],
  "Pet": ["dog food", "cat food", "pet", "litter", "kibble", "treat"],
  "Lumber & Building": ["lumber", "plywood", "drywall", "insulation", "2x4", "stud"],
  "Paint & Stain": ["paint", "stain", "primer", "brush", "roller"],
  "Electrical": ["wire", "outlet", "switch", "breaker", "electrical"],
  "Plumbing": ["pipe", "faucet", "valve", "plumbing", "drain"],
  "Tools": ["drill", "saw", "hammer", "screwdriver", "wrench", "level", "tape measure"],
  "Hardware & Fasteners": ["screw", "nail", "bolt", "nut", "washer", "anchor", "hinge", "hook"],
  "Lighting": ["bulb", "lamp", "light", "fixture", "led"],
  "Outdoor & Garden": ["seed", "soil", "mulch", "fertilizer", "garden", "plant", "pot", "hose"],
};

function localCategorize(itemName: string, validCategories: string[]): string {
  const lower = itemName.toLowerCase();
  for (const [category, keywords] of Object.entries(CATEGORY_KEYWORDS)) {
    if (!validCategories.includes(category)) continue;
    for (const keyword of keywords) {
      if (lower.includes(keyword)) return category;
    }
  }
  return "Other";
}

function buildBrandHint(brandPreference: string): string {
  switch (brandPreference) {
    case "GENERIC_PREFERRED":
      return "The user prefers generic or store-brand products. Suggest a search term that favours store brands or generic sizes (e.g., '2% milk 4L' rather than 'Natrel 2% milk').";
    case "BRAND_NAME_ONLY":
      return "The user only wants name-brand products. Suggest a search term using a popular Canadian brand name (e.g., 'Natrel 2% milk' rather than just 'milk').";
    default:
      return "The user has no brand preference. Suggest a general search term.";
  }
}

function buildPrompt(
  itemName: string,
  listType: string,
  categories: string[],
  brandHint: string
): string {
  return `You are a Canadian shopping assistant. A user is adding "${itemName}" to their ${listType.toLowerCase().replace("_", " ")} shopping list.

Pick the single best category from this list: ${JSON.stringify(categories)}
Then generate a refined search term (2-5 words) for Canadian grocery stores.

${brandHint}

Reply with ONLY this JSON (no markdown, no explanation):
{"category": "CategoryName", "search_tip": "search term here"}`;
}
