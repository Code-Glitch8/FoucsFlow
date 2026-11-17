package com.focusflow.app.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusflow.app.FocusFlowApp
import com.focusflow.app.MainActivity
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.data.repo.clock
import com.focusflow.app.domain.SessionClock
import com.focusflow.app.ui.formatClock
import com.focusflow.app.ui.theme.FocusFlowTheme
import com.focusflow.app.ui.theme.ThemeMode
import kotlinx.coroutines.delay

/** The screen shown when a blocked app is opened during a session. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        val container = (application as FocusFlowApp).container

        setContent {
            val theme by container.settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.DARK)
            val snap by container.blockState.snapshot.collectAsStateWithLifecycle()
            val now by produceState(System.currentTimeMillis()) {
                while (true) { value = System.currentTimeMillis(); delay(1_000) }
            }
            val session = snap.session
            if (session == null) {
                // Session ended while this screen was up: nothing left to block.
                finish()
            }
            FocusFlowTheme(theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("$label is blocked", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Text(
                            "You're in a focus session" + (session?.subjectName?.let { " for $it" } ?: "") +
                                ". Apps you chose to block stay closed until it ends or you earn a break.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (session != null) {
                            val clock = session.clock()
                            val remaining = SessionClock.remainingMs(clock, now)
                            Text(
                                if (session.mode == SessionMode.TIMED && remaining != null) {
                                    "${formatClock(remaining)} left"
                                } else {
                                    "Focused ${formatClock(SessionClock.focusElapsedMs(clock, now))}"
                                },
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        Button(
                            onClick = {
                                startActivity(
                                    Intent(this@BlockActivity, MainActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                                )
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Back to my session") }
                        OutlinedButton(onClick = { goHome() }, modifier = Modifier.fillMaxWidth()) { Text("Go to home screen") }
                    }
                }
            }
        }
    }

    @Deprecated("Back goes to the home screen rather than revealing the blocked app")
    override fun onBackPressed() = goHome()

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"

        fun show(context: Context, blockedPackage: String) {
            val intent = Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, blockedPackage)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // If Android refuses a background start the app has still been sent home; nothing else to do.
            }
        }
    }
}
