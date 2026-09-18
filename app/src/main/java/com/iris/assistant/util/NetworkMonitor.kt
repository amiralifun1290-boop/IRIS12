package com.iris.assistant.util

import kotlinx.coroutines.flow.MutableStateFlow

object NetworkMonitor {
    val isOnline = MutableStateFlow(true)
}
