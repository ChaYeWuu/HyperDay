package com.chayewuu.hypermatter.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import com.chayewuu.hypermatter.data.EventViewModel
import com.chayewuu.hypermatter.data.SettingsStore

/** Provides the [EventViewModel] to all pages. */
val LocalEventViewModel = compositionLocalOf<EventViewModel> {
    error("EventViewModel not provided")
}

/** Provides the [SettingsStore] to all pages. */
val LocalSettingsStore = compositionLocalOf<SettingsStore> {
    error("SettingsStore not provided")
}

/**
 * Root Miuix theme.
 * @param colorMode 0 = System, 1 = Light, 2 = Dark
 */
@Composable
fun MiuixAppTheme(
    colorMode: Int = 0,
    content: @Composable () -> Unit,
) {
    val controller = remember(colorMode) {
        when (colorMode) {
            1 -> ThemeController(ColorSchemeMode.Light)
            2 -> ThemeController(ColorSchemeMode.Dark)
            else -> ThemeController(ColorSchemeMode.System)
        }
    }
    MiuixTheme(controller = controller) {
        content()
    }
}
