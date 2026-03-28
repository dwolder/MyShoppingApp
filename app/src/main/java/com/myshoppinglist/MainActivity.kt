package com.myshoppinglist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.myshoppinglist.data.remote.SupabaseSyncService
import com.myshoppinglist.ui.navigation.AppNavigation
import com.myshoppinglist.ui.theme.MyShoppingListTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var syncService: SupabaseSyncService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyShoppingListTheme {
                AppNavigation(syncService = syncService)
            }
        }
    }
}
