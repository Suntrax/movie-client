package com.blissless.movieclient.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ChizukiColors = darkColorScheme(
    primary = ChizukiAccent,
    onPrimary = ChizukiOnBg,
    primaryContainer = ChizukiAccentAlt,
    onPrimaryContainer = ChizukiOnBg,
    secondary = ChizukiAccentAlt,
    onSecondary = ChizukiOnBg,
    background = ChizukiBackground,
    onBackground = ChizukiOnBg,
    surface = ChizukiSurface,
    onSurface = ChizukiOnBg,
    surfaceVariant = ChizukiSurfaceVariant,
    onSurfaceVariant = ChizukiOnBgMuted,
    outline = ChizukiOutline,
    outlineVariant = ChizukiOutlineStrong,
    error = ChizukiError,
)

@Composable
fun ChizukiTheme(
    content: @Composable () -> Unit,
) {
    // Theme is always dark — matches the chizuki reference and saves a light palette.
    MaterialTheme(
        colorScheme = ChizukiColors,
        typography = ChizukiTypography,
        content = content,
    )
}
