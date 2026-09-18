package com.iris.assistant.agent

import com.iris.assistant.memory.ShortTermMemory

/**
 * The central decision + execution loop of IRIS.
 * Receives natural language, picks a tool via RuleBasedAIModel,
 * handles permissions / confirmations, and returns a spoken reply.
 */
class AgentCore(
    private val toolRegistry: ToolRegistry,
    private val aiModel: IntentModel,
    private val shortTermMemory: ShortTermMemory,
    private val requestPermission: suspend (String) -> Boolean,
    private val confirm: suspend (String) -> Boolean,
    private val onLog: (String) -> Unit = {}
) {

    suspend fun handleRequest(userText: String): String {
        AgentStateHolder.setState(AgentState.THINKING)
        shortTermMemory.add("user", userText)
        onLog("درخواست کاربر: $userText")

        val decision = try {
            aiModel.decide(userText, toolRegistry.all(), shortTermMemory)
        } catch (e: Exception) {
            Decision(null, emptyMap(), "خطا در پردازش درخواست: ${e.message}")
        }

        // No tool needed – just reply
        if (decision.toolName == null) {
            val reply = decision.spokenReply ?: "متوجه نشدم."
            shortTermMemory.add("assistant", reply)
            AgentStateHolder.setState(AgentState.SPEAKING)
            return reply
        }

        val tool = toolRegistry.get(decision.toolName)
        if (tool == null) {
            val reply = "ابزار «${decision.toolName}» پیدا نشد."
            AgentStateHolder.setState(AgentState.SPEAKING)
            return reply
        }

        AgentStateHolder.setLastAction(tool.name)
        AgentStateHolder.setState(AgentState.EXECUTING)
        onLog("اجرای ابزار: ${tool.name}")

        // Permission check
        for (perm in tool.requiredPermissions) {
            // The tool itself also checks, but we pre-emptively ask via bridge
            // if the tool reported NeedsPermission we will handle it below.
        }

        var result = try {
            tool.execute(decision.params)
        } catch (e: Exception) {
            ToolResult.Failure("خطای داخلی: ${e.message}")
        }

        // Handle NeedsPermission
        if (result is ToolResult.NeedsPermission) {
            onLog("درخواست دسترسی: ${result.permission}")
            val granted = requestPermission(result.permission)
            if (!granted) {
                val reply = "دسترسی لازم داده نشد. ${result.reason}"
                AgentStateHolder.setState(AgentState.SPEAKING)
                return reply
            }
            // Retry once after permission granted
            result = try {
                tool.execute(decision.params)
            } catch (e: Exception) {
                ToolResult.Failure("خطای داخلی: ${e.message}")
            }
        }

        // Handle NeedsConfirmation
        if (result is ToolResult.NeedsConfirmation) {
            onLog("درخواست تایید از کاربر")
            val ok = confirm(result.prompt)
            result = if (ok) {
                try {
                    result.onConfirm()
                } catch (e: Exception) {
                    ToolResult.Failure("خطا بعد از تایید: ${e.message}")
                }
            } else {
                ToolResult.Failure("لغو شد.")
            }
        }

        val reply = when (result) {
            is ToolResult.Success -> result.message
            is ToolResult.Failure -> result.message
            is ToolResult.InvalidInput -> result.message
            is ToolResult.NeedsPermission -> result.reason
            is ToolResult.NeedsConfirmation -> "نیاز به تایید داشت."
        }

        shortTermMemory.add("assistant", reply)
        onLog("پاسخ: $reply")
        AgentStateHolder.setState(AgentState.SPEAKING)
        return reply
    }
}
