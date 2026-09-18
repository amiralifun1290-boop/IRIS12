package com.iris.assistant.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Lets AgentCore ask "should I do this?" without knowing about Compose/dialogs at all. */
object ConfirmationBridge {
    private var pending: CompletableDeferred<Boolean>? = null

    private val _prompt = MutableStateFlow<String?>(null)
    val prompt: StateFlow<String?> = _prompt

    suspend fun request(promptText: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        _prompt.value = promptText
        val result = deferred.await()
        _prompt.value = null
        return result
    }

    /** Called by whichever UI is showing the confirmation dialog right now. */
    fun resolve(confirmed: Boolean) {
        _prompt.value = null
        pending?.complete(confirmed)
        pending = null
    }
}

/**
 * Lets AgentCore ask for a runtime permission without holding an Activity
 * reference. HONEST LIMITATION: Android can only show the system
 * permission dialog from a foreground Activity — if a Tool needs a
 * permission that hasn't been granted yet and the app isn't open (e.g.
 * triggered from the background voice overlay), this will time out and
 * the Tool will report failure. The user needs to open IRIS once and
 * grant it there first.
 */
object PermissionBridge {
    private var pending: CompletableDeferred<Boolean>? = null

    private val _permission = MutableStateFlow<String?>(null)
    val permission: StateFlow<String?> = _permission

    suspend fun request(permission: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        _permission.value = permission
        // If no Activity is around to answer within 10s, fail closed rather than hang forever.
        return try {
            kotlinx.coroutines.withTimeout(10_000) { deferred.await() }
        } catch (e: Exception) {
            false
        } finally {
            _permission.value = null
        }
    }

    fun resolve(granted: Boolean) {
        _permission.value = null
        pending?.complete(granted)
        pending = null
    }
}
