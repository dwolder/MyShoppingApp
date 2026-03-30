package com.myshoppinglist.data.remote

import com.myshoppinglist.ui.viewmodel.StoreProduct
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ProductSearchRequest(
    val query: String,
    val store: String,
    @SerialName("postal_code")
    val postalCode: String = ""
)

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

    suspend fun searchProducts(
        query: String,
        storeApiId: String,
        postalCode: String = ""
    ): List<StoreProduct> {
        if (storeApiId.isBlank()) return emptyList()

        if (!isConfigured()) {
            throw IllegalStateException("Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties.")
        }

        val requestBody = json.encodeToString(
            ProductSearchRequest.serializer(),
            ProductSearchRequest(
                query = query,
                store = storeApiId,
                postalCode = postalCode
            )
        )

        val response = supabaseClient.functions.invoke(
            function = "product-search",
            body = requestBody,
            headers = Headers.build {
                append(HttpHeaders.ContentType, "application/json")
            }
        )

        val responseBody: String = response.body()

        if (responseBody.trimStart().startsWith("{")) {
            val errorObj = json.decodeFromString<ErrorResponse>(responseBody)
            if (errorObj.error != null) {
                throw IllegalStateException("Search failed: ${errorObj.error}")
            }
            return emptyList()
        }

        val products = json.decodeFromString<List<ProductResponse>>(responseBody)

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
