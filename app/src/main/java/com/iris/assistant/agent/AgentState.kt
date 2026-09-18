package com.iris.assistant.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AgentState {
    IDLE,
    LISTENING,
    THINKING,
    EXECUTING,
    SPEAKING
}

object AgentStateHolder {
    private val _state = MutableStateFlow(AgentState.IDLE)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private val _lastAction = MutableStateFlow("")
    val lastAction: StateFlow<String> = _lastAction.asStateFlow()

    fun setState(newState: AgentState) {
        _state.value = newState
    }

    fun setLastAction(action: String) {
        _lastAction.value = action
    }

    fun reset() {
        _state.value = AgentState.IDLE
        _lastAction.value = ""
    }
}
