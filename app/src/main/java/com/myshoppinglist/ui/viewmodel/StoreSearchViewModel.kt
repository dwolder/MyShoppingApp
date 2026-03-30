package com.myshoppinglist.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myshoppinglist.data.local.entity.ListType
import com.myshoppinglist.data.local.entity.ShoppingItemEntity
import com.myshoppinglist.data.local.entity.StoreInfo
import com.myshoppinglist.data.remote.ProductSearchService
import com.myshoppinglist.data.repository.ShoppingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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

data class PriceComparison(
    val itemName: String,
    val results: List<StoreProductGroup>
)

data class StoreProductGroup(
    val store: StoreInfo,
    val products: List<StoreProduct>,
    val bestPrice: Double
)

@HiltViewModel
class StoreSearchViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val searchService: ProductSearchService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val listId: String = savedStateHandle.get<String>("listId")
        ?: throw IllegalArgumentException("listId required")

    val listItems: StateFlow<List<ShoppingItemEntity>> = repository.getItemsByListId(listId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _listType = MutableStateFlow(ListType.GROCERY)
    val listType: StateFlow<ListType> = _listType.asStateFlow()

    private val _comparisons = MutableStateFlow<List<PriceComparison>>(emptyList())
    val comparisons: StateFlow<List<PriceComparison>> = _comparisons.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val isConfigured: Boolean
        get() = searchService.isConfigured()

    init {
        viewModelScope.launch {
            val list = repository.getListById(listId)
            if (list != null) {
                _listType.value = ListType.fromName(list.listType)
            }
        }
    }

    fun compareAllStores() {
        val stores = StoreInfo.forListType(_listType.value)
        if (stores.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            _comparisons.value = emptyList()
            _hasSearched.value = true
            _errorMessage.value = null

            try {
                val items = listItems.value.filter { !it.isChecked }
                val results = mutableListOf<PriceComparison>()

                for (item in items) {
                    val storeResults = stores.map { store ->
                        async {
                            try {
                                val products = searchService.searchProducts(
                                    query = item.name,
                                    storeApiId = store.apiId
                                )
                                StoreProductGroup(
                                    store = store,
                                    products = products,
                                    bestPrice = products.minOfOrNull {
                                        it.salePrice ?: it.price
                                    } ?: Double.MAX_VALUE
                                )
                            } catch (_: Exception) {
                                StoreProductGroup(store = store, products = emptyList(), bestPrice = Double.MAX_VALUE)
                            }
                        }
                    }.awaitAll().filter { it.products.isNotEmpty() }

                    if (storeResults.isNotEmpty()) {
                        results.add(
                            PriceComparison(
                                itemName = item.name,
                                results = storeResults.sortedBy { it.bestPrice }
                            )
                        )
                    }
                }

                _comparisons.value = results
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Search failed"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
