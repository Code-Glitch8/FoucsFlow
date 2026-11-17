package com.focusflow.app.ui.blocking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusflow.app.domain.YouTubeScreen
import com.focusflow.app.service.DetectorDebug
import com.focusflow.app.ui.common.AppCard
import com.focusflow.app.ui.common.ScreenTitle
import com.focusflow.app.ui.common.SectionLabel

/**
 * Live view of what the detector sees in YouTube. While this screen is open the service
 * classifies and reports but does NOT block anything, so you can browse YouTube freely and check
 * that Shorts, Home and normal videos are told apart correctly.
 */
@Composable
fun DetectorTestScreen(onBack: () -> Unit) {
    val state by DetectorDebug.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        DetectorDebug.testMode.value = true
        onDispose { DetectorDebug.testMode.value = false }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                ScreenTitle("Detector test")
            }
        }
        item {
            Text(
                "Open YouTube (use split-screen or switch apps), move between Home, Shorts, a normal video and " +
                    "search, then come back here. The last thing the detector saw is shown below. Nothing is " +
                    "blocked while this screen is open.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            AppCard {
                SectionLabel("SERVICE")
                Text(if (state.serviceConnected) "Accessibility service is running" else "Accessibility service is NOT running. Enable it in Blocking first.")
            }
        }
        item {
            AppCard {
                SectionLabel("LAST YOUTUBE SCREEN SEEN")
                val seen = state.lastScreen
                Text(
                    when (seen) {
                        null -> "Nothing yet"
                        YouTubeScreen.SHORTS_PLAYER -> "Shorts player (would be blocked)"
                        YouTubeScreen.HOME_FEED -> "Home feed"
                        YouTubeScreen.OTHER -> "Other (video, search, subscriptions… never blocked)"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.signals.isNotEmpty()) {
                    state.signals.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        item {
            AppCard {
                SectionLabel("COUNTERS SINCE APP START")
                Text("Shorts blocked: ${state.shortsBlocked}")
                Text("Home feed redirects: ${state.homeRedirects}")
            }
        }
        item {
            AppCard {
                SectionLabel("VIEW IDS SEEN (for troubleshooting after a YouTube update)")
                Text(
                    if (state.sampleIds.isEmpty()) "None yet" else state.sampleIds.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
