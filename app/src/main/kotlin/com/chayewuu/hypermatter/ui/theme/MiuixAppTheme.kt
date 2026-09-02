package com.chayewuu.hypermatter.ui.theme

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import com.chayewuu.hypermatter.data.CategoryStore
import com.chayewuu.hypermatter.data.EventViewModel
import com.chayewuu.hypermatter.data.ReminderStore
import com.chayewuu.hypermatter.data.SettingsStore

/** Provides the [EventViewModel] to all pages. */
val LocalEventViewModel = compositionLocalOf<EventViewModel> {
    error("EventViewModel not provided")
}

/** Provides the [SettingsStore] to all pages. */
val LocalSettingsStore = compositionLocalOf<SettingsStore> {
    error("SettingsStore not provided")
}

/** Provides the [CategoryStore] to all pages. */
val LocalCategoryStore = compositionLocalOf<CategoryStore> {
    error("CategoryStore not provided")
}

/** Provides the [ReminderStore] to all pages. */
val LocalReminderStore = compositionLocalOf<ReminderStore> {
    error("ReminderStore not provided")
}

/** Order must match the palette-style option list shown on the theme page. */
val PALETTE_STYLES = listOf(
    ThemePaletteStyle.TonalSpot,
    ThemePaletteStyle.Neutral,
    ThemePaletteStyle.Vibrant,
    ThemePaletteStyle.Expressive,
    ThemePaletteStyle.Rainbow,
    ThemePaletteStyle.FruitSalad,
    ThemePaletteStyle.Monochrome,
    ThemePaletteStyle.Fidelity,
    ThemePaletteStyle.Content,
)

/**
 * Root Miuix theme.
 * @param colorMode 0 = System, 1 = Light, 2 = Dark
 * @param monetColor true = derive the palette from the system wallpaper
 *   (Material You / HyperOS dynamic color), combined with [colorMode]
 * @param monetPaletteStyle -1 = follow the system wallpaper color style;
 *   0..8 = force a [ThemePaletteStyle] (index into [PALETTE_STYLES]).
 *   A forced style reads the system wallpaper seed color and feeds it to
 *   ThemeController.keyColor, because ThemeController only applies
 *   paletteStyle when a keyColor is present.
 */
@Composable
fun MiuixAppTheme(
    colorMode: Int = 0,
    monetColor: Boolean = false,
    monetPaletteStyle: Int = -1,
    monetSeedColor: Long? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val controller = remember(colorMode, monetColor, monetPaletteStyle, monetSeedColor) {
        val monetMode = when (colorMode) {
            1 -> ColorSchemeMode.MonetLight
            2 -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.MonetSystem
        }
        val plainMode = when (colorMode) {
            1 -> ColorSchemeMode.Light
            2 -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }
        if (!monetColor) {
            ThemeController(colorSchemeMode = plainMode)
        } else {
            val forcedStyle = monetPaletteStyle
                .takeIf { it in PALETTE_STYLES.indices }
                ?.let { PALETTE_STYLES[it] }
            val seedOverride = monetSeedColor?.let { Color(it.toInt()) }
            // Custom seed color wins; otherwise a forced style reads the
            // system wallpaper seed.
            val seed = seedOverride
                ?: forcedStyle?.let { readSystemSeedColor(context) }
            if ((forcedStyle != null || seedOverride != null) && seed != null) {
                // Match the platform's spec choice (Spec2025 on API 36+);
                // ThemeController downgrades internally for unsupported styles.
                val spec = if (Build.VERSION.SDK_INT >= 36)
                    ThemeColorSpec.Spec2025
                else
                    ThemeColorSpec.Spec2021
                // Style: forced choice > system wallpaper style > TonalSpot
                val style = forcedStyle
                    ?: readSystemPaletteStyle(context)
                    ?: ThemePaletteStyle.TonalSpot
                ThemeController(
                    colorSchemeMode = monetMode,
                    keyColor = seed,
                    colorSpec = spec,
                    paletteStyle = style,
                )
            } else {
                // No custom style (or seed unavailable) — let Miuix read the
                // system palette info itself.
                ThemeController(colorSchemeMode = monetMode)
            }
        }
    }
    MiuixTheme(controller = controller) {
        content()
    }
}

/**
 * Reads the system wallpaper seed color the same way Miuix's
 * platformDynamicColors does: the theme_customization_overlay_packages
 * secure setting on API 33+, falling back to system_accent1_500 on
 * API 31+. Returns null when unavailable (controller then falls back to
 * platform behavior).
 */
private fun readSystemSeedColor(context: Context): Color? {
    return try {
        if (Build.VERSION.SDK_INT >= 33) {
            val json = Settings.Secure.getString(
                context.contentResolver,
                "theme_customization_overlay_packages",
            ) ?: return fallbackSeed(context)
            val seedHex = JSONObject(json)
                .optString("android.theme.customization.system_palette", "")
            if (seedHex.isNotBlank()) {
                val argb = android.graphics.Color.parseColor(
                    if (seedHex.startsWith("#")) seedHex else "#$seedHex"
                )
                Color(argb)
            } else {
                fallbackSeed(context)
            }
        } else {
            fallbackSeed(context)
        }
    } catch (_: Exception) {
        null
    }
}

private fun fallbackSeed(context: Context): Color? {
    return if (Build.VERSION.SDK_INT >= 31) {
        try {
            Color(context.resources.getColor(android.R.color.system_accent1_500, context.theme))
        } catch (_: Exception) {
            null
        }
    } else {
        null
    }
}

/**
 * Reads the system wallpaper palette style the same way Miuix's
 * platformDynamicColors does (theme_customization_overlay_packages on
 * API 33+). Returns null when unavailable.
 */
private fun readSystemPaletteStyle(context: Context): ThemePaletteStyle? {
    if (Build.VERSION.SDK_INT < 33) return null
    return try {
        val json = Settings.Secure.getString(
            context.contentResolver,
            "theme_customization_overlay_packages",
        ) ?: return null
        val styleName = JSONObject(json)
            .optString("android.theme.customization.theme_style", "TONAL_SPOT")
        when (styleName.uppercase()) {
            "TONAL_SPOT" -> ThemePaletteStyle.TonalSpot
            "VIBRANT" -> ThemePaletteStyle.Vibrant
            "EXPRESSIVE" -> ThemePaletteStyle.Expressive
            "SPRITZ", "NEUTRAL" -> ThemePaletteStyle.Neutral
            "RAINBOW" -> ThemePaletteStyle.Rainbow
            "FRUIT_SALAD" -> ThemePaletteStyle.FruitSalad
            "MONOCHROMATIC", "MONOCHROME" -> ThemePaletteStyle.Monochrome
            "FIDELITY" -> ThemePaletteStyle.Fidelity
            "CONTENT" -> ThemePaletteStyle.Content
            else -> ThemePaletteStyle.TonalSpot
        }
    } catch (_: Exception) {
        null
    }
}
