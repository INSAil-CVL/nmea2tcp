// File: app/src/main/java/com/insail/nmeagpsserver/ThemeManager.kt
package com.insail.nmeagpsserver

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

object ThemeManager {
    const val PREF_KEY_THEME = "pref_theme"
    private const val PREF_KEY_THEME_VERSION = "pref_theme_version"

    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_RED = "red"

    /** Applique le thème choisi avant setContentView */
    fun applyTheme(activity: Activity) {
        when (getSelectedTheme(activity)) {
            THEME_LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                activity.setTheme(R.style.Theme_NmeaGps_Light)
            }
            THEME_DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                activity.setTheme(R.style.Theme_NmeaGps_Dark)
            }
            THEME_RED -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                activity.setTheme(R.style.Theme_NmeaGps_NightRed)
            }
        }
    }

    fun getSelectedTheme(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(PREF_KEY_THEME, THEME_LIGHT) ?: THEME_LIGHT
    }

    /** Setter centralisé : enregistre la valeur + incrémente la version */
    fun setSelectedTheme(context: Context, value: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putString(PREF_KEY_THEME, value)
            .putInt(PREF_KEY_THEME_VERSION, getThemeVersion(context) + 1)
            .apply()
    }

    fun getThemeVersion(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getInt(PREF_KEY_THEME_VERSION, 0)
    }
}
