import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

interface SearchRequest {
  query: string;
  store: string;
  postal_code?: string;
}

interface Product {
  id: string;
  name: string;
  brand: string;
  price: number;
  sale_price: number | null;
  is_on_sale: boolean;
  size: string;
  image_url: string | null;
  store_name: string;
}

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

const MERCHANT_ALIASES: Record<string, string[]> = {
  superstore: ["Real Canadian Superstore"],
  metro: ["Metro"],
  freshco: ["FreshCo"],
  sobeys: ["Sobeys"],
  homedepot: ["Home Depot"],
  rona: ["RONA", "RONA & RONA +"],
};

const DISPLAY_NAMES: Record<string, string> = {
  superstore: "Real Canadian Superstore",
  metro: "Metro",
  freshco: "FreshCo",
  sobeys: "Sobeys",
  homedepot: "Home Depot",
  rona: "RONA",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const { query, store, postal_code }: SearchRequest = await req.json();

    if (!query || !store) {
      return new Response(JSON.stringify({ error: "query and store are required" }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 400,
      });
    }

    const products = await searchFlipp(query, store, postal_code);

    return new Response(JSON.stringify(products), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    console.error("Product search error:", message);
    return new Response(JSON.stringify({ error: message }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 500,
    });
  }
});

async function searchFlipp(
  query: string,
  store: string,
  postalCode?: string
): Promise<Product[]> {
  const aliases = MERCHANT_ALIASES[store];
  if (!aliases) {
    console.error(`Unknown store: ${store}`);
    return [];
  }

  const displayName = DISPLAY_NAMES[store] || store;
  const pc = postalCode || Deno.env.get("FLIPP_POSTAL_CODE") || "M5V1J2";

  const response = await fetch(
    `https://backflipp.wishabi.com/flipp/items/search?locale=en-ca&postal_code=${encodeURIComponent(pc)}&q=${encodeURIComponent(query)}`,
    {
      headers: {
        Accept: "application/json",
        "User-Agent":
          "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
      },
    }
  );

  if (!response.ok) {
    const body = await response.text();
    console.error(`Flipp returned ${response.status}: ${body.substring(0, 200)}`);
    return [];
  }

  const data = await response.json();

  const matchesMerchant = (name: string): boolean =>
    aliases.some((alias) => name.toLowerCase().includes(alias.toLowerCase()));

  const flyerItems: Product[] = (data.items || [])
    .filter((item: Record<string, unknown>) =>
      matchesMerchant(String(item.merchant_name || ""))
    )
    .map((item: Record<string, unknown>): Product => {
      const currentPrice = Number(item.current_price || 0);
      const originalPrice = Number(item.original_price || currentPrice);
      const isOnSale = originalPrice > currentPrice && currentPrice > 0;

      return {
        id: String(item.id || crypto.randomUUID()),
        name: String(item.name || ""),
        brand: "",
        price: isOnSale ? originalPrice : currentPrice,
        sale_price: isOnSale ? currentPrice : null,
        is_on_sale: isOnSale,
        size: buildSizeString(item),
        image_url: item.clean_image_url
          ? String(item.clean_image_url)
          : item.clipping_image_url
            ? String(item.clipping_image_url)
            : null,
        store_name: displayName,
      };
    });

  const ecomItems: Product[] = (data.ecom_items || [])
    .filter((item: Record<string, unknown>) =>
      matchesMerchant(String(item.merchant || ""))
    )
    .map((item: Record<string, unknown>): Product => {
      const currentPrice = Number(item.current_price || 0);
      const originalPrice = Number(item.original_price || currentPrice);
      const isOnSale = originalPrice > currentPrice && currentPrice > 0;

      return {
        id: String(item.global_id || item.sku || crypto.randomUUID()),
        name: String(item.name || ""),
        brand: "",
        price: isOnSale ? originalPrice : currentPrice,
        sale_price: isOnSale ? currentPrice : null,
        is_on_sale: isOnSale,
        size: String(item.description || ""),
        image_url: item.image_url ? String(item.image_url) : null,
        store_name: displayName,
      };
    });

  const combined = [...flyerItems, ...ecomItems];

  combined.sort((a, b) => {
    const priceA = a.sale_price ?? a.price;
    const priceB = b.sale_price ?? b.price;
    return priceA - priceB;
  });

  return combined.slice(0, 10);
}

function buildSizeString(item: Record<string, unknown>): string {
  const parts: string[] = [];
  if (item.pre_price_text) parts.push(String(item.pre_price_text));
  if (item.post_price_text) parts.push(String(item.post_price_text));
  if (item.sale_story) parts.push(String(item.sale_story));
  return parts.join(" ").trim();
}
