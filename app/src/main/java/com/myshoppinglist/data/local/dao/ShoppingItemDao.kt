package com.myshoppinglist.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myshoppinglist.data.local.entity.ShoppingItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingItemDao {
    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY isChecked ASC, category ASC, name ASC")
    fun getItemsByListId(listId: String): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun getItemById(id: String): ShoppingItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ShoppingItemEntity>)

    @Update
    suspend fun updateItem(item: ShoppingItemEntity)

    @Delete
    suspend fun deleteItem(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("UPDATE shopping_items SET isChecked = :isChecked, updatedAt = :updatedAt, needsSync = 1 WHERE id = :id")
    suspend fun setItemChecked(id: String, isChecked: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE shopping_items SET isChecked = 0, updatedAt = :updatedAt, needsSync = 1 WHERE listId = :listId")
    suspend fun uncheckAllItems(listId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM shopping_items WHERE listId = :listId AND isChecked = 1")
    suspend fun deleteCheckedItems(listId: String)

    @Query("SELECT * FROM shopping_items WHERE needsSync = 1")
    suspend fun getUnsyncedItems(): List<ShoppingItemEntity>

    @Query("UPDATE shopping_items SET needsSync = 0, supabaseId = :supabaseId WHERE id = :id")
    suspend fun markSynced(id: String, supabaseId: String)
}
