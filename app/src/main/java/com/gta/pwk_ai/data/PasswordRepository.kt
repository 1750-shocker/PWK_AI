package com.gta.pwk_ai.data

import kotlinx.coroutines.flow.Flow

class PasswordRepository(private val passwordDao: PasswordDao) {

    val allPasswords: Flow<List<PasswordEntry>> = passwordDao.getAllPasswords()

    fun searchPasswords(query: String): Flow<List<PasswordEntry>> {
        return passwordDao.searchPasswords(query)
    }

    suspend fun insert(passwordEntry: PasswordEntry) {
        passwordDao.insert(passwordEntry)
    }

    suspend fun insertAll(passwords: List<PasswordEntry>) {
        passwordDao.insertAll(passwords)
    }

    suspend fun update(passwordEntry: PasswordEntry) {
        passwordDao.update(passwordEntry)
    }

    suspend fun delete(passwordEntry: PasswordEntry) {
        passwordDao.delete(passwordEntry)
    }
}
