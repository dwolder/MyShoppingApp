package com.myshoppinglist.data.remote

import com.myshoppinglist.ui.viewmodel.PriceComparison
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

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
        val requestBody = buildJsonObject {
            put("comparisons", buildJsonArray {
                for (comparison in comparisons) {
                    add(buildJsonObject {
                        put("item_name", comparison.itemName)
                        put("stores", buildJsonArray {
                            for (storeGroup in comparison.results) {
                                add(buildJsonObject {
                                    put("store_name", storeGroup.store.displayName)
                                    put("price", storeGroup.bestPrice)
                                })
                            }
                        })
                    })
                }
            })
        }

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
