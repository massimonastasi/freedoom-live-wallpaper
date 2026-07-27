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
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.nio.channels.FileChannel
import kotlin.math.abs

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

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val buttons = findViewById<ViewGroup>(R.id.button_bar)
        // The bar sits on the very bottom of the window and keeps its own content clear of
        // the navigation bar, rather than being pushed up by it and leaving a strip of the
        // list showing underneath.
        ViewCompat.setOnApplyWindowInsetsListener(buttons) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottom + view.paddingTop)
            insets
        }

        findViewById<MaterialButton>(R.id.set_wallpaper).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.reset).setOnClickListener { fragment.confirmReset() }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        /** The palette of whichever WAD is active, so the swatches show real colours. */
        private var palette = IntArray(256) { Color.BLACK }

        private var swatches: SwatchGrid? = null

        private val choosePhoto = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            val ok = PhotoStore.import(requireContext(), uri)
            Toast.makeText(
                requireContext(),
                if (ok) R.string.photo_imported else R.string.photo_unreadable,
                Toast.LENGTH_LONG,
            ).show()
        }

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
                Settings.of(requireContext()).edit()
                    .putString(Settings.KEY_SPRITES, Settings.SPRITES_USER).apply()
            }
            showSprites()
        }

        /**
         * Keeps the list clear of the buttons pinned over it.
         *
         * The two buttons are siblings of the scrolling view, not part of it, so nothing tells
         * the list they are there: the last section scrolled underneath them and could not be
         * reached. Padding with clipToPadding off means the content still scrolls behind them,
         * which is what should happen, but it can also scroll past them.
         */
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            // No dividers. The design separates sections with their headers and the gap
            // around them; the library draws a hairline between every row on top of that.
            setDivider(null)
            setDividerHeight(0)
            listView.clipToPadding = false
            val bar = requireActivity().findViewById<View>(R.id.button_bar)
            // Its real height, taken once it has been laid out, rather than a constant that
            // has to be kept in step with the layout by hand. It was 176dp against a bar that
            // is taller than that with the navigation inset added, so the last row sat under
            // the buttons.
            bar.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
                val height = bottom - top
                if (listView.paddingBottom != height) {
                    listView.setPadding(0, 0, 0, height)
                }
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = Settings.FILE
            setPreferencesFromResource(R.xml.settings, rootKey)
            loadPalette()

            findPreference<OptionListPreference>(Settings.KEY_BACKGROUND)?.apply {
                // The swatches belong inside the flat-colour row, not in a block under it.
                extraFor = { i -> if (i == COLOUR_ROW) swatchGrid() else null }
                // Only the photo row carries a chevron, and it does the same thing as choosing
                // the row: picking "Image" with no image behind it would show nothing.
                trailingIcon = { i -> if (i == PHOTO_ROW) R.drawable.ic_chevron else 0 }
                onTrailing = { choosePhoto.launch(arrayOf("image/*")) }
                onChosen = { value ->
                    if (value == "photo" && PhotoStore.file(requireContext()) == null) {
                        choosePhoto.launch(arrayOf("image/*"))
                    }
                }
            }

            findPreference<ButtonPreference>(Settings.KEY_WAD)?.setOnPreferenceClickListener {
                chooseWad.launch(arrayOf("*/*")); true
            }

            findPreference<LinkRowPreference>(Settings.KEY_NOTICES)?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), LicencesActivity::class.java)); true
            }

            // Hidden rather than pointed at nothing: the repository is not public yet, and a
            // row that opens a 404 is worse than a row that is not there.
            findPreference<LinkRowPreference>(Settings.KEY_SOURCE)?.isVisible = false

            // The version, at the end of About. It was under the header title until that
            // title became the collapsing layout's own, which carries one line and no
            // caption. Read from the package rather than written down, so it cannot disagree
            // with the build that is running.
            findPreference<ParagraphPreference>("about_note")?.let {
                it.body = getString(
                    R.string.settings_about_note_versioned,
                    it.body,
                    requireContext().packageManager
                        .getPackageInfo(requireContext().packageName, 0).versionName,
                )
            }

            showSprites()
        }

        /**
         * The background rows, and what the photo row says about itself.
         *
         * Once an image is chosen the row shows its file name rather than "from your device",
         * which tells nobody which image is in use, and the chevron becomes a bin - the same
         * gesture as the imported WAD, because it is the same kind of thing: a file the user
         * put there and is the only one who can take away.
         */
        private fun showBackground() {
            val photo = PhotoStore.name(requireContext())
            findPreference<OptionListPreference>(Settings.KEY_BACKGROUND)?.apply {
                val captions = resources.getTextArray(R.array.background_captions)
                if (photo != null) captions[PHOTO_ROW] = photo
                setOptions(
                    resources.getTextArray(R.array.background_labels),
                    resources.getTextArray(R.array.background_values),
                    captions,
                )
                // The swatches belong inside the flat-colour row, not in a block under it.
                extraFor = { i -> if (i == COLOUR_ROW) swatchGrid() else null }
                trailingIcon = { i ->
                    when {
                        i != PHOTO_ROW -> 0
                        photo != null -> R.drawable.ic_delete
                        else -> R.drawable.ic_chevron
                    }
                }
                onTrailing = {
                    if (photo != null) {
                        PhotoStore.clear(requireContext())
                        showBackground()
                    } else {
                        choosePhoto.launch(arrayOf("image/*"))
                    }
                }
                // Choosing "Image" with nothing behind it would show nothing, so it asks.
                onChosen = { value ->
                    if (value == "photo" && PhotoStore.file(requireContext()) == null) {
                        choosePhoto.launch(arrayOf("image/*"))
                    }
                }
            }
        }

        /** The sprite sets on disk: the bundled one, plus an imported WAD if there is one. */
        private fun showSprites() {
            val context = requireContext()
            val user = WadStore.active(context)
            findPreference<OptionListPreference>(Settings.KEY_SPRITES)?.apply {
                if (user == null) {
                    setOptions(
                        arrayOf(getString(R.string.sprites_bundled)),
                        arrayOf(Settings.SPRITES_BUNDLED),
                        arrayOf(getString(R.string.sprites_bundled_note)),
                    )
                    // One option is not a choice: shown, so it is clear what is in use, but
                    // not offered as if something could be picked.
                    choosable = false
                    trailingIcon = { 0 }
                } else {
                    // Named, not described: someone who has imported more than one over a week
                    // needs to see which file is in, and "your own WAD" answers no question.
                    val label = WadStore.name(context) ?: getString(R.string.wad_unnamed)
                    setOptions(
                        arrayOf(getString(R.string.sprites_bundled), label),
                        arrayOf(Settings.SPRITES_BUNDLED, Settings.SPRITES_USER),
                        arrayOf(
                            getString(R.string.sprites_bundled_note),
                            getString(R.string.sprites_size, user.length() / 1024),
                        ),
                    )
                    choosable = true
                    // Only the imported row can be deleted. The bundled set came with the app.
                    trailingIcon = { i -> if (i == 1) R.drawable.ic_delete else 0 }
                    onTrailing = { confirmDeleteWad() }
                }
            }
        }

        private fun confirmDeleteWad() {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.wad_delete)
                .setMessage(R.string.wad_delete_confirm)
                .setPositiveButton(R.string.wad_delete) { _, _ ->
                    WadStore.clear(requireContext())
                    Settings.of(requireContext()).edit()
                        .putString(Settings.KEY_SPRITES, Settings.SPRITES_BUNDLED).apply()
                    loadPalette()
                    swatches = null
                    preferenceScreen = null
                    onCreatePreferences(null, null)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        private fun swatchGrid(): SwatchGrid {
            swatches?.let { return it }
            val prefs = Settings.of(requireContext())
            val grid = SwatchGrid(
                requireContext(),
                resources.getTextArray(R.array.palette_labels),
                resources.getTextArray(R.array.palette_values),
            )
            grid.colourOf = { palette[it.coerceIn(0, 255)] }
            grid.onChosen = { value ->
                prefs.edit().putString(Settings.KEY_BACKGROUND_COLOUR, value).apply()
            }
            grid.show(prefs.getString(Settings.KEY_BACKGROUND_COLOUR, "0"))
            swatches = grid
            return grid
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
                    swatches = null
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
            /** Indices into background_labels. */
            const val PHOTO_ROW = 1
            const val COLOUR_ROW = 2
        }
    }
}
