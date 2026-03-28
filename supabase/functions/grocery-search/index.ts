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

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const { query, store, postal_code }: SearchRequest = await req.json();

    if (!query || !store) {
      return new Response(JSON.stringify([]), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    let products: Product[] = [];

    switch (store) {
      case "superstore":
        products = await searchSuperstore(query, postal_code);
        break;
      case "metro":
        products = await searchMetro(query, postal_code);
        break;
      case "freshco":
      case "sobeys":
        products = await searchFlipp(query, store);
        break;
      default:
        products = [];
    }

    return new Response(JSON.stringify(products), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (error) {
    console.error("Grocery search error:", error);
    return new Response(JSON.stringify([]), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 200, // Return empty results, not errors
    });
  }
});

async function searchSuperstore(
  query: string,
  postalCode?: string
): Promise<Product[]> {
  const storeId = Deno.env.get("PC_STORE_ID") || "1001";
  const apiKey =
    Deno.env.get("PC_API_KEY") || "1im1hL52q9xvta16GlSdYDsTsG0dmyhF";

  try {
    const response = await fetch(
      "https://api.pcexpress.ca/product-facade/v3/products/search",
      {
        method: "POST",
        headers: {
          Accept: "application/json, text/plain, */*",
          "Content-Type": "application/json",
          "Site-Banner": "superstore",
          "X-Apikey": apiKey,
          Origin: "https://www.realcanadiansuperstore.ca",
        },
        body: JSON.stringify({
          pagination: { from: 0, size: 10 },
          banner: "superstore",
          cartId: crypto.randomUUID(),
          lang: "en",
          date: formatDate(new Date()),
          storeId: storeId,
          pcId: false,
          pickupType: "STORE",
          offerType: "ALL",
          term: query,
          userData: {
            domainUserId: crypto.randomUUID(),
            sessionId: crypto.randomUUID(),
          },
        }),
      }
    );

    if (!response.ok) return [];

    const data = await response.json();
    const results = data?.results || [];

    return results.slice(0, 5).map(
      (item: Record<string, unknown>): Product => ({
        id: String(item.productId || crypto.randomUUID()),
        name: String(item.name || ""),
        brand: String(item.brand || ""),
        price: extractPrice(item.prices),
        sale_price: extractSalePrice(item.prices),
        is_on_sale: Boolean(
          item.badges &&
            Array.isArray(item.badges) &&
            item.badges.some(
              (b: Record<string, unknown>) =>
                String(b.text || "").toLowerCase().includes("sale") ||
                String(b.type || "").toLowerCase().includes("sale")
            )
        ),
        size: String(item.packageSize || ""),
        image_url: item.imageUrl ? String(item.imageUrl) : null,
        store_name: "Real Canadian Superstore",
      })
    );
  } catch (error) {
    console.error("Superstore search error:", error);
    return [];
  }
}

function extractPrice(
  prices: Record<string, unknown> | undefined | null
): number {
  if (!prices) return 0;
  const price = prices.price ?? prices.wasPrice ?? 0;
  if (typeof price === "object" && price !== null) {
    return Number((price as Record<string, unknown>).value || 0);
  }
  return Number(price) || 0;
}

function extractSalePrice(
  prices: Record<string, unknown> | undefined | null
): number | null {
  if (!prices) return null;
  const salePrice = prices.price;
  const wasPrice = prices.wasPrice;
  if (wasPrice && salePrice) {
    const sale =
      typeof salePrice === "object"
        ? Number((salePrice as Record<string, unknown>).value || 0)
        : Number(salePrice);
    const was =
      typeof wasPrice === "object"
        ? Number((wasPrice as Record<string, unknown>).value || 0)
        : Number(wasPrice);
    if (sale < was) return sale;
  }
  return null;
}

async function searchMetro(
  query: string,
  _postalCode?: string
): Promise<Product[]> {
  try {
    const response = await fetch(
      `https://api.metro.ca/products-v1/search/products?filter=${encodeURIComponent(query)}&lang=en&limit=10&storeId=0564`,
      {
        headers: {
          Accept: "application/json",
          "User-Agent":
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
          Origin: "https://www.metro.ca",
          Referer: "https://www.metro.ca/",
        },
      }
    );

    if (!response.ok) return [];

    const data = await response.json();
    const products = data?.products || data?.results || [];

    return products.slice(0, 5).map(
      (item: Record<string, unknown>): Product => ({
        id: String(item.productId || item.code || crypto.randomUUID()),
        name: String(item.name || item.title || ""),
        brand: String(item.brand || ""),
        price: Number(item.price || item.regularPrice || 0),
        sale_price: item.specialPrice ? Number(item.specialPrice) : null,
        is_on_sale: Boolean(item.specialPrice || item.isOnPromotion),
        size: String(item.size || item.packageSize || ""),
        image_url: item.imageUrl ? String(item.imageUrl) : null,
        store_name: "Metro",
      })
    );
  } catch (error) {
    console.error("Metro search error:", error);
    return [];
  }
}

async function searchFlipp(
  query: string,
  store: string
): Promise<Product[]> {
  const merchantMap: Record<string, string> = {
    freshco: "FreshCo",
    sobeys: "Sobeys",
  };
  const merchantName = merchantMap[store] || store;

  try {
    const response = await fetch(
      `https://backflipp.wishabi.com/flipp/items/search?locale=en-ca&postal_code=M5V1J2&q=${encodeURIComponent(query)}`,
      {
        headers: {
          Accept: "application/json",
          "User-Agent":
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
        },
      }
    );

    if (!response.ok) return [];

    const data = await response.json();
    const items = data?.items || [];

    const storeItems = items.filter(
      (item: Record<string, unknown>) =>
        String(item.merchant || "")
          .toLowerCase()
          .includes(merchantName.toLowerCase())
    );

    return storeItems.slice(0, 5).map(
      (item: Record<string, unknown>): Product => ({
        id: String(item.id || crypto.randomUUID()),
        name: String(item.name || ""),
        brand: String(item.brand || ""),
        price: Number(item.current_price || item.price || 0),
        sale_price: item.current_price ? Number(item.current_price) : null,
        is_on_sale: true, // Flipp results are from flyers, so they're on sale
        size: String(item.display_name || item.description || ""),
        image_url: item.cutout_image_url
          ? String(item.cutout_image_url)
          : null,
        store_name: merchantName,
      })
    );
  } catch (error) {
    console.error("Flipp search error:", error);
    return [];
  }
}

function formatDate(date: Date): string {
  const dd = String(date.getDate()).padStart(2, "0");
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const yyyy = date.getFullYear();
  return `${dd}${mm}${yyyy}`;
}
