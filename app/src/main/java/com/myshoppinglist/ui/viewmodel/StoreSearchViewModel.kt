package com.myshoppinglist.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.myshoppinglist.data.local.entity.ListType
import com.myshoppinglist.data.local.entity.ShoppingItemEntity
import com.myshoppinglist.data.local.entity.StoreInfo
import com.myshoppinglist.data.remote.LocationInfo
import com.myshoppinglist.data.remote.LocationService
import com.myshoppinglist.data.remote.ProductSearchService
import com.myshoppinglist.data.remote.TripPlannerService
import com.myshoppinglist.data.remote.TripPlanResponse
import com.myshoppinglist.data.repository.ShoppingRepository
import com.myshoppinglist.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val locationService: LocationService,
    private val tripPlannerService: TripPlannerService,
    private val preferencesRepository: UserPreferencesRepository,
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

    private val _discoveredStores = MutableStateFlow<List<String>>(emptyList())
    val discoveredStores: StateFlow<List<String>> = _discoveredStores.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _locationInfo = MutableStateFlow<LocationInfo?>(null)
    val locationInfo: StateFlow<LocationInfo?> = _locationInfo.asStateFlow()

    private val _locationFailed = MutableStateFlow(false)
    val locationFailed: StateFlow<Boolean> = _locationFailed.asStateFlow()

    private val _tripPlan = MutableStateFlow<TripPlanResponse?>(null)
    val tripPlan: StateFlow<TripPlanResponse?> = _tripPlan.asStateFlow()

    private val _isTripLoading = MutableStateFlow(false)
    val isTripLoading: StateFlow<Boolean> = _isTripLoading.asStateFlow()

    private val _tripError = MutableStateFlow<String?>(null)
    val tripError: StateFlow<String?> = _tripError.asStateFlow()

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

    fun fetchLocation() {
        viewModelScope.launch {
            val info = locationService.getLocation()
            _locationInfo.value = info
            _locationFailed.value = info == null
        }
    }

    fun compareAllStores() {
        viewModelScope.launch {
            _isLoading.value = true
            _comparisons.value = emptyList()
            _discoveredStores.value = emptyList()
            _tripPlan.value = null
            _hasSearched.value = true
            _errorMessage.value = null

            try {
                val locationResult = locationService.getLocation()
                if (locationResult != null) {
                    _locationInfo.value = locationResult
                } else {
                    _locationFailed.value = true
                }
                val postalCode = locationResult?.postalCode ?: ""
                val brandPref = preferencesRepository.brandPreference.first().name
                val items = listItems.value.filter { !it.isChecked }
                val results = mutableListOf<PriceComparison>()
                val allStoreNames = mutableSetOf<String>()

                Log.d("StoreSearch", "Searching ${items.size} items, postal=$postalCode, brand=$brandPref")

                for (item in items) {
                    try {
                        val products = searchService.searchAllStores(
                            query = item.name,
                            postalCode = postalCode,
                            brandPreference = brandPref
                        )
                        Log.d("StoreSearch", "${item.name}: ${products.size} products found")

                        if (products.isNotEmpty()) {
                            val grouped = products
                                .groupBy { it.storeName }
                                .map { (storeName, storeProducts) ->
                                    allStoreNames.add(storeName)
                                    StoreProductGroup(
                                        store = StoreInfo(
                                            id = storeName.lowercase().replace(" ", "_"),
                                            displayName = storeName,
                                            apiId = storeName.lowercase().replace(" ", "_"),
                                            listType = _listType.value
                                        ),
                                        products = storeProducts,
                                        bestPrice = storeProducts.minOf {
                                            it.salePrice ?: it.price
                                        }
                                    )
                                }
                                .sortedBy { it.bestPrice }

                            results.add(PriceComparison(itemName = item.name, results = grouped))
                        }
                    } catch (e: Exception) {
                        Log.e("StoreSearch", "Failed searching for ${item.name}", e)
                    }
                }

                _comparisons.value = results
                _discoveredStores.value = allStoreNames.sorted()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Search failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun planTrip() {
        val currentComparisons = _comparisons.value
        if (currentComparisons.isEmpty()) return

        viewModelScope.launch {
            _isTripLoading.value = true
            _tripError.value = null

            try {
                val plan = tripPlannerService.planTrip(currentComparisons)
                _tripPlan.value = plan
            } catch (e: Exception) {
                _tripError.value = e.message ?: "Failed to generate trip plan"
            } finally {
                _isTripLoading.value = false
            }
        }
    }
}
