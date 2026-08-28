package com.chayewuu.hypermatter.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-level settings stored in SharedPreferences.
 * Tracks the dark-mode preference (System / Light / Dark).
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

    companion object {
        private const val KEY_COLOR_MODE = "color_mode"
        const val MODE_SYSTEM = 0
        const val MODE_LIGHT = 1
        const val MODE_DARK = 2
    }
}
