import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

interface SearchRequest {
  query: string;
  store?: string;
  postal_code?: string;
  brand_preference?: string;
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

const STORE_BRAND_INDICATORS = [
  "no name",
  "great value",
  "selection",
  "irresistibles",
  "compliments",
  "pc ",
  "president's choice",
  "kirkland",
  "exact",
  "life brand",
  "equate",
  "our finest",
  "sensations by compliments",
];

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const body: SearchRequest = await req.json();
    const { query, postal_code, brand_preference } = body;

    if (!query) {
      return new Response(JSON.stringify({ error: "query is required" }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 400,
      });
    }

    let products = await searchFlipp(query, postal_code);
    products = applyBrandFilter(products, brand_preference);

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
  postalCode?: string
): Promise<Product[]> {
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

  const flyerItems: Product[] = (data.items || [])
    .filter((item: Record<string, unknown>) => {
      const price = Number(item.current_price || 0);
      return price > 0 && String(item.merchant_name || "").length > 0;
    })
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
        store_name: String(item.merchant_name || ""),
      };
    });

  const ecomItems: Product[] = (data.ecom_items || [])
    .filter((item: Record<string, unknown>) => {
      const price = Number(item.current_price || 0);
      return price > 0 && String(item.merchant || "").length > 0;
    })
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
        store_name: String(item.merchant || ""),
      };
    });

  const combined = [...flyerItems, ...ecomItems];

  const bestPerStore = new Map<string, Product>();
  for (const product of combined) {
    const effectivePrice = product.sale_price ?? product.price;
    const existing = bestPerStore.get(product.store_name);
    if (!existing || effectivePrice < (existing.sale_price ?? existing.price)) {
      bestPerStore.set(product.store_name, product);
    }
  }

  const results = Array.from(bestPerStore.values());
  results.sort((a, b) => {
    const priceA = a.sale_price ?? a.price;
    const priceB = b.sale_price ?? b.price;
    return priceA - priceB;
  });

  return results.slice(0, 20);
}

function buildSizeString(item: Record<string, unknown>): string {
  const parts: string[] = [];
  if (item.pre_price_text) parts.push(String(item.pre_price_text));
  if (item.post_price_text) parts.push(String(item.post_price_text));
  if (item.sale_story) parts.push(String(item.sale_story));
  return parts.join(" ").trim();
}

function isStoreBrand(productName: string): boolean {
  const lower = productName.toLowerCase();
  return STORE_BRAND_INDICATORS.some((indicator) => lower.includes(indicator));
}

function applyBrandFilter(products: Product[], brandPreference?: string): Product[] {
  if (!brandPreference || brandPreference === "ALL") {
    return products;
  }

  if (brandPreference === "BRAND_NAME_ONLY") {
    const filtered = products.filter((p) => !isStoreBrand(p.name));
    return filtered.length > 0 ? filtered : products;
  }

  if (brandPreference === "GENERIC_PREFERRED") {
    const store: Product[] = [];
    const name: Product[] = [];
    for (const p of products) {
      if (isStoreBrand(p.name)) {
        store.push(p);
      } else {
        name.push(p);
      }
    }
    return [...store, ...name];
  }

  return products;
}
