package com.gta.pwk_ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gta.pwk_ai.data.PasswordEntry
import com.gta.pwk_ai.data.PasswordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PasswordViewModel(private val repository: PasswordRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    @OptIn(ExperimentalCoroutinesApi::class)
    val passwords: StateFlow<List<PasswordEntry>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            repository.allPasswords
        } else {
            repository.searchPasswords(query)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun insert(passwordEntry: PasswordEntry) = viewModelScope.launch {
        repository.insert(passwordEntry)
    }

    fun update(passwordEntry: PasswordEntry) = viewModelScope.launch {
        repository.update(passwordEntry)
    }

    fun delete(passwordEntry: PasswordEntry) = viewModelScope.launch {
        repository.delete(passwordEntry)
    }

    fun importPasswords(passwords: List<PasswordEntry>) = viewModelScope.launch {
        repository.insertAll(passwords)
    }
}

class PasswordViewModelFactory(private val repository: PasswordRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PasswordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PasswordViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
