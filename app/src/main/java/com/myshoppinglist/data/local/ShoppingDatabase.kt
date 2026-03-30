package com.myshoppinglist.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myshoppinglist.data.local.dao.ShoppingItemDao
import com.myshoppinglist.data.local.dao.ShoppingListDao
import com.myshoppinglist.data.local.entity.ShoppingItemEntity
import com.myshoppinglist.data.local.entity.ShoppingListEntity

@Database(
    entities = [ShoppingListEntity::class, ShoppingItemEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ShoppingDatabase : RoomDatabase() {
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun shoppingItemDao(): ShoppingItemDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shopping_lists ADD COLUMN listType TEXT NOT NULL DEFAULT 'GROCERY'")
            }
        }
    }
}
