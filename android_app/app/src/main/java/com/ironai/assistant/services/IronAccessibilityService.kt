package com.ironai.assistant.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Android Accessibility Service for hands-free automation.
 * Grants SIU the power to touchlessly unlock the screen via swipe gestures,
 * tap buttons, type messages in WhatsApp, take screenshots, pull quick settings,
 * open recent apps, show power dialog, and lock the screen without root access or internet.
 */
class IronAccessibilityService : AccessibilityService() {

    companion object {
        var instance: IronAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i("SiuAccessibility", "SIU Accessibility Service connected and active.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Inspects screen updates if needed for automation
    }

    override fun onInterrupt() {
        Log.w("SiuAccessibility", "SIU Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * Touchless Unlock Gesture:
     * Dispatches an automated bottom-to-top swipe gesture to dismiss the swipe lockscreen
     * without needing the user to physically touch the phone!
     */
    fun performSwipeUpToUnlock(): Boolean {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val startX = width / 2f
        val startY = height * 0.85f // Start near bottom
        val endY = height * 0.20f   // Swipe up to near top

        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val gestureBuilder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(swipePath, 0, 300)
        gestureBuilder.addStroke(stroke)

        return dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d("AlexaAccessibility", "Touchless swipe-up gesture completed successfully.")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w("AlexaAccessibility", "Touchless swipe-up gesture cancelled.")
            }
        }, null)
    }

    // System Navigation Shortcuts
    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openPowerDialog(): Boolean = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    fun lockScreen(): Boolean = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    } else false
    fun takeScreenshot(): Boolean = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    } else false

    /**
     * Finds and clicks a button by its text or content description
     */
    fun clickButtonByText(targetText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(targetText)
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            node.parent?.let { parent ->
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
        }
        return false
    }

    /**
     * Automatically enters text into the active edit text field (e.g. WhatsApp message bar)
     */
    fun enterTextIntoActiveField(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusNode != null) {
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            return focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
        return false
    }
}
