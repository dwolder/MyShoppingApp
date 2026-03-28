package com.myshoppinglist.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.myshoppinglist.data.local.dao.ShoppingItemDao
import com.myshoppinglist.data.local.dao.ShoppingListDao
import com.myshoppinglist.data.local.entity.ShoppingItemEntity
import com.myshoppinglist.data.local.entity.ShoppingListEntity

@Database(
    entities = [ShoppingListEntity::class, ShoppingItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ShoppingDatabase : RoomDatabase() {
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun shoppingItemDao(): ShoppingItemDao
}
