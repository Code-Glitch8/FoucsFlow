package com.focusflow.app.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusflow.app.FocusFlowApp
import com.focusflow.app.R
import com.focusflow.app.ui.theme.FocusFlowTheme
import com.focusflow.app.ui.theme.ThemeMode

/** A simple blocking page. Closing it returns Home; it never ends the study session. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shortVideo = intent.getBooleanExtra(EXTRA_SHORTS, false)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val label = if (shortVideo) "YouTube Shorts" else runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)

        val settings = (application as FocusFlowApp).container.settings
        setContent {
            val theme by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.DARK)
            FocusFlowTheme(theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher),
                            contentDescription = "FocusFlow logo",
                            modifier = Modifier.size(88.dp),
                        )
                        Text(
                            "FocusFlow",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
                        )
                        Text(
                            "You should be studying right now!",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "$label is blocked during your focus session.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
                        )
                        Button(onClick = { goHome() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Close App")
                        }
                    }
                }
            }
        }
    }

    @Deprecated("Back closes the blocked app and returns Home")
    override fun onBackPressed() = goHome()

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_SHORTS = "shorts"

        fun show(context: Context, blockedPackage: String, shorts: Boolean = false) {
            val intent = Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, blockedPackage)
                .putExtra(EXTRA_SHORTS, shorts)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                // Android can restrict background activity launches; the HOME action still closes the blocked app.
            }
        }
    }
}
