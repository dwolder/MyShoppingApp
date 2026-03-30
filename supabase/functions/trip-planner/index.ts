import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

interface TripStorePrice {
  store_name: string;
  price: number;
}

interface TripItemComparison {
  item_name: string;
  stores: TripStorePrice[];
}

interface TripPlanRequest {
  comparisons: TripItemComparison[];
}

interface TripStoreItem {
  name: string;
  price: number;
}

interface TripStoreGroup {
  name: string;
  items: TripStoreItem[];
  subtotal: number;
}

interface TripPlanResponse {
  stores: TripStoreGroup[];
  total: number;
  summary: string;
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
    const apiKey = Deno.env.get("GEMINI_API_KEY");

    const { comparisons }: TripPlanRequest = await req.json();

    if (!comparisons || comparisons.length === 0) {
      return new Response(
        JSON.stringify({ error: "No comparison data provided" }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
          status: 400,
        }
      );
    }

    const plan = buildOptimalPlan(comparisons);

    const now = Date.now();
    const shouldCallGemini = apiKey && now >= rateLimitedUntil;

    if (shouldCallGemini) {
      const summary = await fetchGeminiSummary(apiKey!, comparisons, plan);
      if (summary) {
        plan.summary = summary;
      }
    }

    return new Response(JSON.stringify(plan), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    console.error("Trip planner error:", message);
    return new Response(JSON.stringify({ error: message }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 500,
    });
  }
});

async function fetchGeminiSummary(
  apiKey: string,
  comparisons: TripItemComparison[],
  plan: TripPlanResponse
): Promise<string | null> {
  const prompt = buildGeminiPrompt(comparisons, plan);

  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1/models/gemini-3-flash-preview:generateContent?key=${apiKey}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig: {
          temperature: 0.3,
          maxOutputTokens: 200,
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
  return text.trim() || null;
}

function buildOptimalPlan(
  comparisons: TripItemComparison[]
): TripPlanResponse {
  const storeMap = new Map<string, TripStoreItem[]>();

  const TOLERANCE = 0.5;

  const storeCounts = new Map<string, number>();

  for (const item of comparisons) {
    if (item.stores.length === 0) continue;
    const sorted = [...item.stores].sort((a, b) => a.price - b.price);
    const cheapest = sorted[0];

    let chosenStore = cheapest.store_name;

    for (const candidate of sorted.slice(1)) {
      if (candidate.price - cheapest.price <= TOLERANCE) {
        const candidateCount = storeCounts.get(candidate.store_name) || 0;
        const cheapestCount = storeCounts.get(cheapest.store_name) || 0;
        if (candidateCount > cheapestCount) {
          chosenStore = candidate.store_name;
          break;
        }
      } else {
        break;
      }
    }

    const chosenPrice =
      item.stores.find((s) => s.store_name === chosenStore)?.price ??
      cheapest.price;

    if (!storeMap.has(chosenStore)) {
      storeMap.set(chosenStore, []);
    }
    storeMap.get(chosenStore)!.push({
      name: item.item_name,
      price: Math.round(chosenPrice * 100) / 100,
    });

    storeCounts.set(chosenStore, (storeCounts.get(chosenStore) || 0) + 1);
  }

  const stores: TripStoreGroup[] = [];
  let total = 0;

  for (const [storeName, items] of storeMap) {
    const subtotal =
      Math.round(items.reduce((sum, i) => sum + i.price, 0) * 100) / 100;
    total += subtotal;
    stores.push({ name: storeName, items, subtotal });
  }

  stores.sort((a, b) => b.items.length - a.items.length);
  total = Math.round(total * 100) / 100;

  const storeList = stores
    .map((s) => `${s.name} (${s.items.length} item${s.items.length !== 1 ? "s" : ""}, $${s.subtotal.toFixed(2)})`)
    .join(", ");
  const summary = `Shop at ${stores.length} store${stores.length !== 1 ? "s" : ""}: ${storeList}. Estimated total: $${total.toFixed(2)}.`;

  return { stores, total, summary };
}

function buildGeminiPrompt(
  comparisons: TripItemComparison[],
  plan: TripPlanResponse
): string {
  const planSummary = plan.stores
    .map(
      (s) =>
        `${s.name}: ${s.items.map((i) => `${i.name} ($${i.price.toFixed(2)})`).join(", ")} = $${s.subtotal.toFixed(2)}`
    )
    .join("\n");

  return `You are a helpful Canadian shopping assistant. A shopper has compared prices across stores for their grocery/shopping list. Here is the optimal plan:

${planSummary}

Grand total: $${plan.total.toFixed(2)}

Write a brief, friendly 2-3 sentence summary of this shopping trip plan. Mention each store and what to buy there, the total cost, and any savings tips (like "since FreshCo has the most items, start there"). Be concise and conversational. Do NOT use markdown or bullet points -- just plain sentences. Do NOT include JSON.`;
}
