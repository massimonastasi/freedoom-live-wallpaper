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
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The third-party notices, shown from the settings screen.
 *
 * This exists because it has to. The bundled Freedoom assets are under a BSD 3-clause
 * licence whose second condition requires the copyright notice, the conditions and the
 * disclaimer to be reproduced "in the documentation and/or other materials provided with the
 * distribution" — an APK ships with no documentation, so this screen is that material. The
 * GPL text is here for the same reason.
 *
 * Read from assets copied out of the repository at build time, so the files in the
 * repository stay the single source of truth and cannot drift from what is shown.
 */
class LicencesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        for (name in listOf("NOTICE.md", "LICENSE")) {
            column.addView(TextView(this).apply {
                text = read(name)
                // Monospaced: the GPL is laid out in fixed columns and reflows badly.
                typeface = Typeface.MONOSPACE
                textSize = 11f
                setPadding(0, 0, 0, pad * 2)
                setTextIsSelectable(true)
            })
        }

        setContentView(ScrollView(this).apply {
            addView(column, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        })
    }

    private fun read(name: String): String = try {
        assets.open(name).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        // Never silently blank: a licence screen that shows nothing is worse than one that
        // says it could not read the file, because only the second is noticed.
        getString(R.string.licences_missing, name)
    }
}
