package com.iris.assistant.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): PreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(item: PreferenceEntity)

    @Query("SELECT * FROM preferences")
    fun getAll(): Flow<List<PreferenceEntity>>
}
