package com.gta.pwk_ai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "passwords")
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val account: String,
    val password: String,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
