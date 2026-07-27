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
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButton

/**
 * An outlined button inside the list, aligned to the trailing edge.
 *
 * "Use your own WAD" is an action rather than a setting, and the design draws it as a button
 * rather than as another row - which is right: a row that looks like the options above it but
 * opens a file picker instead of choosing something would be lying about what it does.
 */
class ButtonPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    init {
        layoutResource = R.layout.preference_button
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(R.id.button) as MaterialButton).apply {
            text = title
            // The listener directly, not performClick(). Preference.performClick() with no
            // handler of its own falls through to the view's performClick(), which calls
            // this listener again - measured on the device as a stack overflow that took the
            // whole app down the first time the button was pressed.
            setOnClickListener { onPreferenceClickListener?.onPreferenceClick(this@ButtonPreference) }
        }
    }
}
