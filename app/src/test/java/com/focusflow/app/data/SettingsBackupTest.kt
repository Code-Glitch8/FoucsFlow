package com.focusflow.app.data

import com.focusflow.app.ai.external.ProviderConfig
import com.focusflow.app.ai.external.ProviderType
import com.focusflow.app.data.repo.SettingsBackup
import com.focusflow.app.data.repo.SettingsParse
import com.focusflow.app.data.repo.SettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {
    private val p = ProviderConfig(id = "a", type = ProviderType.OPENROUTER, name = "OR", endpoint = "https://openrouter.ai/api/v1", model = "m")

    @Test fun `round trip keeps values and contains no key material`() {
        val snap = SettingsSnapshot(theme = "DARK", defaultDurationMin = 45, snapGptEnabled = true, blockAllExceptAllowed = true, providers = listOf(p), selectedProvider = "a")
        val json = SettingsBackup.toJson(snap)
        assertFalse(json.contains("apiKey", ignoreCase = true))
        val parsed = (SettingsBackup.parse(json) as SettingsParse.Ok).snapshot
        assertEquals(snap, parsed)
    }

    @Test fun `foreign and newer files are rejected`() {
        assertTrue(SettingsBackup.parse("{}") is SettingsParse.Rejected)
        assertTrue(SettingsBackup.parse("garbage") is SettingsParse.Rejected)
        assertTrue(SettingsBackup.parse("""{"format":"focusflow-settings","version":99}""") is SettingsParse.Rejected)
    }

    @Test fun `merging providers by id never duplicates`() {
        val merged = SettingsBackup.mergeProviders(listOf(p), listOf(p.copy(model = "new"), p.copy(id = "b")))
        assertEquals(2, merged.size)
        assertEquals("new", merged.first { it.id == "a" }.model)
    }
}
