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
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * A choice of one among a few, drawn as a Material 3 segmented button.
 *
 * The frame rate is three values that sit on one line and mean less-to-more, which is what a
 * segmented button is for; the same three as a radio list took three rows to say the same
 * thing. The options are ordered ascending for that reason - left to right has to mean
 * something or the shape is decoration.
 */
class SegmentedPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    private var entries: Array<CharSequence> = emptyArray()
    private var values: Array<CharSequence> = emptyArray()
    private var current: String? = null

    init {
        layoutResource = R.layout.preference_segmented
        isSelectable = false
        context.styled(attrs, R.styleable.OptionListPreference) {
            entries = it.getTextArray(R.styleable.OptionListPreference_android_entries) ?: emptyArray()
            values = it.getTextArray(R.styleable.OptionListPreference_android_entryValues) ?: emptyArray()
        }
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any? =
        a.getString(index)

    override fun onSetInitialValue(defaultValue: Any?) {
        current = getPersistedString(defaultValue as? String)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val group = holder.findViewById(R.id.segmented_group) as MaterialButtonToggleGroup
        group.clearOnButtonCheckedListeners()
        group.removeAllViews()

        val inflater = LayoutInflater.from(group.context)
        val chosen = values.indexOfFirst { it.toString() == current }.let { if (it >= 0) it else 0 }

        entries.forEachIndexed { i, label ->
            val button = inflater.inflate(R.layout.segment, group, false) as MaterialButton
            button.id = i
            button.text = label
            group.addView(button)
        }
        group.check(chosen)

        group.addOnButtonCheckedListener { _, id, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val value = values.getOrNull(id)?.toString() ?: return@addOnButtonCheckedListener
            if (value == current) return@addOnButtonCheckedListener
            if (!callChangeListener(value)) return@addOnButtonCheckedListener
            current = value
            persistString(value)
        }
    }
}
