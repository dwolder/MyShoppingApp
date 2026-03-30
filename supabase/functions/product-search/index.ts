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
      case "homedepot":
        products = await searchHomeDepot(query);
        break;
      case "rona":
        products = await searchFlipp(query, "rona");
        break;
      default:
        products = [];
    }

    return new Response(JSON.stringify(products), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (error) {
    console.error("Product search error:", error);
    return new Response(JSON.stringify([]), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 200,
    });
  }
});

// ── Grocery: Superstore (PC Express) ──

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

// ── Grocery: Metro ──

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

// ── Flipp (FreshCo, Sobeys, Rona, Home Depot flyers) ──

async function searchFlipp(
  query: string,
  store: string
): Promise<Product[]> {
  const merchantMap: Record<string, string> = {
    freshco: "FreshCo",
    sobeys: "Sobeys",
    rona: "RONA",
    homedepot: "Home Depot",
  };
  const merchantName = merchantMap[store] || store;

  try {
    const postalCode = Deno.env.get("FLIPP_POSTAL_CODE") || "M5V1J2";
    const response = await fetch(
      `https://backflipp.wishabi.com/flipp/items/search?locale=en-ca&postal_code=${postalCode}&q=${encodeURIComponent(query)}`,
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
        is_on_sale: true,
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

// ── Home Improvement: Home Depot CA (GraphQL) ──

async function searchHomeDepot(query: string): Promise<Product[]> {
  const storeId = Deno.env.get("HOMEDEPOT_STORE_ID") || "7140";

  try {
    const graphqlQuery = {
      operationName: "searchModel",
      variables: {
        keyword: query,
        navParam: null,
        storeId: storeId,
        pageSize: 10,
        startIndex: 0,
        orderBy: { field: "TOP_SELLERS", order: "ASC" },
      },
      query: `query searchModel($keyword: String, $navParam: String, $storeId: String, $pageSize: Int, $startIndex: Int, $orderBy: ProductSort) {
        searchModel(keyword: $keyword, navParam: $navParam, storeId: $storeId) {
          products(pageSize: $pageSize, startIndex: $startIndex, orderBy: $orderBy) {
            items {
              itemId
              dataSources
              brandName
              productLabel
              modelNumber
              pricing {
                value
                original
                alternatePriceDisplay
              }
              media {
                images {
                  url
                  sizes
                }
              }
              averageRating
              canonicalUrl
            }
            totalProducts
          }
        }
      }`,
    };

    const response = await fetch(
      "https://apionline.homedepot.ca/federation-gateway/graphql?opname=searchModel",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
          "User-Agent":
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
          Origin: "https://www.homedepot.ca",
          Referer: "https://www.homedepot.ca/",
        },
        body: JSON.stringify(graphqlQuery),
      }
    );

    if (!response.ok) {
      // Fall back to Flipp for flyer-based results
      return searchFlipp(query, "homedepot");
    }

    const data = await response.json();
    const items =
      data?.data?.searchModel?.products?.items || [];

    const results: Product[] = items.slice(0, 5).map(
      (item: Record<string, unknown>): Product => {
        const pricing = item.pricing as Record<string, unknown> | null;
        const media = item.media as Record<string, unknown> | null;
        const images = media?.images as Array<Record<string, unknown>> | null;
        const imageUrl = images?.[0]?.url ? String(images[0].url) : null;

        const currentPrice = Number(pricing?.value || 0);
        const originalPrice = Number(pricing?.original || currentPrice);
        const isOnSale = originalPrice > currentPrice && currentPrice > 0;

        return {
          id: String(item.itemId || crypto.randomUUID()),
          name: String(item.productLabel || ""),
          brand: String(item.brandName || ""),
          price: isOnSale ? originalPrice : currentPrice,
          sale_price: isOnSale ? currentPrice : null,
          is_on_sale: isOnSale,
          size: item.modelNumber ? `Model: ${item.modelNumber}` : "",
          image_url: imageUrl,
          store_name: "Home Depot",
        };
      }
    );

    // If GraphQL returned nothing, try Flipp as fallback
    if (results.length === 0) {
      return searchFlipp(query, "homedepot");
    }

    return results;
  } catch (error) {
    console.error("Home Depot search error:", error);
    return searchFlipp(query, "homedepot");
  }
}

function formatDate(date: Date): string {
  const dd = String(date.getDate()).padStart(2, "0");
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const yyyy = date.getFullYear();
  return `${dd}${mm}${yyyy}`;
}
