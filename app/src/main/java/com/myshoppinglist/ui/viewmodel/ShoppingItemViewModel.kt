package com.myshoppinglist.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myshoppinglist.data.local.entity.GroceryCategory
import com.myshoppinglist.data.local.entity.ListType
import com.myshoppinglist.data.local.entity.ShoppingItemEntity
import com.myshoppinglist.data.repository.ShoppingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryGroup(
    val category: GroceryCategory,
    val items: List<ShoppingItemEntity>
)

@HiltViewModel
class ShoppingItemViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

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
            repository.addItem(
                listId = listId,
                name = name.trim(),
                quantity = quantity,
                unit = unit,
                category = category,
                notes = notes.trim()
            )
            _showAddDialog.value = false
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
