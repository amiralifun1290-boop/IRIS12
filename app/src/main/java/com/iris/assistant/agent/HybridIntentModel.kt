package com.iris.assistant.agent

import android.content.Context
import com.iris.assistant.config.ApiKeyStore
import com.iris.assistant.memory.ShortTermMemory

/**
 * Hybrid decision engine for IRIS:
 *
 * 1. Offline RuleBased first (fast, free, private) for tools & simple talk.
 * 2. If complex question:
 *    - If user has API key(s) → OpenAiIntentModel (multi-provider)
 *    - Else → FreeWebIntentModel (no API key, public free endpoints)
 * 3. Graceful fallback if online fails.
 */
class HybridIntentModel(
    private val context: Context,
    private val apiModel: OpenAiIntentModel,
    private val freeWebModel: FreeWebIntentModel,
    private val offline: RuleBasedAIModel
) : IntentModel {

    override suspend fun decide(
        userText: String,
        availableTools: Collection<Tool>,
        memory: ShortTermMemory
    ): Decision {
        // 1. Offline first
        val offlineDecision = try {
            offline.decide(userText, availableTools, memory)
        } catch (_: Exception) {
            Decision(null, emptyMap(), null)
        }

        if (offlineDecision.toolName != null) {
            return offlineDecision
        }

        val isComplex = offline.isComplexQuestion(userText.trim().lowercase())

        // 2. Escalate complex (or empty offline) questions online
        if (isComplex || offlineDecision.spokenReply == null) {
            val hasApiKey = ApiKeyStore.getAllUsableProviders(context).isNotEmpty()

            return try {
                val onlineDecision = if (hasApiKey) {
                    apiModel.decide(userText, availableTools, memory)
                } else {
                    freeWebModel.decide(userText, availableTools, memory)
                }

                when {
                    onlineDecision.toolName != null -> onlineDecision
                    !onlineDecision.spokenReply.isNullOrBlank() -> onlineDecision
                    else -> offlineDecision
                }
            } catch (e: Exception) {
                val warning = if (isComplex) {
                    "⚠️ فعلاً مدل‌های آنلاین در دسترس نیستن. "
                } else {
                    "⚠️ "
                }
                val reply = offlineDecision.spokenReply ?: "متوجه نشدم."
                offlineDecision.copy(spokenReply = warning + reply)
            }
        }

        return offlineDecision
    }
}
