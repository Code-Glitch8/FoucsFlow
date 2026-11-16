package com.focusflow.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector

/** The five top-level destinations shown in the bottom navigation bar. */
enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home),
    Tasks("tasks", "Tasks", Icons.Outlined.Checklist),
    Focus("focus", "Focus", Icons.Outlined.Timer),
    Statistics("statistics", "Statistics", Icons.Outlined.BarChart),
    Settings("settings", "Settings", Icons.Outlined.Settings);

    companion object {
        val start: Destination = Home

        fun fromRoute(route: String?): Destination? = entries.firstOrNull { it.route == route }
    }
}
