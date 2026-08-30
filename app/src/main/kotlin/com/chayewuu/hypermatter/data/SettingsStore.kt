package com.chayewuu.hypermatter.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-level settings stored in SharedPreferences.
 * Tracks the dark-mode preference (System / Light / Dark), the app visual
 * style (Classic / Liquid Glass — placeholder for now), and the Monet
 * dynamic color switch (placeholder for now).
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("hypermatter_settings", Context.MODE_PRIVATE)

    /** 0 = System, 1 = Light, 2 = Dark */
    val colorMode = MutableStateFlow(prefs.getInt(KEY_COLOR_MODE, 0))

    fun setColorMode(mode: Int) {
        prefs.edit().putInt(KEY_COLOR_MODE, mode).apply()
        colorMode.value = mode
    }

    /** 0 = Classic, 1 = Liquid Glass (placeholder — visual effect not wired yet) */
    val appStyle = MutableStateFlow(prefs.getInt(KEY_APP_STYLE, 0))

    fun setAppStyle(style: Int) {
        prefs.edit().putInt(KEY_APP_STYLE, style).apply()
        appStyle.value = style
    }

    /** Monet dynamic color (placeholder — not wired yet) */
    val monetColor = MutableStateFlow(prefs.getBoolean(KEY_MONET_COLOR, false))

    fun setMonetColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONET_COLOR, enabled).apply()
        monetColor.value = enabled
    }

    /**
     * Monet palette style override.
     * PALETTE_FOLLOW_SYSTEM = -1: use the system wallpaper color style.
     * 0..8: force one of the Miuix ThemePaletteStyle values (see
     * MiuixAppTheme.PALETTE_STYLES for the ordering).
     */
    val monetPaletteStyle = MutableStateFlow(prefs.getInt(KEY_MONET_PALETTE_STYLE, PALETTE_FOLLOW_SYSTEM))

    fun setMonetPaletteStyle(style: Int) {
        prefs.edit().putInt(KEY_MONET_PALETTE_STYLE, style).apply()
        monetPaletteStyle.value = style
    }

    /**
     * Custom Monet seed color (ARGB), null = use the wallpaper-derived
     * system seed color. Values are constrained to the official Miuix
     * example KeyColors presets; any legacy value outside that list is
     * cleared so the UI ("跟随壁纸") and behavior stay consistent.
     */
    val monetSeedColor = MutableStateFlow(
        prefs.getLong(KEY_MONET_SEED_COLOR, -1L)
            .takeIf { it >= 0 }
            ?.takeIf { MonetPresetSeeds.contains(it) }
            .also { seed ->
                if (seed == null) {
                    prefs.edit().putLong(KEY_MONET_SEED_COLOR, -1L).apply()
                }
            }
    )

    fun setMonetSeedColor(argb: Long?) {
        prefs.edit().putLong(KEY_MONET_SEED_COLOR, argb ?: -1L).apply()
        monetSeedColor.value = argb
    }

    companion object {
        private const val KEY_COLOR_MODE = "color_mode"
        private const val KEY_APP_STYLE = "app_style"
        private const val KEY_MONET_COLOR = "monet_color"
        private const val KEY_MONET_PALETTE_STYLE = "monet_palette_style"
        private const val KEY_MONET_SEED_COLOR = "monet_seed_color"
        const val MODE_SYSTEM = 0
        const val MODE_LIGHT = 1
        const val MODE_DARK = 2
        const val STYLE_CLASSIC = 0
        const val STYLE_LIQUID_GLASS = 1
        const val PALETTE_FOLLOW_SYSTEM = -1

        /** Official Miuix example KeyColors, in dropdown order. */
        val MonetPresetSeeds = longArrayOf(
            0xFF3482FFL, // Blue
            0xFF36D167L, // Green
            0xFF7C4DFFL, // Purple
            0xFFFFB21DL, // Yellow
            0xFFFF5722L, // Orange
            0xFFE91E63L, // Pink
            0xFF00BCD4L, // Teal
        )
    }
}
