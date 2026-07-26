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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * The settings screen, reached from the wallpaper picker's own Settings button and from the
 * launcher icon.
 *
 * ponytail: views built in code rather than a layout plus a preference library. The screen
 * is three controls and two buttons, and the project ships with no runtime dependencies at
 * all — adding one to draw five rows would be a poor trade.
 */
class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Settings.of(this)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun heading(text: String) = column.addView(TextView(this).apply {
            this.text = text
            textSize = 20f
            setPadding(0, pad, 0, pad / 2)
        })

        fun note(text: String) = column.addView(TextView(this).apply {
            this.text = text
            alpha = 0.7f
            setPadding(0, 0, 0, pad / 2)
        })

        fun toggle(label: String, on: Boolean, set: (Boolean) -> Unit) =
            column.addView(Switch(this).apply {
                text = label
                isChecked = on
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                setOnCheckedChangeListener { _, v -> set(v) }
            })

        fun button(label: String, action: () -> Unit) =
            column.addView(Button(this).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                setOnClickListener { action() }
            })

        heading(getString(R.string.settings_frame_rate))
        note(getString(R.string.settings_frame_rate_note))

        // A row of frame rates rather than a slider: there are three, and a slider would
        // imply values in between that the draw loop does not offer.
        val rates = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val buttons = mutableListOf<Button>()
        fun paintRates() {
            val chosen = Settings.fps(prefs)
            buttons.forEachIndexed { i, b -> b.alpha = if (Settings.FPS_CHOICES[i] == chosen) 1f else 0.45f }
        }
        for (fps in Settings.FPS_CHOICES) {
            val b = Button(this).apply {
                text = getString(R.string.settings_fps, fps)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                setOnClickListener { Settings.setFps(prefs, fps); paintRates() }
            }
            buttons += b
            rates.addView(b)
        }
        column.addView(rates)
        paintRates()

        heading(getString(R.string.settings_scene))
        toggle(getString(R.string.settings_readout), Settings.readout(prefs)) {
            Settings.setReadout(prefs, it)
        }
        toggle(getString(R.string.settings_god), Settings.godMode(prefs)) {
            Settings.setGodMode(prefs, it)
        }

        heading(getString(R.string.settings_about))
        note(getString(R.string.settings_about_note))
        button(getString(R.string.settings_licences)) {
            startActivity(Intent(this, LicencesActivity::class.java))
        }
        button(getString(R.string.settings_set_wallpaper)) {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        setContentView(ScrollView(this).apply {
            addView(column, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            // Edge to edge is the default under targetSdk 36, so the content has to keep
            // clear of the bars itself rather than assume an inset.
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        })
    }
}
