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
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
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
        val a = context.obtainStyledAttributes(attrs, R.styleable.RadioListPreference)
        try {
            labels = a.getTextArray(R.styleable.RadioListPreference_android_entries) ?: emptyArray()
            indices = a.getTextArray(R.styleable.RadioListPreference_android_entryValues) ?: emptyArray()
        } finally {
            a.recycle()
        }
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any? =
        a.getString(index)

    override fun onSetInitialValue(defaultValue: Any?) {
        current = getPersistedString(defaultValue as? String)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val group = holder.findViewById(R.id.radio_group) as RadioGroup
        group.setOnCheckedChangeListener(null)
        group.removeAllViews()

        val dp = context.resources.displayMetrics.density
        labels.forEachIndexed { i, label ->
            val palette = indices.getOrNull(i)?.toString()?.toIntOrNull() ?: 0
            group.addView(RadioButton(context).apply {
                id = i
                text = label
                isChecked = indices.getOrNull(i)?.toString() == current
                // The sample sits after the label, drawn at the real colour with a faint
                // outline so black and the background do not merge into one another.
                val box = GradientDrawable().apply {
                    setColor(colourOf(palette))
                    setStroke((1 * dp).toInt(), 0x66888888)
                    setSize((28 * dp).toInt(), (18 * dp).toInt())
                }
                setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, box, null)
                compoundDrawablePadding = (12 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            })
        }
        if (group.checkedRadioButtonId == -1 && labels.isNotEmpty()) {
            (group.getChildAt(0) as? RadioButton)?.isChecked = true
        }

        group.setOnCheckedChangeListener { _, id ->
            val chosen = indices.getOrNull(id)?.toString() ?: return@setOnCheckedChangeListener
            if (chosen == current) return@setOnCheckedChangeListener
            if (!callChangeListener(chosen)) return@setOnCheckedChangeListener
            current = chosen
            persistString(chosen)
        }
    }
}
