package com.myshoppinglist.data.repository

import com.myshoppinglist.data.local.dao.ShoppingItemDao
import com.myshoppinglist.data.local.dao.ShoppingListDao
import com.myshoppinglist.data.local.entity.ShoppingItemEntity
import com.myshoppinglist.data.local.entity.ShoppingListEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingRepository @Inject constructor(
    private val listDao: ShoppingListDao,
    private val itemDao: ShoppingItemDao
) {
    fun getAllLists(): Flow<List<ShoppingListEntity>> = listDao.getAllLists()

    suspend fun getListById(id: String): ShoppingListEntity? = listDao.getListById(id)

    suspend fun createList(name: String): ShoppingListEntity {
        val list = ShoppingListEntity(
            id = UUID.randomUUID().toString(),
            name = name
        )
        listDao.insertList(list)
        return list
    }

    suspend fun updateList(list: ShoppingListEntity) {
        listDao.updateList(list.copy(updatedAt = System.currentTimeMillis(), needsSync = true))
    }

    suspend fun deleteList(id: String) {
        listDao.deleteListById(id)
    }

    fun getItemsByListId(listId: String): Flow<List<ShoppingItemEntity>> =
        itemDao.getItemsByListId(listId)

    suspend fun addItem(
        listId: String,
        name: String,
        quantity: Double = 1.0,
        unit: String = "",
        category: String = "Other",
        notes: String = ""
    ): ShoppingItemEntity {
        val item = ShoppingItemEntity(
            id = UUID.randomUUID().toString(),
            listId = listId,
            name = name,
            quantity = quantity,
            unit = unit,
            category = category,
            notes = notes
        )
        itemDao.insertItem(item)
        return item
    }

    suspend fun updateItem(item: ShoppingItemEntity) {
        itemDao.updateItem(item.copy(updatedAt = System.currentTimeMillis(), needsSync = true))
    }

    suspend fun deleteItem(id: String) {
        itemDao.deleteItemById(id)
    }

    suspend fun toggleItemChecked(id: String, isChecked: Boolean) {
        itemDao.setItemChecked(id, isChecked)
    }

    suspend fun uncheckAllItems(listId: String) {
        itemDao.uncheckAllItems(listId)
    }

    suspend fun deleteCheckedItems(listId: String) {
        itemDao.deleteCheckedItems(listId)
    }
}
