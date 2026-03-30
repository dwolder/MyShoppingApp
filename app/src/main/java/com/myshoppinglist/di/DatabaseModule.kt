package com.myshoppinglist.di

import android.content.Context
import androidx.room.Room
import com.myshoppinglist.data.local.ShoppingDatabase
import com.myshoppinglist.data.local.dao.ShoppingItemDao
import com.myshoppinglist.data.local.dao.ShoppingListDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ShoppingDatabase {
        return Room.databaseBuilder(
            context,
            ShoppingDatabase::class.java,
            "shopping_database"
        )
            .addMigrations(ShoppingDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideShoppingListDao(database: ShoppingDatabase): ShoppingListDao {
        return database.shoppingListDao()
    }

    @Provides
    fun provideShoppingItemDao(database: ShoppingDatabase): ShoppingItemDao {
        return database.shoppingItemDao()
    }
}
