package com.focusflow.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.focusflow.app.AppContainer
import com.focusflow.app.MainActivity
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.repo.ActiveSession
import com.focusflow.app.data.repo.BreakKind
import com.focusflow.app.data.repo.clock
import com.focusflow.app.domain.SessionClock
import com.focusflow.app.ui.formatClock
import com.focusflow.app.ui.theme.FocusFlowTheme
import com.focusflow.app.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Draggable bubble that expands into a compact panel, drawn over other apps with the
 * "display over other apps" permission. The window is never focusable, so it can't steal the
 * keyboard or interfere with typing in the app underneath.
 */
class FloatingAssistant(
    private val service: Service,
    private val container: AppContainer,
) {
    private val context: Context = service
    private val wm = service.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var params: WindowManager.LayoutParams? = null

    private var bubbleX = 0
    private var bubbleY = 0
    private var expanded by mutableStateOf(false)

    val isShowing: Boolean get() = view != null

    suspend fun show() {
        if (view != null) return
        val screen = context.resources.displayMetrics
        val saved = container.settings.bubblePosition.first()
        bubbleX = saved?.first ?: (screen.widthPixels - dp(72))
        bubbleY = saved?.second ?: (screen.heightPixels / 3)
        clampBubble()

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleX
            y = bubbleY
        }
        params = lp

        val o = OverlayOwner()
        val v = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent { Content() }
        }
        try {
            wm.addView(v, lp)
        } catch (e: Exception) {
            // Permission revoked or window refused; stay hidden and retry on the next sync.
            o.destroy()
            return
        }
        o.resume()
        owner = o
        view = v
    }

    fun hide() {
        val v = view ?: return
        runCatching { wm.removeView(v) }
        owner?.destroy()
        view = null
        owner = null
        params = null
        expanded = false
    }

    private fun dp(value: Int) = (value * density).toInt()

    private fun clampBubble() {
        val s = context.resources.displayMetrics
        bubbleX = bubbleX.coerceIn(0, (s.widthPixels - dp(56)).coerceAtLeast(0))
        bubbleY = bubbleY.coerceIn(0, (s.heightPixels - dp(120)).coerceAtLeast(0))
    }

    private fun applyLayout() {
        val v = view ?: return
        val lp = params ?: return
        val s = context.resources.displayMetrics
        if (expanded) {
            val panelW = minOf(s.widthPixels - dp(24), dp(330))
            lp.width = panelW
            lp.x = bubbleX.coerceIn(dp(12), (s.widthPixels - panelW - dp(12)).coerceAtLeast(dp(12)))
            lp.y = bubbleY.coerceAtMost(s.heightPixels / 2)
        } else {
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            lp.x = bubbleX
            lp.y = bubbleY
        }
        runCatching { wm.updateViewLayout(v, lp) }
    }

    private fun dragBy(dx: Float, dy: Float) {
        bubbleX += dx.toInt()
        bubbleY += dy.toInt()
        clampBubble()
        applyLayout()
    }

    private fun savePosition() {
        container.appScope.launch { container.settings.setBubblePosition(bubbleX, bubbleY) }
    }

    private fun setExpanded(value: Boolean) {
        expanded = value
        applyLayout()
    }

    private fun openApp() {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        setExpanded(false)
    }

    // ------------------------------------------------------------------ UI

    @Composable
    private fun Content() {
        val theme by container.settings.themeMode.collectAsState(initial = ThemeMode.DARK)
        val active by container.sessions.observeActive().collectAsState(initial = null)
        val chapters by container.subjects.allChapters.collectAsState(initial = emptyList())
        val now by produceState(System.currentTimeMillis()) {
            while (true) { value = System.currentTimeMillis(); delay(1_000) }
        }
        FocusFlowTheme(theme) {
            val a = active ?: return@FocusFlowTheme
            if (expanded) {
                Panel(a, chapters.filter { it.id in a.chapterIds }.map { it.name }, now)
            } else {
                Bubble(a, now)
            }
        }
    }

    @Composable
    private fun Bubble(a: ActiveSession, now: Long) {
        val clock = a.session.clock()
        val onBreak = SessionClock.isOnBreak(clock, now)
        val text = when {
            onBreak -> formatClock(SessionClock.breakRemainingMs(clock, now))
            a.session.mode == SessionMode.TIMED -> formatClock(SessionClock.remainingMs(clock, now) ?: 0L)
            else -> formatClock(SessionClock.focusElapsedMs(clock, now))
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(if (onBreak) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { savePosition() },
                        onDrag = { change, amount -> change.consume(); dragBy(amount.x, amount.y) },
                    )
                }
                .clickable { setExpanded(true) },
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
        }
    }

    @Composable
    private fun Panel(a: ActiveSession, chapterNames: List<String>, now: Long) {
        val s = a.session
        val clock = s.clock()
        val onBreak = SessionClock.isOnBreak(clock, now)
        val credits = a.credits()
        val cfg = a.breakConfig

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // The header drags the panel too, so it can be moved out of the way.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { savePosition() },
                                onDrag = { change, amount -> change.consume(); dragBy(amount.x, amount.y) },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.subjectName, style = MaterialTheme.typography.titleMedium)
                        if (chapterNames.isNotEmpty()) {
                            Text(
                                chapterNames.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                    TextButton(onClick = { setExpanded(false) }) { Text("Collapse") }
                }

                Text(
                    when {
                        onBreak -> "On break · ${formatClock(SessionClock.breakRemainingMs(clock, now))} left"
                        s.mode == SessionMode.TIMED -> "${formatClock(SessionClock.remainingMs(clock, now) ?: 0L)} left"
                        else -> "Focused ${formatClock(SessionClock.focusElapsedMs(clock, now))}"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )

                Text(
                    "Tasks · ${a.completedCount}/${a.tasks.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                ) {
                    if (a.tasks.isEmpty()) {
                        Text("No tasks planned.", style = MaterialTheme.typography.bodyMedium)
                    }
                    a.tasks.forEach { row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = row.link.completed,
                                onCheckedChange = { checked ->
                                    container.appScope.launch {
                                        container.sessions.setTaskCompleted(row.link.id, row.link.taskId, checked)
                                    }
                                },
                            )
                            Text(row.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                when {
                    !cfg.enabled -> Unit
                    onBreak -> OutlinedButton(
                        onClick = { container.appScope.launch { container.sessions.endBreakEarly(s.id) } },
                    ) { Text("End break") }
                    credits.any -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (credits.shortAvailable > 0) {
                            Button(onClick = { container.appScope.launch { container.sessions.startBreak(s.id, BreakKind.SHORT) } }) {
                                Text("${cfg.shortBreakMinutes}-min break")
                            }
                        }
                        if (credits.longAvailable > 0) {
                            Button(onClick = { container.appScope.launch { container.sessions.startBreak(s.id, BreakKind.LONG) } }) {
                                Text("${cfg.longBreakMinutes}-min break")
                            }
                        }
                    }
                    else -> Text(
                        "Complete ${cfg.tasksForShortBreak} task${if (cfg.tasksForShortBreak == 1) "" else "s"} to earn a break.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedButton(onClick = { openApp() }, modifier = Modifier.fillMaxWidth()) { Text("Open FocusFlow") }
            }
        }
    }

    /** Minimal lifecycle + saved-state owner so Compose can run inside a plain window. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        init {
            controller.performAttach()
            controller.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        fun resume() { registry.currentState = Lifecycle.State.RESUMED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
    }
}
