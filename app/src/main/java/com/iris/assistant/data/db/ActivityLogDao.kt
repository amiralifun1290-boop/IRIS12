package com.iris.assistant.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ActivityLogEntity>>

    @Insert
    suspend fun insert(item: ActivityLogEntity)

    @Query("DELETE FROM activity_logs")
    suspend fun clearAll()
}
