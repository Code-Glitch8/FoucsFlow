package com.focusflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.focusflow.app.service.AppVisibility
import com.focusflow.app.service.FocusSessionService
import com.focusflow.app.ui.FocusFlowRoot
import com.focusflow.app.widgets.WeeklyAnalyticsWidgetProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FocusFlowRoot() }
    }

    override fun onStop() {
        AppVisibility.mainVisible.value = false
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.mainVisible.value = true
        // If the process was killed mid-session, the session is still in the database:
        // bring the foreground service (timer, notification) back.
        WeeklyAnalyticsWidgetProvider.refreshAll(this)
        lifecycleScope.launch {
            if ((application as FocusFlowApp).container.sessions.hasActive()) {
                FocusSessionService.start(this@MainActivity)
            }
        }
    }
}
