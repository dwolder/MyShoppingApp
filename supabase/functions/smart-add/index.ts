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

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const apiKey = Deno.env.get("GEMINI_API_KEY");
    if (!apiKey) {
      console.error("GEMINI_API_KEY not set in secrets");
      return new Response(
        JSON.stringify({ error: "GEMINI_API_KEY not configured" }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
          status: 500,
        }
      );
    }

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

    const brandHint = buildBrandHint(brand_preference || "ALL");
    const prompt = buildPrompt(name, list_type || "GROCERY", validCategories, brandHint);

    const geminiResult = await callGeminiWithRetry(apiKey, prompt, 2);

    if (!geminiResult) {
      const fallback = localCategorize(name, validCategories);
      return new Response(
        JSON.stringify({ category: fallback, search_tip: name }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        }
      );
    }

    let result: SmartAddResponse;
    try {
      const jsonMatch = geminiResult.match(/\{[\s\S]*\}/);
      const jsonStr = jsonMatch ? jsonMatch[0] : geminiResult.trim();
      result = JSON.parse(jsonStr);
    } catch {
      console.error("Failed to parse Gemini JSON:", geminiResult.substring(0, 300));
      const fallback = localCategorize(name, validCategories);
      result = { category: fallback, search_tip: name };
    }

    if (!result.category || !validCategories.includes(result.category)) {
      result.category = localCategorize(name, validCategories);
    }
    if (!result.search_tip) {
      result.search_tip = name;
    }

    return new Response(JSON.stringify(result), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
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

async function callGeminiWithRetry(apiKey: string, prompt: string, maxRetries: number): Promise<string | null> {
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    const response = await fetch(
      `https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash:generateContent?key=${apiKey}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: {
            temperature: 0.2,
            maxOutputTokens: 256,
          },
        }),
      }
    );

    if (response.ok) {
      const data = await response.json();
      const text = data?.candidates?.[0]?.content?.parts?.[0]?.text || "";
      if (text.trim()) return text;
      console.error("Gemini returned empty text:", JSON.stringify(data).substring(0, 300));
      return null;
    }

    if (response.status === 429 && attempt < maxRetries) {
      const waitMs = 1000 * (attempt + 1);
      console.error(`Gemini 429, retrying in ${waitMs}ms (attempt ${attempt + 1}/${maxRetries})`);
      await new Promise((r) => setTimeout(r, waitMs));
      continue;
    }

    const errBody = await response.text();
    console.error(`Gemini ${response.status}: ${errBody.substring(0, 300)}`);
    return null;
  }
  return null;
}

const CATEGORY_KEYWORDS: Record<string, string[]> = {
  "Produce": ["apple", "banana", "orange", "lettuce", "tomato", "potato", "onion", "carrot", "pepper", "cucumber", "broccoli", "celery", "grape", "berry", "lemon", "lime", "avocado", "spinach", "kale", "mushroom", "garlic", "ginger", "fruit", "vegetable", "salad"],
  "Dairy": ["milk", "cheese", "yogurt", "butter", "cream", "egg", "eggs", "sour cream", "cottage", "margarine"],
  "Meat & Seafood": ["chicken", "beef", "pork", "steak", "ground", "salmon", "shrimp", "fish", "bacon", "sausage", "turkey", "lamb", "ham"],
  "Bakery": ["bread", "bagel", "muffin", "croissant", "bun", "tortilla", "pita", "roll", "cake", "donut"],
  "Frozen": ["frozen", "pizza", "ice cream"],
  "Canned & Jarred": ["canned", "soup", "beans", "tuna", "tomato sauce", "salsa", "jam", "peanut butter", "nutella"],
  "Snacks": ["chips", "crackers", "cookies", "popcorn", "nuts", "granola bar", "chocolate", "candy"],
  "Beverages": ["juice", "water", "pop", "soda", "coffee", "tea", "beer", "wine"],
  "Condiments & Sauces": ["ketchup", "mustard", "mayo", "mayonnaise", "sauce", "vinegar", "dressing", "soy sauce", "hot sauce"],
  "Pasta & Grains": ["pasta", "rice", "noodle", "spaghetti", "macaroni", "quinoa", "oat", "flour"],
  "Cereal & Breakfast": ["cereal", "oatmeal", "granola", "pancake", "waffle", "syrup"],
  "Baking": ["sugar", "baking", "vanilla", "cocoa", "yeast", "baking powder", "baking soda"],
  "Spices & Seasonings": ["salt", "pepper", "cinnamon", "oregano", "basil", "cumin", "paprika", "spice"],
  "Household": ["paper towel", "toilet paper", "dish soap", "laundry", "trash bag", "garbage bag", "cleaning", "detergent", "bleach", "sponge"],
  "Personal Care": ["shampoo", "soap", "toothpaste", "deodorant", "razor", "lotion", "tissue"],
  "Baby": ["diaper", "formula", "baby food", "wipes"],
  "Pet": ["dog food", "cat food", "pet", "litter"],
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
