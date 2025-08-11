// File: app/src/main/java/com/insail/nmeagpsserver/SettingsActivity.kt
package com.insail.nmeagpsserver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: Toolbar = findViewById(R.id.settingsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.navigationIcon?.setTint(getColorFromAttr(R.attr.colorOnPrimary))

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, RootPrefsFragment())
                .commit()
        }
    }

    private fun Context.getColorFromAttr(attrColor: Int): Int {
        val ta = theme.obtainStyledAttributes(intArrayOf(attrColor))
        val color = ta.getColor(0, 0)
        ta.recycle()
        return color
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class RootPrefsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        // Ouvrir les logs système
        findPreference<Preference>("pref_system_logs")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), SystemLogsActivity::class.java))
            true
        }

        // Sélecteur de thème
        val themePref = findPreference<ListPreference>(ThemeManager.PREF_KEY_THEME)
        themePref?.let { lp ->
            // Résumé initial
            updateThemeSummary(lp)
            // Au changement: on enregistre via ThemeManager (qui incrémente la version)
            lp.setOnPreferenceChangeListener { _, newValue ->
                val value = newValue?.toString() ?: ThemeManager.THEME_LIGHT
                ThemeManager.setSelectedTheme(requireContext(), value)
                // Mettre à jour l'affichage local immédiatement
                lp.value = value
                updateThemeSummary(lp)
                // Recrée uniquement l'écran Settings (les autres se mettront à jour à leur reprise)
                requireActivity().recreate()
                true
            }
        }
    }

    private fun updateThemeSummary(lp: ListPreference) {
        val idx = lp.findIndexOfValue(lp.value ?: ThemeManager.THEME_LIGHT)
        if (idx >= 0) lp.summary = lp.entries[idx]
    }
}
