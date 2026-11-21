package ai.zavi.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle

/**
 * User-controlled accessibility bridge for ZAVI.
 * The service does not observe or act on screens while disconnected from an explicit request.
 */
class ZaviAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally no automatic screen scraping or background actions.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        connected = false
        super.onDestroy()
    }

    fun clickVisibleText(label: String): Boolean {
        if (label.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(label)
        for (node in matches) {
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable && current.isEnabled) {
                    val success = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (success) return true
                }
                current = current.parent
            }
        }
        return false
    }

    fun scrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return findScrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
    }

    fun scrollBackward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return findScrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
    }

    fun setTextOnFocusedField(value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (!focused.isEditable || !focused.isEnabled) return false
        return focused.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        )
    }

    fun tapScreen(x: Float, y: Float): Boolean {
        if (x < 0 || y < 0) return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable && node.isEnabled) return node
        for (index in 0 until node.childCount) {
            val found = node.getChild(index)?.let(::findScrollable)
            if (found != null) return found
        }
        return null
    }

    companion object {
        @Volatile var connected: Boolean = false
            private set
        @Volatile private var instance: ZaviAccessibilityService? = null

        fun clickText(label: String): Boolean = instance?.clickVisibleText(label) == true
        fun scrollDown(): Boolean = instance?.scrollForward() == true
        fun scrollUp(): Boolean = instance?.scrollBackward() == true
        fun typeInFocusedField(value: String): Boolean = instance?.setTextOnFocusedField(value) == true
        fun tap(x: Float, y: Float): Boolean = instance?.tapScreen(x, y) == true
    }
}
