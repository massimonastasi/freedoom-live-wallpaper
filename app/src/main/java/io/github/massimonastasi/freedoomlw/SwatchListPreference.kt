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
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.GridLayout
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * The flat-colour choice, each option shown as the colour itself beside its name.
 *
 * The colours are not fixed values: they are palette indices resolved against whichever WAD
 * is loaded, so the sample is exactly what the wallpaper will draw. Naming a colour without
 * showing it would be a guess on the user's part, since "red" in one IWAD's palette is not
 * the same red as in another's.
 */
class SwatchListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    private var labels: Array<CharSequence> = emptyArray()
    private var indices: Array<CharSequence> = emptyArray()
    private var current: String? = null

    /** Resolves a palette index to a colour. Supplied by the screen, which has the WAD. */
    var colourOf: (Int) -> Int = { Color.BLACK }
        set(value) { field = value; notifyChanged() }

    init {
        layoutResource = R.layout.preference_swatches
        isSelectable = false
        context.styled(attrs, R.styleable.SwatchListPreference) { a ->
            labels = a.getTextArray(R.styleable.SwatchListPreference_android_entries) ?: emptyArray()
            indices = a.getTextArray(R.styleable.SwatchListPreference_android_entryValues) ?: emptyArray()
        }
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any? =
        a.getString(index)

    override fun onSetInitialValue(defaultValue: Any?) {
        current = getPersistedString(defaultValue as? String)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val grid = holder.findViewById(R.id.swatch_grid) as GridLayout
        grid.removeAllViews()

        val dp = context.resources.displayMetrics.density
        val size = (44 * dp).toInt()
        val gap = (8 * dp).toInt()

        labels.forEachIndexed { i, label ->
            val palette = indices.getOrNull(i)?.toString()?.toIntOrNull() ?: 0
            val chosen = indices.getOrNull(i)?.toString() == current
            val swatch = View(context).apply {
                // The colour itself, with a ring that thickens when chosen. A tick drawn
                // over the sample would be invisible on half of these - white on cream,
                // black on black - and the ring never collides with what it marks.
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colourOf(palette))
                    setStroke(
                        ((if (chosen) 3 else 1) * dp).toInt(),
                        if (chosen) primaryColour() else 0x66888888,
                    )
                }
                contentDescription = label
                setOnClickListener { choose(i) }
            }
            grid.addView(swatch, GridLayout.LayoutParams().apply {
                width = size
                height = size
                setMargins(0, 0, gap, gap)
            })
        }
    }

    /** The theme's own primary, so the chosen ring follows the system palette. */
    private fun primaryColour(): Int {
        val out = android.util.TypedValue()
        context.theme.resolveAttribute(
            com.google.android.material.R.attr.colorPrimary, out, true,
        )
        return out.data
    }

    private fun choose(index: Int) {
        val chosen = indices.getOrNull(index)?.toString() ?: return
        if (chosen == current) return
        if (!callChangeListener(chosen)) return
        current = chosen
        persistString(chosen)
        notifyChanged()
    }
}
