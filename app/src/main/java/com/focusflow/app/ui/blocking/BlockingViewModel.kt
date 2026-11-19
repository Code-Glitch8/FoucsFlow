package com.focusflow.app.ui.blocking

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.app.data.db.AppCategory
import com.focusflow.app.data.db.BlockedApplicationEntity
import com.focusflow.app.data.repo.BlockRulesRepository
import com.focusflow.app.data.settings.AppSettings
import com.focusflow.app.domain.ShortsMode
import com.focusflow.app.service.InstalledApp
import com.focusflow.app.service.InstalledApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppChoice { NONE, BLOCK, ALLOW }

data class BlockingUiState(
    val loading: Boolean = true,
    val apps: List<InstalledApp> = emptyList(),
    val blocked: Map<String, BlockedApplicationEntity> = emptyMap(),
    val allowed: Set<String> = emptySet(),
    val query: String = "",
    val shortsMode: ShortsMode = ShortsMode.FOCUS_ONLY,
    val homeRedirect: Boolean = false,
    val blockYoutubeApp: Boolean = true,
    val blockAllExceptAllowed: Boolean = false,
) {
    fun choice(pkg: String) = when {
        pkg in blocked -> AppChoice.BLOCK
        pkg in allowed -> AppChoice.ALLOW
        else -> AppChoice.NONE
    }
    val visible: List<InstalledApp>
        get() = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
    fun countIn(category: AppCategory) = apps.count { it.category == category }
}

class BlockingViewModel(
    private val context: Context,
    private val rules: BlockRulesRepository,
    private val settings: AppSettings,
) : ViewModel() {
    private val apps = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")

    init { viewModelScope.launch { apps.value = InstalledApps.load(context) } }

    val state: StateFlow<BlockingUiState> = combine(
        apps, query, rules.blocked, rules.allowed, settings.shortsMode,
    ) { a, q, blocked, allowed, shorts ->
        BlockingUiState(
            loading = a == null,
            apps = a ?: emptyList(),
            blocked = blocked.associateBy { it.packageName },
            allowed = allowed.map { it.packageName }.toSet(),
            query = q,
            shortsMode = shorts,
        )
    }.combine(settings.blockYoutubeApp) { ui, blockYoutube ->
        ui.copy(blockYoutubeApp = blockYoutube)
    }.combine(settings.blockAllExceptAllowed) { ui, whitelistMode ->
        ui.copy(blockAllExceptAllowed = whitelistMode)
    }.combine(settings.homeRedirect) { ui, redirect ->
        ui.copy(homeRedirect = redirect)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockingUiState())

    fun setQuery(q: String) { query.value = q }

    fun choose(app: InstalledApp, choice: AppChoice) {
        viewModelScope.launch {
            when (choice) {
                AppChoice.BLOCK -> rules.block(app.packageName, app.label, app.category)
                AppChoice.ALLOW -> rules.allow(app.packageName, app.label)
                AppChoice.NONE -> rules.clear(app.packageName)
            }
        }
    }

    fun blockAll(category: AppCategory) {
        val targets = state.value.apps.filter { it.category == category }
            .map { Triple(it.packageName, it.label, it.category) }
        viewModelScope.launch { rules.blockMany(targets) }
    }

    fun setBlockedDuringBreak(rule: BlockedApplicationEntity, value: Boolean) {
        viewModelScope.launch { rules.setBlockedDuringBreak(rule, value) }
    }

    fun setShortsMode(m: ShortsMode) { viewModelScope.launch { settings.setShortsMode(m) } }
    fun setHomeRedirect(v: Boolean) { viewModelScope.launch { settings.setHomeRedirect(v) } }
    fun setBlockYoutubeApp(v: Boolean) { viewModelScope.launch { settings.setBlockYoutubeApp(v) } }
    fun setBlockAllExceptAllowed(v: Boolean) { viewModelScope.launch { settings.setBlockAllExceptAllowed(v) } }
}
