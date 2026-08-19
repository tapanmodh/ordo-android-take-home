package com.tm.ordotakehome.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class OrdoAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != SETTINGS_PACKAGE) {
            return
        }

        val root = rootInActiveWindow ?: return

        logNodeTree(root)

        if (!AccessibilityDemoCommand.shouldNavigateToBluetooth(this)) {
            return
        }

        navigateTowardBluetooth(root)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    private fun navigateTowardBluetooth(root: AccessibilityNodeInfo) {
        // Search deepest destination first because accessibility
        // events can briefly expose nodes from the previous screen.
        findNodeByLabel(root, LABEL_USE_BLUETOOTH)?.let { node ->
            turnBluetoothOn(node)
            return
        }

        findNodeByLabel(root, LABEL_BLUETOOTH)?.let { node ->
            clickNodeOrParent(node)
            return
        }

        findNodeByLabel(root, LABEL_CONNECTION_PREFERENCES)?.let { node ->
            clickNodeOrParent(node)
            return
        }

        findNodeByLabel(root, LABEL_CONNECTED_DEVICES)?.let { node ->
            clickNodeOrParent(node)
        }
    }

    private fun turnBluetoothOn(useBluetoothNode: AccessibilityNodeInfo) {
        val switchBar = findAncestorByViewId(node = useBluetoothNode, viewId = SWITCH_BAR_VIEW_ID)

        if (switchBar == null) {
            Log.w(TAG, "Bluetooth switch bar not found")
            return
        }

        val switchNode = findNodeByViewId(node = switchBar, viewId = SWITCH_WIDGET_VIEW_ID)

        if (switchNode == null) {
            Log.w(TAG, "Bluetooth switch node not found")
            return
        }

        if (switchNode.isChecked) {
            Log.d(TAG, "Bluetooth is already enabled")
            AccessibilityDemoCommand.clearBluetoothNavigation(this)
            return
        }

        // Clear before clicking. Turning Bluetooth on triggers more
        // accessibility events, and we must not toggle it again.
        AccessibilityDemoCommand.clearBluetoothNavigation(this)

        val clicked = switchBar.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        if (clicked) {
            Log.d(TAG, "Bluetooth switch clicked")
        } else {
            Log.w(TAG, "Unable to click Bluetooth switch")

            // Restore the pending command so another event
            // can retry the operation.
            AccessibilityDemoCommand.requestBluetoothNavigation(this)
        }
    }

    private fun findNodeByLabel(
        root: AccessibilityNodeInfo,
        label: String,
    ): AccessibilityNodeInfo? {

        val queue = ArrayDeque<AccessibilityNodeInfo>()

        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            val textMatches = node.text?.toString()?.equals(label, ignoreCase = true) == true

            val descriptionMatches =
                node.contentDescription?.toString()?.equals(label, ignoreCase = true) == true

            if (textMatches || descriptionMatches) {
                return node
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }

        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node

        while (current != null) {
            if (current.isClickable && current.isEnabled) {
                return current.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK,
                )
            }

            current = current.parent
        }

        Log.w(TAG, "No clickable parent found for ${node.text}")

        return false
    }

    private fun findAncestorByViewId(
        node: AccessibilityNodeInfo,
        viewId: String
    ): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node

        while (current != null) {
            if (current.viewIdResourceName == viewId) {
                return current
            }

            current = current.parent
        }

        return null
    }

    private fun findNodeByViewId(
        node: AccessibilityNodeInfo,
        viewId: String
    ): AccessibilityNodeInfo? {
        if (node.viewIdResourceName == viewId) {
            return node
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue

            val result = findNodeByViewId(node = child, viewId = viewId)

            if (result != null) {
                return result
            }
        }

        return null
    }

    private fun logNodeTree(node: AccessibilityNodeInfo, depth: Int = 0) {

        Log.d(
            TAG,
            buildString {
                append("  ".repeat(depth))
                append(node.className)
                append(" text=")
                append(node.text)
                append(" description=")
                append(node.contentDescription)
                append(" clickable=")
                append(node.isClickable)
                append(" enabled=")
                append(node.isEnabled)
                append(" checked=")
                append(node.isChecked)
                append(" id=")
                append(node.viewIdResourceName)
            },
        )

        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                logNodeTree(node = child, depth = depth + 1)
            }
        }
    }

    companion object {

        private const val TAG = "OrdoAccessibility"

        private const val SETTINGS_PACKAGE = "com.android.settings"

        private const val SWITCH_BAR_VIEW_ID = "com.android.settings:id/switch_bar"

        private const val SWITCH_WIDGET_VIEW_ID = "android:id/switch_widget"

        private const val LABEL_USE_BLUETOOTH = "Use Bluetooth"

        private const val LABEL_BLUETOOTH = "Bluetooth"

        private const val LABEL_CONNECTION_PREFERENCES = "Connection preferences"

        private const val LABEL_CONNECTED_DEVICES = "Connected devices"
    }
}