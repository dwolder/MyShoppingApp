package com.myshoppinglist.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class BrandPreference(val displayName: String) {
    ALL("All Products"),
    GENERIC_PREFERRED("Prefer Generic / Store Brands"),
    BRAND_NAME_ONLY("Name Brands Only");

    companion object {
        fun fromName(name: String): BrandPreference =
            entries.find { it.name == name } ?: ALL
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val brandPrefKey = stringPreferencesKey("brand_preference")

    val brandPreference: Flow<BrandPreference> = context.dataStore.data.map { prefs ->
        BrandPreference.fromName(prefs[brandPrefKey] ?: BrandPreference.ALL.name)
    }

    suspend fun setBrandPreference(pref: BrandPreference) {
        context.dataStore.edit { prefs ->
            prefs[brandPrefKey] = pref.name
        }
    }
}
