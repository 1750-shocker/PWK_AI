package com.gta.pwk_ai.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(passwordEntry: PasswordEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(passwords: List<PasswordEntry>)

    @Update
    suspend fun update(passwordEntry: PasswordEntry)

    @Delete
    suspend fun delete(passwordEntry: PasswordEntry)

    @Query("SELECT * FROM passwords ORDER BY timestamp DESC")
    fun getAllPasswords(): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM passwords WHERE title LIKE '%' || :searchQuery || '%' OR account LIKE '%' || :searchQuery || '%' ORDER BY timestamp DESC")
    fun searchPasswords(searchQuery: String): Flow<List<PasswordEntry>>
}
