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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import java.nio.channels.FileChannel

/**
 * The settings screen, reached from the wallpaper picker's own Settings button and from the
 * launcher icon.
 *
 * One activity over one layout. There is no preference library: the whole page is
 * res/layout/settings.xml, which means Android Studio draws it and it can be corrected by
 * looking rather than by installing. Every control is a Material 3 widget; the values go
 * straight to and from [Settings], which is three lines per row and is what the library was
 * doing underneath anyway.
 *
 * Choices apply as they are made. They were briefly staged behind a Save button, which is not
 * the Android convention and bought nothing: the wallpaper reads its settings when it next
 * becomes visible, so a staged copy was only a second state to keep in step with the first.
 */
class SettingsActivity : AppCompatActivity() {

    private val prefs by lazy { Settings.of(this) }

    /** The palette of whichever WAD is active, so the swatches show real colours. */
    private var palette = IntArray(256) { Color.BLACK }

    private val choosePhoto = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val ok = PhotoStore.import(this, uri)
        toast(if (ok) getString(R.string.photo_imported) else getString(R.string.photo_unreadable))
        showBackground()
    }

    private val chooseWad = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val problem = WadStore.import(this, uri)
        toast(problem ?: getString(R.string.wad_imported))
        if (problem == null) {
            // A new WAD brings its own palette, so the swatches have to be re-read.
            loadPalette()
            prefs.edit().putString(Settings.KEY_SPRITES, Settings.SPRITES_USER).apply()
        }
        showSprites()
        showBackground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)
        loadPalette()


        // Read from the package rather than written down, so it cannot disagree with the
        // build that is running.
        findViewById<TextView>(R.id.header_caption).text = getString(
            R.string.settings_version,
            packageManager.getPackageInfo(packageName, 0).versionName,
        )

        // The bar sits on the very bottom of the window and keeps its own content clear of
        // the navigation bar, rather than being pushed up and leaving a strip of the page
        // showing underneath. The page reserves the same height at its end.
        val bar = findViewById<View>(R.id.button_bar)
        ViewCompat.setOnApplyWindowInsetsListener(bar) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottom + view.paddingTop)
            insets
        }
        bar.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val spacer = findViewById<View>(R.id.bottom_spacer)
            val height = bottom - top
            if (spacer.layoutParams.height != height) {
                spacer.layoutParams = spacer.layoutParams.also { it.height = height }
                spacer.requestLayout()
            }
        }

        findViewById<MaterialButton>(R.id.set_wallpaper).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.reset).setOnClickListener { confirmReset() }
        findViewById<MaterialButton>(R.id.import_wad).setOnClickListener {
            chooseWad.launch(arrayOf("*/*"))
        }

        showFrameRate()
        showSwitches()
        showBackground()
        showSprites()
        showAbout()

        shapeGroup(R.id.switch_group)
        shapeGroup(R.id.background_group)
        shapeGroup(R.id.about_group)
    }

    // ------------------------------------------------------------------ sections

    private fun showFrameRate() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.fps_group)
        val ids = intArrayOf(R.id.fps_10, R.id.fps_15, R.id.fps_20)
        val values = intArrayOf(10, 15, 20)
        group.check(ids[values.indexOf(Settings.fps(prefs)).coerceAtLeast(0)])
        group.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val fps = values[ids.indexOf(id).coerceAtLeast(0)]
            prefs.edit().putString(Settings.KEY_FPS, fps.toString()).apply()
        }
    }

    private fun showSwitches() {
        switchRow(
            R.id.row_readout, R.string.settings_readout, R.string.settings_readout_note,
            Settings.KEY_READOUT, default = true,
        )
        switchRow(
            R.id.row_god, R.string.settings_god_mode, R.string.settings_god_mode_note,
            Settings.KEY_GOD_MODE, default = false,
        )
        switchRow(
            R.id.row_debug, R.string.settings_debug, R.string.settings_debug_note,
            Settings.KEY_DEBUG, default = false,
        )
    }

    /**
     * The background rows, and what the photo row says about itself.
     *
     * Once an image is chosen the row shows its file name rather than "from your device",
     * which tells nobody which image is in use, and its chevron becomes a bin - the same
     * gesture as the imported WAD, because it is the same kind of thing: a file the user put
     * there and is the only one who can take away.
     */
    private fun showBackground() {
        val chosen = prefs.getString(Settings.KEY_BACKGROUND, "dynamic")
        val rows = intArrayOf(R.id.row_floor, R.id.row_colour, R.id.row_photo)
        val values = arrayOf("dynamic", "colour", "photo")
        val photo = PhotoStore.name(this)

        row(R.id.row_floor, R.string.background_floor, getString(R.string.background_floor_note))
        row(R.id.row_colour, R.string.background_colour, getString(R.string.background_colour_note))
        row(R.id.row_photo, R.string.background_photo, photo ?: getString(R.string.background_photo_note))

        // The swatches live inside the flat-colour row, which is where the design puts them
        // and the only arrangement in which the radio clearly owns them.
        val extra = findViewById<View>(R.id.row_colour)
            .findViewById<android.widget.FrameLayout>(R.id.row_extra)
        if (extra.childCount == 0) {
            val grid = SwatchGrid(
                this,
                resources.getTextArray(R.array.palette_labels),
                resources.getTextArray(R.array.palette_values),
            )
            grid.colourOf = { palette[it.coerceIn(0, 255)] }
            grid.onChosen = { prefs.edit().putString(Settings.KEY_BACKGROUND_COLOUR, it).apply() }
            grid.show(prefs.getString(Settings.KEY_BACKGROUND_COLOUR, "0"))
            extra.addView(grid)
        }
        extra.visibility = View.VISIBLE

        // Choosing "Image" with nothing behind it would show nothing, so it asks; once there
        // is one, the trailing button removes it.
        if (photo != null) {
            action(R.id.row_photo, R.drawable.ic_delete) {
                PhotoStore.clear(this)
                showBackground()
            }
        } else {
            pointer(R.id.row_photo, R.drawable.ic_chevron)
        }

        rows.forEachIndexed { i, id ->
            select(id, values[i] == chosen)
            findViewById<View>(id).setOnClickListener {
                prefs.edit().putString(Settings.KEY_BACKGROUND, values[i]).apply()
                if (values[i] == "photo" && PhotoStore.file(this) == null) {
                    choosePhoto.launch(arrayOf("image/*"))
                }
                showBackground()
            }
        }
    }

    /** The sprite sets on disk: the bundled one, plus an imported WAD if there is one. */
    private fun showSprites() {
        val user = WadStore.active(this)
        val useUser = user != null && Settings.useUserWad(prefs)

        row(R.id.row_bundled, R.string.sprites_bundled, getString(R.string.sprites_bundled_note))
        select(R.id.row_bundled, !useUser)

        val wadRow = findViewById<View>(R.id.row_wad)
        // The row exists only when the file does. Nothing is bundled beyond Freedoom, so at
        // first launch there is one option and it is not offered as a choice.
        wadRow.visibility = if (user == null) View.GONE else View.VISIBLE
        shapeGroup(R.id.sprites_group)
        findViewById<View>(R.id.row_bundled).findViewById<View>(R.id.row_radio).isEnabled = user != null

        if (user != null) {
            // Named, not described: someone who has imported more than one over a week needs
            // to see which file is in, and "your own WAD" answers no question.
            val label = WadStore.name(this) ?: getString(R.string.wad_unnamed)
            val row = wadRow.findViewById<TextView>(R.id.row_label)
            row.text = label
            caption(R.id.row_wad, getString(R.string.sprites_size, user.length() / 1024))
            select(R.id.row_wad, useUser)
            action(R.id.row_wad, R.drawable.ic_delete) { confirmDeleteWad() }
            shapeGroup(R.id.sprites_group)
            wadRow.setOnClickListener {
                prefs.edit().putString(Settings.KEY_SPRITES, Settings.SPRITES_USER).apply()
                showSprites()
            }
            findViewById<View>(R.id.row_bundled).setOnClickListener {
                prefs.edit().putString(Settings.KEY_SPRITES, Settings.SPRITES_BUNDLED).apply()
                showSprites()
            }
        }
    }

    private fun showAbout() {
        // Source is shown and disabled: the repository is not public yet, so the row says the
        // source exists and will be reachable rather than appearing and disappearing between
        // versions. Disabling the whole row rather than only its click means it also reads as
        // unavailable instead of looking broken when tapped.
        row(R.id.row_source, R.string.settings_source, getString(R.string.settings_source_note))
        findViewById<View>(R.id.row_source).apply {
            findViewById<View>(R.id.row_radio).visibility = View.GONE
            isEnabled = false
            alpha = DISABLED_ALPHA
        }

        row(R.id.row_licences, R.string.settings_licences, getString(R.string.settings_licences_note))
        findViewById<View>(R.id.row_licences).findViewById<View>(R.id.row_radio).visibility = View.GONE
        pointer(R.id.row_licences, R.drawable.ic_chevron)
        findViewById<View>(R.id.row_licences).setOnClickListener { openLicences() }
    }

    private fun openLicences() = startActivity(Intent(this, LicencesActivity::class.java))

    /**
     * Gives every visible row in a group the corner shape for where it sits.
     *
     * Material ships the four shapes - Single, First, Middle, Last - and a segmented list is
     * just the right one on each row: big corners at the ends of the group, small in between.
     * Doing it here rather than in the layout is what lets a row appear and disappear, which
     * two of these groups do: the WAD row exists only once one is imported, and the group has
     * to close up around it.
     */
    private fun shapeGroup(groupId: Int) {
        val group = findViewById<android.view.ViewGroup>(groupId)
        val rows = (0 until group.childCount)
            .map { group.getChildAt(it) }
            .filter { it.visibility == View.VISIBLE }
            .filterIsInstance<com.google.android.material.card.MaterialCardView>()

        rows.forEachIndexed { i, card ->
            val style = when {
                rows.size == 1 -> com.google.android.material.R.style.ShapeAppearance_Material3_ListItem_Single
                i == 0 -> com.google.android.material.R.style.ShapeAppearance_Material3_ListItem_First
                i == rows.lastIndex -> com.google.android.material.R.style.ShapeAppearance_Material3_ListItem_Last
                else -> com.google.android.material.R.style.ShapeAppearance_Material3_ListItem_Middle
            }
            card.shapeAppearanceModel = com.google.android.material.shape.ShapeAppearanceModel
                .builder(this, style, 0)
                .build()
        }
    }

    // ------------------------------------------------------------------ row helpers

    /** Label and supporting line. The row is one included layout, so this is how it is filled. */
    private fun row(id: Int, label: Int, caption: String?) {
        val row = findViewById<View>(id)
        row.findViewById<TextView>(R.id.row_label).setText(label)
        caption(id, caption)
    }

    private fun caption(id: Int, text: String?) {
        findViewById<View>(id).findViewById<TextView>(R.id.row_caption).apply {
            this.text = text
            visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }

    /** activated, not selected: selected is a transient touch state, activated persists. */
    private fun select(id: Int, on: Boolean) {
        val row = findViewById<View>(id)
        row.isActivated = on
        row.findViewById<com.google.android.material.radiobutton.MaterialRadioButton>(R.id.row_radio)
            .isChecked = on
    }

    /**
     * A trailing action with its own target: a button, for deleting a file the user imported.
     */
    private fun action(id: Int, icon: Int, onClick: () -> Unit) {
        val row = findViewById<View>(id)
        row.findViewById<View>(R.id.row_icon).visibility = View.GONE
        row.findViewById<MaterialButton>(R.id.row_action).apply {
            visibility = View.VISIBLE
            setIconResource(icon)
            setOnClickListener { onClick() }
        }
    }

    /**
     * A trailing icon that only points: the row itself is what gets tapped.
     *
     * Not a button. It has no container of its own and is outside the accessibility tree,
     * because a control announced beside a row that does the same thing is one target too
     * many.
     */
    private fun pointer(id: Int, icon: Int) {
        val row = findViewById<View>(id)
        row.findViewById<MaterialButton>(R.id.row_action).visibility = View.GONE
        row.findViewById<android.widget.ImageView>(R.id.row_icon).apply {
            visibility = View.VISIBLE
            setImageResource(icon)
        }
    }

    private fun switchRow(id: Int, label: Int, caption: Int, key: String, default: Boolean) {
        val root = findViewById<View>(id)
        root.findViewById<View>(R.id.row_radio).visibility = View.GONE
        row(id, label, getString(caption))

        val toggle = root.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.row_switch)
        toggle.visibility = View.VISIBLE
        var on = prefs.getBoolean(key, default)
        toggle.isChecked = on
        root.isActivated = on
        root.setOnClickListener {
            on = !on
            prefs.edit().putBoolean(key, on).apply()
            toggle.isChecked = on
            root.isActivated = on
        }
    }

    // ------------------------------------------------------------------ actions

    private fun confirmDeleteWad() {
        AlertDialog.Builder(this)
            .setTitle(R.string.wad_delete)
            .setMessage(R.string.wad_delete_confirm)
            .setPositiveButton(R.string.wad_delete) { _, _ ->
                WadStore.clear(this)
                prefs.edit().putString(Settings.KEY_SPRITES, Settings.SPRITES_BUNDLED).apply()
                loadPalette()
                showSprites()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Puts everything back as it was installed, files included.
     *
     * The imported WAD and photo go too: they are the only things here that occupy real
     * storage, and a reset that left tens of megabytes behind would not be one.
     */
    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_reset)
            .setMessage(R.string.settings_reset_confirm)
            .setPositiveButton(R.string.settings_reset) { _, _ ->
                prefs.edit().clear().apply()
                WadStore.clear(this)
                PhotoStore.clear(this)
                loadPalette()
                // Recreated rather than refreshed: every row's value has changed underneath
                // the views, and rebuilding is the honest way to show that.
                recreate()
                toast(getString(R.string.settings_reset_done))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Reads the active WAD's palette, so the swatches are the wallpaper's own colours. */
    private fun loadPalette() {
        palette = try {
            val user = WadStore.active(this)
            val buf = if (user != null) {
                user.inputStream().use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, user.length()) }
            } else {
                val afd = assets.openFd("freedoom1.wad")
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

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private companion object {
        /** Material 3 draws a disabled control at 38% opacity. */
        const val DISABLED_ALPHA = 0.38f
    }
}
