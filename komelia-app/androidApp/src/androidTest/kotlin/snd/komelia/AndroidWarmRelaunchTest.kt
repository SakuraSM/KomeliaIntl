package snd.komelia

import android.app.Activity
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidWarmRelaunchTest {
    @Test
    fun finishingAndReopeningTheActivityKeepsTheAuthenticatedSessionUsable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val originalPid = Process.myPid()
        var activity: Activity? = null
        try {
            activity = instrumentation.startActivitySync(intent)
            assertTrue("The initial session did not reach the main navigation", waitUntil(30_000) {
                if (hasMainNavigation()) return@waitUntil true
                clickText("Use local library", "使用本地书库")
                false
            })

            val firstActivity = activity
            instrumentation.runOnMainSync { firstActivity.finish() }
            assertTrue("The first activity did not finish", waitUntil(10_000) { firstActivity.isDestroyed })

            activity = instrumentation.startActivitySync(intent)
            assertEquals(originalPid, Process.myPid())
            assertTrue("A new activity in the same process did not return to the main navigation", waitUntil(15_000) {
                hasMainNavigation()
            })
        } finally {
            activity?.let { current -> instrumentation.runOnMainSync { current.finish() } }
        }
    }

    private fun hasMainNavigation(): Boolean =
        findText("Home", "首页") != null && findText("Settings", "设置") != null

    private fun clickText(vararg labels: String) {
        var node = findText(*labels) ?: return
        while (!node.isClickable) node = node.parent ?: return
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findText(vararg labels: String): AccessibilityNodeInfo? {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return null
        if (root.packageName?.toString() != InstrumentationRegistry.getInstrumentation().targetContext.packageName) return null
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (labels.any { it == node.text?.toString() || it == node.contentDescription?.toString() }) return node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { child -> visit(child)?.let { return it } }
            }
            return null
        }
        return visit(root)
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(200)
        }
        return condition()
    }
}
