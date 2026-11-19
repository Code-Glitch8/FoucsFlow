package com.focusflow.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FocusFlowApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun startWifiSync() {
        container.wifiSync.start(appScope)
        container.wifiSync.syncNow()
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (container.sync.pairingCode.value != null) container.wifiSync.start(appScope)
        appScope.launch {
            container.subjects.ensureBuiltIns()
            container.subjects.ensureClass11Chapters(container.settings)
            container.widgets.seedDefaults(container.settings, container.subjects)
        }
    }
}
