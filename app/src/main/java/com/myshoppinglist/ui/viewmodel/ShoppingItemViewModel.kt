package com.myshoppinglist.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myshoppinglist.data.local.entity.GroceryCategory
import com.myshoppinglist.data.local.entity.ListType
import com.myshoppinglist.data.local.entity.ShoppingItemEntity
import com.myshoppinglist.data.remote.LocationService
import com.myshoppinglist.data.remote.ProductSearchService
import com.myshoppinglist.data.repository.BrandPreference
import com.myshoppinglist.data.repository.ShoppingRepository
import com.myshoppinglist.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject

data class CategoryGroup(
    val category: GroceryCategory,
    val items: List<ShoppingItemEntity>
)

@Serializable
private data class SmartAddResponse(
    val category: String = "Other",
    val search_tip: String = ""
)

@HiltViewModel
class ShoppingItemViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val searchService: ProductSearchService,
    private val locationService: LocationService,
    private val preferencesRepository: UserPreferencesRepository,
    private val supabaseClient: SupabaseClient,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    val listId: String = savedStateHandle.get<String>("listId")
        ?: throw IllegalArgumentException("listId required")

    private val _listName = MutableStateFlow("")
    val listName: StateFlow<String> = _listName.asStateFlow()

    private val _listType = MutableStateFlow(ListType.GROCERY)
    val listType: StateFlow<ListType> = _listType.asStateFlow()

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _editingItem = MutableStateFlow<ShoppingItemEntity?>(null)
    val editingItem: StateFlow<ShoppingItemEntity?> = _editingItem.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    val items: StateFlow<List<ShoppingItemEntity>> = repository.getItemsByListId(listId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedItems: StateFlow<List<CategoryGroup>> = repository.getItemsByListId(listId)
        .map { itemList ->
            itemList
                .groupBy { GroceryCategory.fromDisplayName(it.category) }
                .map { (category, items) -> CategoryGroup(category, items) }
                .sortedWith(
                    compareBy<CategoryGroup> { group ->
                        if (group.items.all { it.isChecked }) 1 else 0
                    }.thenBy { it.category.sortOrder }
                )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val checkedCount: StateFlow<Int> = repository.getItemsByListId(listId)
        .map { itemList -> itemList.count { it.isChecked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCount: StateFlow<Int> = repository.getItemsByListId(listId)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            val list = repository.getListById(listId)
            _listName.value = list?.name ?: "Shopping List"
            _listType.value = ListType.fromName(list?.listType ?: ListType.GROCERY.name)
        }
    }

    fun showAddDialog() { _showAddDialog.value = true }
    fun hideAddDialog() { _showAddDialog.value = false }

    fun startEditing(item: ShoppingItemEntity) { _editingItem.value = item }
    fun stopEditing() { _editingItem.value = null }

    fun addItem(name: String, quantity: Double, unit: String, category: String, notes: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val item = repository.addItem(
                listId = listId,
                name = name.trim(),
                quantity = quantity,
                unit = unit,
                category = category,
                notes = notes.trim()
            )
            _showAddDialog.value = false

            if (searchService.isConfigured()) {
                smartAddAndSearch(item)
            } else {
                Log.w("ShoppingItem", "Supabase not configured, skipping smart-add/search")
            }
        }
    }

    private suspend fun smartAddAndSearch(item: ShoppingItemEntity) {
        try {
            Log.d("ShoppingItem", "smartAddAndSearch starting for: ${item.name}")

            val listType = _listType.value
            val brandPref = preferencesRepository.brandPreference.first()
            val categories = GroceryCategory.forListType(listType).map { it.displayName }

            val smartResult = callSmartAdd(item.name, listType, brandPref, categories)
            Log.d("ShoppingItem", "smart-add result: category=${smartResult?.category}, tip=${smartResult?.search_tip}")

            if (smartResult != null && smartResult.category != item.category) {
                repository.updateItem(item.copy(category = smartResult.category))
                Log.d("ShoppingItem", "Updated category to: ${smartResult.category}")
            }

            val searchTerm = smartResult?.search_tip?.ifBlank { item.name } ?: item.name
            val postalCode = locationService.getLocation()?.postalCode ?: ""
            Log.d("ShoppingItem", "Searching: term=$searchTerm postal=$postalCode brand=${brandPref.name}")

            val products = searchService.searchAllStores(
                query = searchTerm,
                postalCode = postalCode,
                brandPreference = brandPref.name
            )
            Log.d("ShoppingItem", "Search returned ${products.size} products")

            if (products.isNotEmpty()) {
                val cheapest = products.minBy { it.salePrice ?: it.price }
                val cheapestPrice = cheapest.salePrice ?: cheapest.price
                val msg = "${item.name}: $${String.format("%.2f", cheapestPrice)} at ${cheapest.storeName}"
                Log.d("ShoppingItem", "Snackbar: $msg")
                _snackbarMessage.tryEmit(msg)
            }
        } catch (e: Exception) {
            Log.e("ShoppingItem", "smartAddAndSearch failed", e)
        }
    }

    private suspend fun callSmartAdd(
        name: String,
        listType: ListType,
        brandPref: BrandPreference,
        categories: List<String>
    ): SmartAddResponse? {
        return try {
            val requestBody = buildJsonObject {
                put("name", name)
                put("list_type", listType.name)
                put("brand_preference", brandPref.name)
                putJsonArray("categories") {
                    for (cat in categories) add(kotlinx.serialization.json.JsonPrimitive(cat))
                }
            }

            Log.d("ShoppingItem", "Calling smart-add with: $requestBody")

            val response = supabaseClient.functions.invoke(
                function = "smart-add",
                body = requestBody,
            )

            val responseBody: String = response.body()
            Log.d("ShoppingItem", "smart-add response: $responseBody")
            json.decodeFromString<SmartAddResponse>(responseBody)
        } catch (e: Exception) {
            Log.e("ShoppingItem", "callSmartAdd failed", e)
            null
        }
    }

    fun updateItem(item: ShoppingItemEntity) {
        viewModelScope.launch {
            repository.updateItem(item)
            _editingItem.value = null
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            repository.deleteItem(id)
        }
    }

    fun toggleItemChecked(item: ShoppingItemEntity) {
        viewModelScope.launch {
            repository.toggleItemChecked(item.id, !item.isChecked)
        }
    }

    fun uncheckAll() {
        viewModelScope.launch {
            repository.uncheckAllItems(listId)
        }
    }

    fun deleteCheckedItems() {
        viewModelScope.launch {
            repository.deleteCheckedItems(listId)
        }
    }
}
