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
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.nio.channels.FileChannel

/**
 * The settings screen, reached from the wallpaper picker's own Settings button and from the
 * launcher icon.
 *
 * Choices are held until Save rather than applied as they are made, which is not the Android
 * convention and is a deliberate choice: see StagedStore. Importing a file is the exception —
 * that is an action rather than a setting, and staging a copy of tens of megabytes to apply
 * later would be a strange thing to do. What is staged is which of them is used.
 */
class SettingsActivity : AppCompatActivity() {

    private val fragment = SettingsFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, fragment)
                .commit()
        }

        // Leaving with unsaved choices silently would lose them without saying so. Registered
        // as a callback rather than by overriding onBackPressed, which predictive back under
        // targetSdk 36 no longer routes through.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!fragment.hasUnsaved()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    return
                }
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle(R.string.settings_unsaved_title)
                    .setMessage(R.string.settings_unsaved_message)
                    .setPositiveButton(R.string.settings_save) { _, _ ->
                        fragment.save()
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                    .setNegativeButton(R.string.settings_discard) { _, _ ->
                        fragment.discard()
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                    .setNeutralButton(R.string.settings_keep_editing, null)
                    .show()
            }
        })
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var staged: StagedStore

        /** The palette of whichever WAD is active, so the swatches show real colours. */
        private var palette = IntArray(256) { Color.BLACK }

        private val chooseWad = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            val problem = WadStore.import(requireContext(), uri)
            Toast.makeText(
                requireContext(),
                problem ?: getString(R.string.wad_imported),
                Toast.LENGTH_LONG,
            ).show()
            if (problem == null) {
                // A new WAD brings its own palette, so the swatches have to be re-read.
                loadPalette()
                staged.putString(Settings.KEY_SPRITES, Settings.SPRITES_USER)
            }
            refresh()
        }

        private val choosePhoto = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            val ok = PhotoStore.import(requireContext(), uri)
            Toast.makeText(
                requireContext(),
                if (ok) R.string.photo_imported else R.string.photo_unreadable,
                Toast.LENGTH_LONG,
            ).show()
            refresh()
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            staged = StagedStore(Settings.of(requireContext()))
            preferenceManager.preferenceDataStore = staged
            setPreferencesFromResource(R.xml.settings, rootKey)
            loadPalette()

            findPreference<SwatchListPreference>(Settings.KEY_BACKGROUND_COLOUR)?.colourOf = {
                palette[it.coerceIn(0, 255)]
            }

            // The two conditional rows follow the background choice as it is made, not only
            // when the screen is reopened.
            findPreference<Preference>(Settings.KEY_BACKGROUND)?.setOnPreferenceChangeListener { _, v ->
                showBackgroundExtras(v as String); true
            }

            click(Settings.KEY_BACKGROUND_PHOTO) { choosePhoto.launch(arrayOf("image/*")) }
            click(Settings.KEY_WAD) { chooseWad.launch(arrayOf("*/*")) }
            click(Settings.KEY_SAVE) { save(); Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show() }
            click(Settings.KEY_SET_WALLPAPER) {
                save()
                startActivity(Intent(requireContext(), SetupActivity::class.java))
            }
            click(Settings.KEY_RESET) { confirmReset() }

            findPreference<TextBlockPreference>(Settings.KEY_NOTICES)?.body = notices()
            refresh()
        }

        fun hasUnsaved(): Boolean = staged.dirty

        fun save() = staged.commit()

        fun discard() = staged.discard()

        /**
         * Puts everything back as it was installed, files included.
         *
         * The imported WAD and photo go too: they are the only things here that occupy real
         * storage, and a reset that left tens of megabytes behind would not be one.
         */
        private fun confirmReset() {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_reset)
                .setMessage(R.string.settings_reset_confirm)
                .setPositiveButton(R.string.settings_reset) { _, _ ->
                    staged.discard()
                    Settings.of(requireContext()).edit().clear().apply()
                    WadStore.clear(requireContext())
                    PhotoStore.clear(requireContext())
                    loadPalette()
                    // Rebuilt rather than refreshed: every row's value has changed underneath
                    // the views, and re-inflating is the honest way to show that.
                    preferenceScreen = null
                    onCreatePreferences(null, null)
                    Toast.makeText(requireContext(), R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        private fun click(key: String, action: () -> Unit) {
            findPreference<Preference>(key)?.setOnPreferenceClickListener { action(); true }
        }

        /** Reflects what is on disk: which sprite sets exist, and whether a photo is set. */
        private fun refresh() {
            val context = requireContext()
            val user = WadStore.active(context)

            findPreference<RadioListPreference>(Settings.KEY_SPRITES)?.apply {
                if (user == null) {
                    setOptions(arrayOf(getString(R.string.sprites_bundled)), arrayOf(Settings.SPRITES_BUNDLED))
                    // One option is not a choice: shown, so it is clear what is in use, but
                    // not offered as if something could be picked.
                    choosable = false
                } else {
                    setOptions(
                        arrayOf(
                            getString(R.string.sprites_bundled),
                            getString(R.string.sprites_user, user.length() / 1024),
                        ),
                        arrayOf(Settings.SPRITES_BUNDLED, Settings.SPRITES_USER),
                    )
                    choosable = true
                }
            }

            findPreference<Preference>(Settings.KEY_BACKGROUND_PHOTO)?.setSummary(
                if (PhotoStore.file(context) == null) R.string.settings_background_photo_none
                else R.string.settings_background_photo_set
            )

            showBackgroundExtras(staged.getString(Settings.KEY_BACKGROUND, "dynamic") ?: "dynamic")
        }

        private fun showBackgroundExtras(mode: String) {
            findPreference<Preference>(Settings.KEY_BACKGROUND_PHOTO)?.isVisible = mode == "photo"
            findPreference<Preference>(Settings.KEY_BACKGROUND_COLOUR)?.isVisible = mode == "colour"
        }

        /** Reads the active WAD's palette, so the swatches are the wallpaper's own colours. */
        private fun loadPalette() {
            val context = requireContext()
            palette = try {
                val user = WadStore.active(context)
                val buf = if (user != null) {
                    user.inputStream().use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, user.length()) }
                } else {
                    val afd = context.assets.openFd("freedoom1.wad")
                    afd.createInputStream().use { s ->
                        s.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.length)
                    }
                }
                val wad = WadFile(buf)
                IntArray(256) { wad.paletteColor(it) }
            } catch (e: Exception) {
                IntArray(256) { Color.BLACK }
            }
        }

        private fun notices(): CharSequence = try {
            val assets = requireContext().assets
            listOf("NOTICE.md", "LICENSE").joinToString("\n\n\n") { name ->
                assets.open(name).bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            getString(R.string.licences_missing, "NOTICE.md")
        }
    }
}
