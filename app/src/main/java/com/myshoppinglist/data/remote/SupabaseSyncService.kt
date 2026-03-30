package com.myshoppinglist.data.remote

import com.myshoppinglist.data.local.dao.ShoppingItemDao
import com.myshoppinglist.data.local.dao.ShoppingListDao
import com.myshoppinglist.data.local.entity.ShoppingItemEntity
import com.myshoppinglist.data.local.entity.ShoppingListEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SupabaseListItem(
    val id: String? = null,
    @SerialName("list_id")
    val listId: String,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "",
    val category: String = "Other",
    @SerialName("is_checked")
    val isChecked: Boolean = false,
    val notes: String = "",
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("local_id")
    val localId: String? = null
)

@Serializable
data class SupabaseShoppingList(
    val id: String? = null,
    val name: String,
    @SerialName("family_id")
    val familyId: String? = null,
    @SerialName("created_by")
    val createdBy: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("local_id")
    val localId: String? = null
)

@Singleton
class SupabaseSyncService @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val listDao: ShoppingListDao,
    private val itemDao: ShoppingItemDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val isAuthenticated: Boolean
        get() = supabaseClient.auth.currentSessionOrNull() != null

    suspend fun signUp(email: String, password: String) {
        supabaseClient.auth.signUpWith(io.github.jan.supabase.auth.providers.builtin.Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signIn(email: String, password: String) {
        supabaseClient.auth.signInWith(io.github.jan.supabase.auth.providers.builtin.Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        supabaseClient.auth.signOut()
    }

    fun getCurrentUserId(): String? {
        return supabaseClient.auth.currentSessionOrNull()?.user?.id
    }

    suspend fun syncLists() {
        if (!isAuthenticated) return

        val unsyncedLists = listDao.getUnsyncedLists()
        for (list in unsyncedLists) {
            try {
                val supabaseList = SupabaseShoppingList(
                    name = list.name,
                    familyId = list.familyId,
                    createdBy = getCurrentUserId(),
                    localId = list.id
                )

                val result = supabaseClient.postgrest["shopping_lists"]
                    .upsert(supabaseList) {
                        onConflict = "local_id"
                    }

                listDao.markSynced(list.id, list.supabaseId ?: list.id)
            } catch (_: Exception) {
                // Will retry on next sync
            }
        }
    }

    suspend fun syncItems() {
        if (!isAuthenticated) return

        val unsyncedItems = itemDao.getUnsyncedItems()
        for (item in unsyncedItems) {
            try {
                val supabaseItem = SupabaseListItem(
                    listId = item.listId,
                    name = item.name,
                    quantity = item.quantity,
                    unit = item.unit,
                    category = item.category,
                    isChecked = item.isChecked,
                    notes = item.notes,
                    localId = item.id
                )

                supabaseClient.postgrest["list_items"]
                    .upsert(supabaseItem) {
                        onConflict = "local_id"
                    }

                itemDao.markSynced(item.id, item.supabaseId ?: item.id)
            } catch (_: Exception) {
                // Will retry on next sync
            }
        }
    }

    fun subscribeToListChanges(listId: String) {
        scope.launch {
            if (!isAuthenticated) return@launch

            try {
                val channel = supabaseClient.realtime.channel("list_items_$listId")

                val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "list_items"
                    filter("list_id", FilterOperator.EQ, listId)
                }

                changeFlow.onEach { action ->
                    when (action) {
                        is PostgresAction.Insert -> {
                            val record = action.record
                            val localId = record["local_id"]?.toString()?.removeSurrounding("\"") ?: UUID.randomUUID().toString()
                            val existing = itemDao.getItemById(localId)
                            if (existing == null) {
                                itemDao.insertItem(
                                    ShoppingItemEntity(
                                        id = localId,
                                        listId = listId,
                                        name = record["name"]?.toString()?.removeSurrounding("\"") ?: "",
                                        quantity = record["quantity"]?.toString()?.toDoubleOrNull() ?: 1.0,
                                        unit = record["unit"]?.toString()?.removeSurrounding("\"") ?: "",
                                        category = record["category"]?.toString()?.removeSurrounding("\"") ?: "Other",
                                        isChecked = record["is_checked"]?.toString()?.toBooleanStrictOrNull() ?: false,
                                        notes = record["notes"]?.toString()?.removeSurrounding("\"") ?: "",
                                        needsSync = false
                                    )
                                )
                            }
                        }
                        is PostgresAction.Update -> {
                            val record = action.record
                            val localId = record["local_id"]?.toString()?.removeSurrounding("\"")
                            if (localId != null) {
                                val existing = itemDao.getItemById(localId)
                                if (existing != null) {
                                    itemDao.updateItem(
                                        existing.copy(
                                            name = record["name"]?.toString()?.removeSurrounding("\"") ?: existing.name,
                                            quantity = record["quantity"]?.toString()?.toDoubleOrNull() ?: existing.quantity,
                                            unit = record["unit"]?.toString()?.removeSurrounding("\"") ?: existing.unit,
                                            category = record["category"]?.toString()?.removeSurrounding("\"") ?: existing.category,
                                            isChecked = record["is_checked"]?.toString()?.toBooleanStrictOrNull() ?: existing.isChecked,
                                            notes = record["notes"]?.toString()?.removeSurrounding("\"") ?: existing.notes,
                                            needsSync = false
                                        )
                                    )
                                }
                            }
                        }
                        is PostgresAction.Delete -> {
                            val oldRecord = action.oldRecord
                            val localId = oldRecord["local_id"]?.toString()?.removeSurrounding("\"")
                            if (localId != null) {
                                itemDao.deleteItemById(localId)
                            }
                        }
                        else -> {}
                    }
                }.launchIn(scope)

                channel.subscribe()
            } catch (_: Exception) {
                // Realtime connection failed; local-only mode continues
            }
        }
    }
}
