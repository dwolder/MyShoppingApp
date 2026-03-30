package com.myshoppinglist.data.remote

import android.util.Log
import com.myshoppinglist.ui.viewmodel.StoreProduct
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ProductResponse(
    val id: String = "",
    val name: String = "",
    val brand: String = "",
    val price: Double = 0.0,
    @SerialName("sale_price")
    val salePrice: Double? = null,
    @SerialName("is_on_sale")
    val isOnSale: Boolean = false,
    val size: String = "",
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("store_name")
    val storeName: String = ""
)

@Serializable
data class ErrorResponse(
    val error: String? = null
)

@Singleton
class ProductSearchService @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun isConfigured(): Boolean {
        return com.myshoppinglist.BuildConfig.SUPABASE_URL.isNotBlank() &&
            com.myshoppinglist.BuildConfig.SUPABASE_URL != "https://YOUR_PROJECT_ID.supabase.co"
    }

    suspend fun searchAllStores(
        query: String,
        postalCode: String = "",
        brandPreference: String = "ALL"
    ): List<StoreProduct> {
        if (!isConfigured()) {
            throw IllegalStateException("Supabase is not configured.")
        }

        val bodyJson = buildJsonObject {
            put("query", query)
            put("postal_code", postalCode)
            put("brand_preference", brandPreference)
        }.toString()

        Log.d("ProductSearch", "Searching: query=$query postal=$postalCode brand=$brandPreference")

        val response = supabaseClient.functions("product-search") {
            setBody(TextContent(bodyJson, ContentType.Application.Json))
        }

        val responseBody: String = response.body()
        Log.d("ProductSearch", "Response (first 300): ${responseBody.take(300)}")

        if (responseBody.trimStart().startsWith("{")) {
            val errorObj = json.decodeFromString<ErrorResponse>(responseBody)
            if (errorObj.error != null) {
                Log.e("ProductSearch", "Edge function error: ${errorObj.error}")
                throw IllegalStateException("Search failed: ${errorObj.error}")
            }
            return emptyList()
        }

        val products = json.decodeFromString<List<ProductResponse>>(responseBody)
        Log.d("ProductSearch", "Got ${products.size} products")

        return products.map { product ->
            StoreProduct(
                id = product.id,
                name = product.name,
                brand = product.brand,
                price = product.price,
                salePrice = product.salePrice,
                isOnSale = product.isOnSale,
                size = product.size,
                imageUrl = product.imageUrl,
                storeName = product.storeName
            )
        }
    }
}
