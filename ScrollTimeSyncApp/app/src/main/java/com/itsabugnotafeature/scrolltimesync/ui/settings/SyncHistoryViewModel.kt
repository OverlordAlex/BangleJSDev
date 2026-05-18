package com.itsabugnotafeature.scrolltimesync.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itsabugnotafeature.scrolltimesync.data.HealthRepository
import com.itsabugnotafeature.scrolltimesync.data.entity.SyncLogEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SyncHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HealthRepository.getInstance(application)

    val allSyncs: StateFlow<List<SyncLogEntry>> = repository.allSyncs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
