package com.focusflow.app.service

import kotlinx.coroutines.flow.MutableStateFlow

/** True while FocusFlow's own screen is visible; the floating bubble hides itself then. */
object AppVisibility {
    val mainVisible = MutableStateFlow(false)
}
