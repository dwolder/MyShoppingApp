package com.myshoppinglist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myshoppinglist.data.local.entity.ListType
import com.myshoppinglist.data.local.entity.ShoppingListEntity
import com.myshoppinglist.data.repository.ShoppingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val repository: ShoppingRepository
) : ViewModel() {

    val lists: StateFlow<List<ShoppingListEntity>> = repository.getAllLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    fun showCreateDialog() { _showCreateDialog.value = true }
    fun hideCreateDialog() { _showCreateDialog.value = false }

    fun createList(name: String, listType: ListType = ListType.GROCERY) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createList(name.trim(), listType)
            _showCreateDialog.value = false
        }
    }

    fun deleteList(id: String) {
        viewModelScope.launch {
            repository.deleteList(id)
        }
    }

    fun renameList(list: ShoppingListEntity, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.updateList(list.copy(name = newName.trim()))
        }
    }
}
