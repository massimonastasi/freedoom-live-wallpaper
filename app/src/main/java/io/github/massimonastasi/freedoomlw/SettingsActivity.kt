/*
 * Freedoom Live Wallpaper
 * Copyright (C) 2026 Massimo Nastasi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy in
 * the file LICENSE; see also NOTICE.md for the third-party notices this work depends on.
 *
 * It is GPL-2.0 because it reproduces gameplay constants and tables from the id Software
 * engine source release (linuxdoom-1.10), which is GPL-2.0. Every such value carries a
 * comment naming the file and symbol it came from; those comments are the attribution the
 * licence requires and must not be removed.
 */
package io.github.massimonastasi.freedoomlw

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

/**
 * The settings screen, reached from the wallpaper picker's own Settings button and from the
 * launcher icon.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        /**
         * The document picker.
         *
         * OpenDocument rather than GetContent, because the persistable read permission is
         * what makes the copy possible without a second prompt. The MIME filter is the
         * wildcard: a WAD has no registered type, and filtering by extension would hide the
         * file on providers that do not expose one.
         */
        private val choose = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            val context = requireContext()
            val problem = WadStore.import(context, uri)
            if (problem == null) {
                Toast.makeText(context, R.string.wad_imported, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, problem, Toast.LENGTH_LONG).show()
            }
            showWadState()
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // The engine reads its own preference file directly, with no library on its
            // side: naming it here is what keeps the two looking at the same values.
            preferenceManager.sharedPreferencesName = Settings.FILE
            setPreferencesFromResource(R.xml.settings, rootKey)

            click(Settings.KEY_WAD) { choose.launch(arrayOf("*/*")) }
            click(Settings.KEY_WAD_RESET) {
                WadStore.clear(requireContext())
                Toast.makeText(requireContext(), R.string.wad_reset_done, Toast.LENGTH_SHORT).show()
                showWadState()
            }
            click(Settings.KEY_LICENCES) {
                startActivity(Intent(requireContext(), LicencesActivity::class.java))
            }
            click(Settings.KEY_SET_WALLPAPER) {
                startActivity(Intent(requireContext(), SetupActivity::class.java))
            }
            showWadState()
        }

        private fun click(key: String, action: () -> Unit) {
            findPreference<Preference>(key)?.setOnPreferenceClickListener { action(); true }
        }

        /** Says which sprites are in use, and hides the reset when there is nothing to reset. */
        private fun showWadState() {
            val file = WadStore.active(requireContext())
            findPreference<Preference>(Settings.KEY_WAD)?.summary = if (file == null) {
                getString(R.string.settings_wad_note)
            } else {
                getString(R.string.settings_wad_active, file.length() / (1024 * 1024))
            }
            findPreference<Preference>(Settings.KEY_WAD_RESET)?.isVisible = file != null
        }
    }
}
