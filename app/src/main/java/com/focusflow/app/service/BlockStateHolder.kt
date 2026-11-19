package com.focusflow.app.service

import com.focusflow.app.data.db.StudySessionEntity
import com.focusflow.app.data.repo.BlockRulesRepository
import com.focusflow.app.data.repo.SessionRepository
import com.focusflow.app.data.repo.clock
import com.focusflow.app.data.settings.AppSettings
import com.focusflow.app.domain.BlockPolicy
import com.focusflow.app.domain.BlockRule
import com.focusflow.app.domain.PolicyInput
import com.focusflow.app.domain.SessionClock
import com.focusflow.app.domain.ShortsMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Latest rules, session and YouTube settings. Whether a break is running is derived from the clock at read time. */
data class BlockSnapshot(
    val session: StudySessionEntity? = null,
    val blocked: Map<String, BlockRule> = emptyMap(),
    val allowed: Set<String> = emptySet(),
    val shortsMode: ShortsMode = ShortsMode.FOCUS_ONLY,
    val homeRedirect: Boolean = false,
    val blockYoutubeApp: Boolean = true,
) {
    val sessionActive: Boolean get() = session != null
    fun onBreak(now: Long): Boolean = session?.let { SessionClock.isOnBreak(it.clock(), now) } == true

    fun policyInput(now: Long, essentials: Set<String>) = PolicyInput(
        sessionActive = sessionActive,
        onBreak = onBreak(now),
        blocked = blocked,
        allowed = allowed,
        essentials = essentials,
        blockYoutubeApp = blockYoutubeApp,
    )

    fun shortsEnabled(now: Long) =
        BlockPolicy.shortsEnabled(shortsMode, sessionActive, onBreak(now)) && session?.blockShorts != false
    fun homeRedirectEnabled(now: Long) =
        BlockPolicy.homeRedirectEnabled(homeRedirect, shortsMode, sessionActive, onBreak(now)) &&
            session?.blockShorts != false
}

/** Shared by the accessibility service and the usage-stats fallback so they always agree. */
class BlockStateHolder(
    sessions: SessionRepository,
    rules: BlockRulesRepository,
    settings: AppSettings,
    scope: CoroutineScope,
) {
    /**
     * The rule lists that apply to the running session: the preset's own lists when the session was
     * started from a preset that has any, otherwise the global lists.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val effectiveRules = sessions.observeActiveSession().flatMapLatest { session ->
        val presetId = session?.presetId
        if (presetId == null) {
            combine(rules.blocked, rules.allowed) { b, a -> b to a }
        } else {
            combine(
                rules.blockedForPreset(presetId), rules.allowedForPreset(presetId),
                rules.blocked, rules.allowed,
            ) { pb, pa, gb, ga -> if (pb.isEmpty() && pa.isEmpty()) gb to ga else pb to pa }
        }
    }

    val snapshot: StateFlow<BlockSnapshot> = combine(
        sessions.observeActiveSession(),
        effectiveRules,
        settings.shortsMode,
        settings.homeRedirect,
        settings.blockYoutubeApp,
    ) { session, (blocked, allowed), shorts, redirect, blockYoutube ->
        BlockSnapshot(
            session = session,
            blocked = blocked.associate { it.packageName to BlockRule(it.packageName, it.blockedDuringBreak) },
            allowed = allowed.map { it.packageName }.toSet(),
            shortsMode = shorts,
            homeRedirect = redirect,
            blockYoutubeApp = blockYoutube,
        )
    }.stateIn(scope, SharingStarted.Eagerly, BlockSnapshot())
}
