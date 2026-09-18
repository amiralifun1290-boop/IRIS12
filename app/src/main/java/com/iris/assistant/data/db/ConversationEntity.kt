package com.iris.assistant.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
