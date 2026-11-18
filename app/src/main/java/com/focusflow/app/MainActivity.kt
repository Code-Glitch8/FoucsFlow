package com.focusflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.focusflow.app.service.AppVisibility
import com.focusflow.app.service.FocusSessionService
import com.focusflow.app.ui.FocusFlowRoot
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
        val container = (application as FocusFlowApp).container
        container.wifiSync.syncNow()
        // Restore the timer/service if the process was killed during a focus session.
        lifecycleScope.launch {
            if (container.sessions.hasActive()) {
                FocusSessionService.start(this@MainActivity)
            }
        }
    }
}
