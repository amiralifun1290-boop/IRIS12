package com.iris.assistant.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val action: String,
    val detail: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true
)
