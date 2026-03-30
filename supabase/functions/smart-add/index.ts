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

    const geminiResponse = await fetch(
      `https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash:generateContent?key=${apiKey}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: {
            temperature: 0.2,
            maxOutputTokens: 256,
            responseMimeType: "application/json",
          },
        }),
      }
    );

    if (!geminiResponse.ok) {
      const errBody = await geminiResponse.text();
      console.error(`Gemini API error ${geminiResponse.status}: ${errBody.substring(0, 300)}`);
      return new Response(
        JSON.stringify({ category: "Other", search_tip: name }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        }
      );
    }

    const geminiData = await geminiResponse.json();
    const text = geminiData?.candidates?.[0]?.content?.parts?.[0]?.text || "";

    let result: SmartAddResponse;
    try {
      result = JSON.parse(text.trim());
    } catch {
      console.error("Failed to parse Gemini JSON:", text.substring(0, 200));
      result = { category: "Other", search_tip: name };
    }

    if (!result.category || !validCategories.includes(result.category)) {
      result.category = "Other";
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

function buildBrandHint(brandPreference: string): string {
  switch (brandPreference) {
    case "GENERIC_PREFERRED":
      return "The user prefers generic or store-brand products (e.g., No Name, Great Value, Selection, Irresistibles). Suggest a search term that favours store brands or generic sizes (e.g., '2% milk 4L' rather than 'Natrel 2% milk').";
    case "BRAND_NAME_ONLY":
      return "The user only wants name-brand products. Suggest a search term using a popular Canadian brand name (e.g., 'Natrel 2% milk' rather than just 'milk').";
    default:
      return "The user has no brand preference. Suggest a general search term that would find the best variety of results.";
  }
}

function buildPrompt(
  itemName: string,
  listType: string,
  categories: string[],
  brandHint: string
): string {
  return `You are a Canadian shopping assistant. A user is adding "${itemName}" to their ${listType.toLowerCase().replace("_", " ")} shopping list.

Your tasks:
1. Pick the single best category from this exact list: ${JSON.stringify(categories)}
2. Generate a refined search term that will find the best product matches in Canadian stores (Superstore, Metro, FreshCo, Sobeys).

${brandHint}

Respond with ONLY a JSON object in this exact format:
{"category": "Dairy", "search_tip": "2% milk 4L"}

Rules:
- category MUST be one of the provided categories exactly as written
- search_tip should be a concise, specific search term (2-5 words) that helps find relevant products
- Do not include explanations, markdown, or anything other than the JSON object`;
}
