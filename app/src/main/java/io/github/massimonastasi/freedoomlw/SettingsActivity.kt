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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.nio.channels.FileChannel

/**
 * The settings screen, reached from the wallpaper picker's own Settings button and from the
 * launcher icon.
 *
 * Choices apply as they are made. They were briefly staged behind a Save button, which is not
 * the Android convention and turned out to buy nothing: the wallpaper reads its settings when
 * it next becomes visible, so a staged copy was only a second state to keep in step with the
 * first. What is left at the bottom is the two things that act on everything above them.
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

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // The version, under the title. Read from the package rather than written down, so it
        // cannot disagree with the build that is running.
        findViewById<TextView>(R.id.header_caption).text = getString(
            R.string.settings_version,
            packageManager.getPackageInfo(packageName, 0).versionName,
        )

        findViewById<MaterialButton>(R.id.set_wallpaper).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.reset).setOnClickListener { fragment.confirmReset() }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        /** The palette of whichever WAD is active, so the swatches show real colours. */
        private var palette = IntArray(256) { Color.BLACK }

        private val choosePhoto = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            val ok = PhotoStore.import(requireContext(), uri)
            Toast.makeText(
                requireContext(),
                if (ok) R.string.photo_imported else R.string.photo_unreadable,
                Toast.LENGTH_LONG,
            ).show()
        }

        /**
         * Keeps the list clear of the buttons pinned over it.
         *
         * The two buttons are siblings of the scrolling view, not part of it, so nothing
         * tells the list they are there: the last section scrolled underneath them and could
         * not be reached. Padding with clipToPadding off means the content still scrolls
         * behind them, which is what should happen, but it can also scroll past them.
         */
        override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val density = resources.displayMetrics.density
            listView.clipToPadding = false
            listView.setPadding(0, 0, 0, (160 * density).toInt())
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = Settings.FILE
            setPreferencesFromResource(R.xml.settings, rootKey)
            loadPalette()

            findPreference<SwatchListPreference>(Settings.KEY_BACKGROUND_COLOUR)?.colourOf = {
                palette[it.coerceIn(0, 255)]
            }

            findPreference<OptionListPreference>(Settings.KEY_BACKGROUND)?.apply {
                // Only the photo row carries a button, and it does the same thing as choosing
                // the row: picking "Image" with no image behind it would show nothing.
                trailingIcon = { i -> if (i == PHOTO_ROW) R.drawable.ic_chevron else 0 }
                onTrailing = { choosePhoto.launch(arrayOf("image/*")) }
                onChosen = { value ->
                    if (value == "photo" && PhotoStore.file(requireContext()) == null) {
                        choosePhoto.launch(arrayOf("image/*"))
                    }
                }
            }
        }

        /**
         * Puts everything back as it was installed, files included.
         *
         * The imported WAD and photo go too: they are the only things here that occupy real
         * storage, and a reset that left tens of megabytes behind would not be one.
         */
        fun confirmReset() {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_reset)
                .setMessage(R.string.settings_reset_confirm)
                .setPositiveButton(R.string.settings_reset) { _, _ ->
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

        private companion object {
            /** Index of "Image" in background_labels. */
            const val PHOTO_ROW = 1
        }
    }
}
