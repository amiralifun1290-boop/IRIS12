package com.iris.assistant.agent

/**
 * Base contract every capability in IRIS must implement.
 */
interface Tool {
    val name: String
    val description: String
    val parameters: List<ToolParameter>
        get() = emptyList()
    val isSensitive: Boolean
        get() = false
    val requiredPermissions: List<String>
        get() = emptyList()

    suspend fun execute(params: Map<String, String>): ToolResult
}

data class ToolParameter(
    val name: String,
    val description: String
)

sealed class ToolResult {
    data class Success(
        val message: String,
        val data: Map<String, Any?> = emptyMap()
    ) : ToolResult()

    data class Failure(val message: String) : ToolResult()
    data class InvalidInput(val message: String) : ToolResult()
    data class NeedsPermission(val permission: String, val reason: String) : ToolResult()
    data class NeedsConfirmation(
        val prompt: String,
        val onConfirm: suspend () -> ToolResult
    ) : ToolResult()
}

class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun get(name: String): Tool? = tools[name]

    fun all(): Collection<Tool> = tools.values
}
