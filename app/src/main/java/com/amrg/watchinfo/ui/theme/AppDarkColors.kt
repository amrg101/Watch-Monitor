package com.amrg.watchinfo.ui.theme

import androidx.compose.ui.graphics.Color

object AppDarkColors {
    val textPrimary = Color(0xFFE8E8E8) // Light gray for primary text
    val textSecondary = Color(0xFFAAAAAA) // Medium gray for secondary text
    val textTertiary = Color(0xFF888888) // Darker gray
    val iconTint = Color(0xFFCCCCCC)

    val primaryAction = Color(0xFF74D9FF) // Light Blue
    val primaryActionContent = Color(0xFF001F2A) // Dark Blue for text on light blue

    val secondaryAction = Color(0xFF84DCC6) // Tealish
    val secondaryActionContent = Color(0xFF002018) // Dark Teal for text on teal

    val surfaceAlpha =
        Color(0xFF1E1E1E).copy(alpha = 0.7f) // Standard dark surface with some transparency
    val surfaceSlightlyLighter = Color(0xFF282828)

    val statusGreen = Color(0xFF81C784) // Softer Green
    val statusOrange = Color(0xFFFFB74D) // Softer Orange
    val statusRed = Color(0xFFCF6679)    // Standard Dark Error (from M2 Wear)
}