/*
 * Prof Live Wallpaper
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
package io.github.massimonastasi.proflw

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup

/**
 * The flat colours, as circles in two rows of five.
 *
 * A view rather than a preference of its own: the design puts these *inside* the "Flat
 * colour" row, not underneath it as a separate block, and a preference is always its own row.
 * The value is still persisted through the preference store, by whoever hosts this.
 *
 * Five columns rather than a scrolling strip, so every colour is on screen at once and none
 * can hide past the edge.
 *
 * It measures its own children rather than sizing them beforehand. The earlier version built
 * fixed-size swatches and rebuilt them from a layout listener when the row turned out to be
 * narrower - which is a rebuild in the middle of a layout pass, and on some devices left ten
 * views that were never laid out: the row kept its height and showed nothing at all inside it.
 * A square is a share of the width, and the width is what onMeasure is handed.
 */
class SwatchGrid(
    context: Context,
    private val labels: Array<CharSequence>,
    private val values: Array<CharSequence>,
) : ViewGroup(context) {

    /** Resolves a palette index to a colour. Supplied by the screen, which has the WAD. */
    var colourOf: (Int) -> Int = { 0 }

    /** Called with the chosen palette index, as a string, so it persists like any other. */
    var onChosen: (String) -> Unit = {}

    private var current: String? = null

    private val gap = (8 * resources.displayMetrics.density).toInt()

    /**
     * The swatch edge for a given width: a fifth of what is left once the gaps are taken,
     * never bigger than the design's own swatch. On a wide row the spare width stays spare.
     */
    private fun cell(width: Int) =
        ((width - gap * (COLUMNS - 1)) / COLUMNS)
            .coerceIn(1, (MAX_SWATCH_DP * resources.displayMetrics.density).toInt())

    fun show(chosen: String?) {
        current = chosen
        removeAllViews()
        val dp = resources.displayMetrics.density

        labels.forEachIndexed { i, label ->
            val value = values.getOrNull(i)?.toString() ?: return@forEachIndexed
            val palette = value.toIntOrNull() ?: 0
            val selected = value == current
            addView(View(context).apply {
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
            })
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val width = MeasureSpec.getSize(widthSpec)
        val size = cell(width)
        val spec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(spec, spec)

        val rows = (childCount + COLUMNS - 1) / COLUMNS
        setMeasuredDimension(width, (rows * size + (rows - 1).coerceAtLeast(0) * gap))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val size = cell(r - l)
        for (i in 0 until childCount) {
            val x = (i % COLUMNS) * (size + gap)
            val y = (i / COLUMNS) * (size + gap)
            getChildAt(i).layout(x, y, x + size, y + size)
        }
    }

    /** The theme's own primary, so the chosen ring follows the system palette. */
    private fun primaryColour(): Int =
        com.google.android.material.color.MaterialColors.getColor(
            this, androidx.appcompat.R.attr.colorPrimary,
        )

    private companion object {
        const val COLUMNS = 5

        /** The size the design draws them at; anything wider is a row, not a swatch. */
        const val MAX_SWATCH_DP = 40

        /** Faint, so black and the surface behind it do not merge into one shape. */
        const val OUTLINE = 0x66888888
    }
}
