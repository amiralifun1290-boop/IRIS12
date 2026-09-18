package com.iris.assistant.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY timestamp ASC")
    fun getAll(): Flow<List<ConversationEntity>>

    @Insert
    suspend fun insert(item: ConversationEntity)

    @Query("DELETE FROM conversations")
    suspend fun clearAll()
}
