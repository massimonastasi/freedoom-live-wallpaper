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

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.GridLayout

/**
 * The flat colours, as circles in two rows of five.
 *
 * A view rather than a preference of its own: the design puts these *inside* the "Flat
 * colour" row, not underneath it as a separate block, and a preference is always its own row.
 * The value is still persisted through the preference store, by whoever hosts this.
 *
 * Five columns rather than a scrolling strip, so every colour is on screen at once and none
 * can hide past the edge.
 */
class SwatchGrid(
    context: Context,
    private val labels: Array<CharSequence>,
    private val values: Array<CharSequence>,
) : GridLayout(context) {

    /** Resolves a palette index to a colour. Supplied by the screen, which has the WAD. */
    var colourOf: (Int) -> Int = { 0 }

    /** Called with the chosen palette index, as a string, so it persists like any other. */
    var onChosen: (String) -> Unit = {}

    private var current: String? = null

    /** The swatch size the rows were last built at, so a relayout only rebuilds on a change. */
    private var builtAt = 0

    init {
        columnCount = COLUMNS
        // The size of a swatch is a share of the width the row leaves, which is not known
        // until the row has been measured - and measuring it depends on the children, so
        // asking for it before building them can never settle. The rows are therefore built
        // straight away at the largest size, and rebuilt if the width turns out to be smaller.
        //
        // That is what fixes the fifth swatch of each line falling off the right edge of the
        // card on a screen narrower than the one the fixed 40dp was chosen on.
        addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            if (right - left > 0 && sizeFor(right - left) != builtAt) show(current)
        }
    }

    /** A share of the available width, never bigger than the design's swatch. */
    private fun sizeFor(available: Int): Int {
        val dp = resources.displayMetrics.density
        val gap = (8 * dp).toInt()
        return ((available - gap * (COLUMNS - 1)) / COLUMNS)
            .coerceIn((20 * dp).toInt(), (40 * dp).toInt())
    }

    fun show(chosen: String?) {
        current = chosen
        removeAllViews()
        val dp = resources.displayMetrics.density
        val gap = (8 * dp).toInt()
        val size = sizeFor(if (width > 0) width else Int.MAX_VALUE)
        builtAt = size

        labels.forEachIndexed { i, label ->
            val value = values.getOrNull(i)?.toString() ?: return@forEachIndexed
            val palette = value.toIntOrNull() ?: 0
            val selected = value == current
            val swatch = View(context).apply {
                // A ring that thickens when chosen, rather than a tick drawn over the colour:
                // a tick would be invisible on half of these - white on cream, black on black
                // - and a ring never collides with what it marks.
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colourOf(palette))
                    setStroke(
                        ((if (selected) 3 else 1) * dp).toInt(),
                        if (selected) primaryColour() else OUTLINE,
                    )
                }
                contentDescription = label
                setOnClickListener {
                    if (value != current) {
                        current = value
                        onChosen(value)
                        show(value)
                    }
                }
            }
            // No trailing margin on the last of a row, which is what pushed the group past
            // the width it was given.
            addView(swatch, LayoutParams().apply {
                width = size
                height = size
                setMargins(0, 0, if (i % COLUMNS == COLUMNS - 1) 0 else gap, gap)
            })
        }
    }

    /** The theme's own primary, so the chosen ring follows the system palette. */
    private fun primaryColour(): Int =
        com.google.android.material.color.MaterialColors.getColor(
            this, androidx.appcompat.R.attr.colorPrimary,
        )

    private companion object {
        const val COLUMNS = 5

        /** Faint, so black and the surface behind it do not merge into one shape. */
        const val OUTLINE = 0x66888888
    }
}
