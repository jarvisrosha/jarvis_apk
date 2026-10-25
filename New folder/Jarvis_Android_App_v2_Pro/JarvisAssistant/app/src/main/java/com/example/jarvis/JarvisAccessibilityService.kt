package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // १. परवानगीशिवाय चालणारे काम: स्क्रोल डाऊन / स्क्रोल अप
    fun scrollScreen(directionDown: Boolean): Boolean {
        val dm = resources.displayMetrics
        val middleX = dm.widthPixels / 2f
        val startY = if (directionDown) dm.heightPixels * 0.78f else dm.heightPixels * 0.22f
        val endY = if (directionDown) dm.heightPixels * 0.22f else dm.heightPixels * 0.78f

        val path = Path().apply {
            moveTo(middleX, startY)
            lineTo(middleX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 380))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    // २. परवानगीशिवाय चालणारे काम: स्क्रीनवरील बटनावर किंवा नावावर क्लिक करणे
    fun clickByText(targetText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(targetText)
        for (node in nodes) {
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                parent = parent.parent
            }
        }
        return false
    }

    // ३. परवानगीने चालणारे संवेदनशील काम: टेक्स्ट बॉक्समध्ये माहिती एडिट किंवा टाईप करणे
    fun editFocusedText(newText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        return focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun performBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
}
