package com.focusflow.app.launch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapGptLauncherTest {
    @Test fun `launches once per session and only when enabled`() {
        assertTrue(SnapGptLauncher.shouldLaunch(true, sessionId = 5, lastLaunchedSession = 4))
        assertFalse(SnapGptLauncher.shouldLaunch(true, sessionId = 5, lastLaunchedSession = 5))
        assertFalse(SnapGptLauncher.shouldLaunch(false, sessionId = 6, lastLaunchedSession = 5))
    }
}
