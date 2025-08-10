package com.insail.nmeagpsserver

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

/** Base Activity : applique le thème AVANT setContentView et se recrée si le thème change. */
open class ThemedActivity : AppCompatActivity() {
    private var themeVersionAtCreate: Int = 0
    private lateinit var prefs: SharedPreferences

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ThemeManager.PREF_KEY_THEME || key == "pref_theme_version") {
                if (!isFinishing) recreate()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Appliquer le thème choisi avant la création de l’activité
        ThemeManager.applyTheme(this)
        themeVersionAtCreate = ThemeManager.getThemeVersion(this)
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onStart() {
        super.onStart()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onStop() {
        super.onStop()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onResume() {
        super.onResume()
        if (ThemeManager.getThemeVersion(this) != themeVersionAtCreate) {
            recreate()
        }
    }
}
