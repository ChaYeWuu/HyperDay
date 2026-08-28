package com.chayewuu.hypermatter.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-level settings stored in SharedPreferences.
 * Tracks the dark-mode preference (System / Light / Dark) and the
 * advanced-material (frosted-glass blur) toggle.
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

    /** Frosted-glass blur on the detail-page card and buttons (API 33+). */
    val advancedMaterial = MutableStateFlow(prefs.getBoolean(KEY_ADVANCED_MATERIAL, false))

    fun setAdvancedMaterial(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ADVANCED_MATERIAL, enabled).apply()
        advancedMaterial.value = enabled
    }

    companion object {
        private const val KEY_COLOR_MODE = "color_mode"
        private const val KEY_ADVANCED_MATERIAL = "advanced_material"
        const val MODE_SYSTEM = 0
        const val MODE_LIGHT = 1
        const val MODE_DARK = 2
    }
}
