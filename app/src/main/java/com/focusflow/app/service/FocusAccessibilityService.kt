package com.focusflow.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.focusflow.app.FocusFlowApp
import com.focusflow.app.domain.BlockPolicy
import com.focusflow.app.domain.UiNode
import com.focusflow.app.domain.YouTubeScreen
import com.focusflow.app.domain.YouTubeUiClassifier

/**
 * Detects blocked apps and YouTube Shorts.
 *
 * Privacy: the service receives window-change events from every app, but uses them only to read
 * the *package name* of the foreground app. The content tree is read ONLY when the foreground
 * app is YouTube, and only the view ids, tab labels and selection state are examined. Nothing is
 * logged, stored or transmitted.
 */
class FocusAccessibilityService : AccessibilityService() {
    private var essentials: Set<String> = emptySet()
    private var lastBlockPkg: String? = null
    private var lastBlockAt = 0L
    private var lastYoutubeEvalAt = 0L
    private var lastYoutubeActionAt = 0L

    private val container get() = (application as FocusFlowApp).container

    override fun onServiceConnected() {
        super.onServiceConnected()
        essentials = EssentialApps.resolve(this)
        connected = true
        DetectorDebug.connected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val now = System.currentTimeMillis()

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleForegroundApp(pkg, now)
        }
        if (pkg == YouTubeUiClassifier.PACKAGE &&
            (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        ) {
            handleYouTube(now)
        }
    }

    private fun handleForegroundApp(pkg: String, now: Long) {
        val snap = container.blockState.snapshot.value
        if (!BlockPolicy.shouldBlockApp(pkg, snap.policyInput(now, essentials))) return
        if (pkg == lastBlockPkg && now - lastBlockAt < 1_200) return
        lastBlockPkg = pkg
        lastBlockAt = now
        // Send the user home first so the blocked app stops being in front, then show the reason.
        performGlobalAction(GLOBAL_ACTION_HOME)
        BlockActivity.show(this, pkg)
    }

    private fun handleYouTube(now: Long) {
        val snap = container.blockState.snapshot.value
        val testing = DetectorDebug.testMode.value
        val shorts = snap.shortsEnabled(now)
        val redirect = snap.homeRedirectEnabled(now)
        if (!testing && !shorts && !redirect) return
        if (now - lastYoutubeEvalAt < 350) return
        lastYoutubeEvalAt = now

        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != YouTubeUiClassifier.PACKAGE) return
            val tree = toUiNode(root, Budget(600), 30)
            val result = YouTubeUiClassifier.classify(tree)
            DetectorDebug.observed(result.screen, result.signals, YouTubeUiClassifier.sampleIds(tree), now)
            if (testing) return
            if (now - lastYoutubeActionAt < 1_500) return

            when {
                result.screen == YouTubeScreen.SHORTS_PLAYER && shorts -> {
                    lastYoutubeActionAt = now
                    DetectorDebug.shortsBlocked()
                    leaveShorts(root)
                }
                result.screen == YouTubeScreen.HOME_FEED && redirect -> {
                    if (clickTab(root) { YouTubeUiClassifier.isSubscriptionsTabLabel(it) }) {
                        lastYoutubeActionAt = now
                        DetectorDebug.homeRedirected()
                    }
                }
            }
        } finally {
            recycleCompat(root)
        }
    }

    /** Prefer switching to the Home tab; fall back to Back (e.g. a Shorts link opened directly). */
    private fun leaveShorts(root: AccessibilityNodeInfo) {
        Toast.makeText(this, "YouTube Shorts is blocked by FocusFlow", Toast.LENGTH_SHORT).show()
        if (!clickTab(root) { YouTubeUiClassifier.isHomeTabLabel(it) }) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun clickTab(root: AccessibilityNodeInfo, matches: (String?) -> Boolean): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 600) {
            val n = queue.removeFirst()
            visited++
            val label = (n.contentDescription ?: n.text)?.toString()
            if (matches(label)) {
                var target: AccessibilityNodeInfo? = n
                while (target != null && !target.isClickable) target = target.parent
                if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
        }
        return false
    }

    private class Budget(var left: Int)

    private fun toUiNode(n: AccessibilityNodeInfo, budget: Budget, depth: Int): UiNode {
        budget.left--
        val children = if (depth > 0 && budget.left > 0) {
            buildList {
                for (i in 0 until n.childCount) {
                    if (budget.left <= 0) break
                    val c = n.getChild(i) ?: continue
                    add(toUiNode(c, budget, depth - 1))
                    recycleCompat(c)
                }
            }
        } else emptyList()
        return UiNode(
            viewId = n.viewIdResourceName,
            text = n.text?.toString(),
            contentDescription = n.contentDescription?.toString(),
            className = n.className?.toString(),
            selected = n.isSelected || n.isChecked,
            children = children,
        )
    }

    @Suppress("DEPRECATION")
    private fun recycleCompat(n: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < 33) n.recycle()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        DetectorDebug.connected(false)
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile var connected: Boolean = false
            private set
    }
}
