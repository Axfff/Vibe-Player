package com.iven.musicplayergo.photos

import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry

/** Inspect user-interactable windows, not only an active root cached during a transition.
 * getWindows excludes windows blocked by a modal dialog; this does not bypass consent UI.
 */
internal object PhotoSystemUi {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private fun descendants(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(node) +
        (0 until node.childCount).mapNotNull { node.getChild(it) }.flatMap(::descendants)

    private fun roots(): List<AccessibilityNodeInfo> {
        val info = automation.serviceInfo
        if (info.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS == 0) {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
        }
        val roots = automation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.mapNotNull { it.root }
            .ifEmpty { listOfNotNull(automation.rootInActiveWindow) }
        return roots.filter { it.refresh() }
    }
    fun nodes() = roots().flatMap(::descendants)
    // Only the top application window counts; a visible background app is not a return.
    fun hasPackage(value: String) = roots().firstOrNull()?.let { root ->
        descendants(root).any { it.packageName?.toString()?.contains(value) == true }
    } == true
    fun diagnose() {
        Log.e("PhotoSystemUi", nodes().take(60).joinToString("\n") {
            "package=${it.packageName}, id=${it.viewIdResourceName}, text=${it.text}, description=${it.contentDescription}"
        })
    }
}
