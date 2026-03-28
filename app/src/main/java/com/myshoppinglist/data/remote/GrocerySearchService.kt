package com.myshoppinglist.data.remote

import com.myshoppinglist.ui.viewmodel.GroceryStore
import com.myshoppinglist.ui.viewmodel.StoreProduct
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.Functions
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
data class GrocerySearchRequest(
    val query: String,
    val store: String,
    @SerialName("postal_code")
    val postalCode: String = ""
)

@Serializable
data class GroceryProductResponse(
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

@Singleton
class GrocerySearchService @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchProducts(
        query: String,
        store: GroceryStore,
        postalCode: String = ""
    ): List<StoreProduct> {
        if (store == GroceryStore.NONE) return emptyList()

        return try {
            val requestBody = json.encodeToString(
                GrocerySearchRequest.serializer(),
                GrocerySearchRequest(
                    query = query,
                    store = store.apiId,
                    postalCode = postalCode
                )
            )

            val response = supabaseClient.functions.invoke(
                function = "grocery-search",
                body = requestBody,
                headers = Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                }
            )

            val responseBody: String = response.body()
            val products = json.decodeFromString<List<GroceryProductResponse>>(responseBody)

            products.map { product ->
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
        } catch (e: Exception) {
            emptyList()
        }
    }
}
