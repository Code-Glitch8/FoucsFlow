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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Latest rules, session and YouTube settings. Whether a break is running is derived from the clock at read time. */
data class BlockSnapshot(
    val session: StudySessionEntity? = null,
    val blocked: Map<String, BlockRule> = emptyMap(),
    val allowed: Set<String> = emptySet(),
    val shortsMode: ShortsMode = ShortsMode.FOCUS_ONLY,
    val homeRedirect: Boolean = false,
) {
    val sessionActive: Boolean get() = session != null
    fun onBreak(now: Long): Boolean = session?.let { SessionClock.isOnBreak(it.clock(), now) } == true

    fun policyInput(now: Long, essentials: Set<String>) = PolicyInput(
        sessionActive = sessionActive,
        onBreak = onBreak(now),
        blocked = blocked,
        allowed = allowed,
        essentials = essentials,
    )

    fun shortsEnabled(now: Long) = BlockPolicy.shortsEnabled(shortsMode, sessionActive, onBreak(now))
    fun homeRedirectEnabled(now: Long) =
        BlockPolicy.homeRedirectEnabled(homeRedirect, shortsMode, sessionActive, onBreak(now))
}

/** Shared by the accessibility service and the usage-stats fallback so they always agree. */
class BlockStateHolder(
    sessions: SessionRepository,
    rules: BlockRulesRepository,
    settings: AppSettings,
    scope: CoroutineScope,
) {
    val snapshot: StateFlow<BlockSnapshot> = combine(
        sessions.observeActiveSession(),
        rules.blocked,
        rules.allowed,
        settings.shortsMode,
        settings.homeRedirect,
    ) { session, blocked, allowed, shorts, redirect ->
        BlockSnapshot(
            session = session,
            blocked = blocked.associate { it.packageName to BlockRule(it.packageName, it.blockedDuringBreak) },
            allowed = allowed.map { it.packageName }.toSet(),
            shortsMode = shorts,
            homeRedirect = redirect,
        )
    }.stateIn(scope, SharingStarted.Eagerly, BlockSnapshot())
}
