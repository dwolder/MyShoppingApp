package com.myshoppinglist.data.remote

import com.myshoppinglist.ui.viewmodel.PriceComparison
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
data class TripPlanRequest(
    val comparisons: List<TripItemComparison>
)

@Serializable
data class TripItemComparison(
    @SerialName("item_name")
    val itemName: String,
    val stores: List<TripStorePrice>
)

@Serializable
data class TripStorePrice(
    @SerialName("store_name")
    val storeName: String,
    val price: Double
)

@Serializable
data class TripPlanResponse(
    val stores: List<TripStoreGroup> = emptyList(),
    val total: Double = 0.0,
    val summary: String = ""
)

@Serializable
data class TripStoreGroup(
    val name: String = "",
    val items: List<TripStoreItem> = emptyList(),
    val subtotal: Double = 0.0
)

@Serializable
data class TripStoreItem(
    val name: String = "",
    val price: Double = 0.0
)

@Singleton
class TripPlannerService @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun planTrip(comparisons: List<PriceComparison>): TripPlanResponse {
        val request = TripPlanRequest(
            comparisons = comparisons.map { comparison ->
                TripItemComparison(
                    itemName = comparison.itemName,
                    stores = comparison.results.map { storeGroup ->
                        TripStorePrice(
                            storeName = storeGroup.store.displayName,
                            price = storeGroup.bestPrice
                        )
                    }
                )
            }
        )

        val requestBody = json.encodeToString(TripPlanRequest.serializer(), request)

        val response = supabaseClient.functions.invoke(
            function = "trip-planner",
            body = requestBody,
            headers = Headers.build {
                append(HttpHeaders.ContentType, "application/json")
            }
        )

        val responseBody: String = response.body()

        if (responseBody.trimStart().startsWith("[") || responseBody.isBlank()) {
            throw IllegalStateException("Unexpected response from trip planner")
        }

        val parsed = json.decodeFromString<TripPlanResponse>(responseBody)
        if (parsed.stores.isEmpty()) {
            val errorCheck = json.decodeFromString<ErrorResponse>(responseBody)
            if (errorCheck.error != null) {
                throw IllegalStateException(errorCheck.error)
            }
        }

        return parsed
    }
}
