package com.iris.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class IrisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: IrisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun findAndClick(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val clicked = nodes?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        root.recycle()
        nodes?.forEach { it.recycle() }
        return clicked
    }

    fun clickAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun scrollDown(): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollable(root)
        val result = scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
        root.recycle()
        scrollable?.recycle()
        return result
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollable(child)
            if (found != null) return found
        }
        return null
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
