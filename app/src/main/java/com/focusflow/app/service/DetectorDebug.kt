package com.focusflow.app.service

import com.focusflow.app.domain.YouTubeScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class DetectorSnapshot(
    val serviceConnected: Boolean = false,
    val lastScreen: YouTubeScreen? = null,
    val signals: List<String> = emptyList(),
    val sampleIds: List<String> = emptyList(),
    val lastSeenAt: Long = 0,
    val shortsBlocked: Int = 0,
    val homeRedirects: Int = 0,
)

/**
 * In-memory state shared between the accessibility service and the detector test screen.
 * Only ids, tab labels and the classification result are kept; nothing is stored or sent anywhere.
 */
object DetectorDebug {
    private val _state = MutableStateFlow(DetectorSnapshot())
    val state: StateFlow<DetectorSnapshot> = _state

    /** While true the service classifies YouTube and reports, but takes no action. */
    val testMode = MutableStateFlow(false)

    fun connected(value: Boolean) = _state.update { it.copy(serviceConnected = value) }

    fun observed(screen: YouTubeScreen, signals: List<String>, ids: List<String>, now: Long) =
        _state.update { it.copy(lastScreen = screen, signals = signals, sampleIds = ids, lastSeenAt = now) }

    fun shortsBlocked() = _state.update { it.copy(shortsBlocked = it.shortsBlocked + 1) }
    fun homeRedirected() = _state.update { it.copy(homeRedirects = it.homeRedirects + 1) }
}
