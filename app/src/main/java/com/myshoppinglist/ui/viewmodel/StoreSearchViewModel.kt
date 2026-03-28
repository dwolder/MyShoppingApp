package com.myshoppinglist.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myshoppinglist.data.local.entity.ShoppingItemEntity
import com.myshoppinglist.data.remote.GrocerySearchService
import com.myshoppinglist.data.repository.ShoppingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GroceryStore(val displayName: String, val apiId: String) {
    NONE("None", ""),
    SUPERSTORE("Superstore", "superstore"),
    METRO("Metro", "metro"),
    FRESHCO("FreshCo", "freshco"),
    SOBEYS("Sobeys", "sobeys");
}

data class StoreProduct(
    val id: String,
    val name: String,
    val brand: String = "",
    val price: Double,
    val salePrice: Double? = null,
    val isOnSale: Boolean = false,
    val size: String = "",
    val imageUrl: String? = null,
    val storeName: String = ""
)

@HiltViewModel
class StoreSearchViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val grocerySearchService: GrocerySearchService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val listId: String = savedStateHandle.get<String>("listId")
        ?: throw IllegalArgumentException("listId required")

    val listItems: StateFlow<List<ShoppingItemEntity>> = repository.getItemsByListId(listId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedStore = MutableStateFlow(GroceryStore.NONE)
    val selectedStore: StateFlow<GroceryStore> = _selectedStore.asStateFlow()

    private val _searchResults = MutableStateFlow<Map<String, List<StoreProduct>>>(emptyMap())
    val searchResults: StateFlow<Map<String, List<StoreProduct>>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun selectStore(store: GroceryStore) {
        _selectedStore.value = store
        if (store != GroceryStore.NONE) {
            searchAllItems(store)
        } else {
            _searchResults.value = emptyMap()
        }
    }

    private fun searchAllItems(store: GroceryStore) {
        viewModelScope.launch {
            _isLoading.value = true
            _searchResults.value = emptyMap()

            val items = listItems.value.filter { !it.isChecked }
            val results = mutableMapOf<String, List<StoreProduct>>()

            for (item in items) {
                try {
                    val products = grocerySearchService.searchProducts(
                        query = item.name,
                        store = store
                    )
                    if (products.isNotEmpty()) {
                        results[item.name] = products
                    }
                } catch (_: Exception) {
                    // Graceful degradation: skip items that fail
                }
            }

            _searchResults.value = results
            _isLoading.value = false
        }
    }
}
