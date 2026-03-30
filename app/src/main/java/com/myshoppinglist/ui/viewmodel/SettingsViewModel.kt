package com.myshoppinglist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myshoppinglist.data.repository.BrandPreference
import com.myshoppinglist.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val brandPreference: StateFlow<BrandPreference> = preferencesRepository.brandPreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BrandPreference.ALL)

    fun setBrandPreference(pref: BrandPreference) {
        viewModelScope.launch {
            preferencesRepository.setBrandPreference(pref)
        }
    }
}
