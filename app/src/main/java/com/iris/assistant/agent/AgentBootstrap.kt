package com.iris.assistant.agent

import android.content.Context
import com.iris.assistant.tools.*

/**
 * Builds the fully-populated ToolRegistry. This is the ONLY place that
 * needs to change when a new Tool is added — AgentCore never does.
 */
object AgentBootstrap {
    fun buildToolRegistry(context: Context): ToolRegistry {
        val registry = ToolRegistry()
        registry.register(SmsTool(context))
        registry.register(CallTool(context))
        registry.register(AlarmTool(context))
        registry.register(FlashlightTool(context))
        registry.register(WeatherTool(context))
        registry.register(OpenAppTool(context))
        registry.register(MediaPlayTool(context))
        registry.register(MediaNextTool(context))
        registry.register(MediaPreviousTool(context))
        registry.register(TranslateTool())
        registry.register(LocationTool(context))
        registry.register(AccessibilityClickTool(context))
        registry.register(AccessibilityScrollTool())
        return registry
    }

    fun buildAgentCore(context: Context, onLog: (String) -> Unit = {}): AgentCore {
        val offline = RuleBasedAIModel(context)
        val apiModel = OpenAiIntentModel(context)
        val freeWeb = FreeWebIntentModel(context)

        val model = HybridIntentModel(
            context = context,
            apiModel = apiModel,
            freeWebModel = freeWeb,
            offline = offline
        )
        return AgentCore(
            toolRegistry = buildToolRegistry(context),
            aiModel = model,
            shortTermMemory = com.iris.assistant.memory.ShortTermMemory(),
            requestPermission = { permission -> PermissionBridge.request(permission) },
            confirm = { prompt -> ConfirmationBridge.request(prompt) },
            onLog = onLog
        )
    }
}
