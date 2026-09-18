package com.iris.assistant.agent

import com.iris.assistant.memory.ShortTermMemory

data class Decision(
    val toolName: String?,
    val params: Map<String, String>,
    val spokenReply: String? = null
)

/**
 * Anything that can look at what the user said (plus recent conversation
 * and the list of real Tools IRIS has) and decide what to do about it.
 */
interface IntentModel {
    suspend fun decide(userText: String, availableTools: Collection<Tool>, memory: ShortTermMemory): Decision
}
