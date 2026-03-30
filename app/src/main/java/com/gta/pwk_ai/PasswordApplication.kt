package com.gta.pwk_ai

import android.app.Application
import com.gta.pwk_ai.data.AppDatabase
import com.gta.pwk_ai.data.PasswordRepository

class PasswordApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { PasswordRepository(database.passwordDao()) }
}
