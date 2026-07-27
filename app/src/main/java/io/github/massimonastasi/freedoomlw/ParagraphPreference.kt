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
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * A paragraph of explanation at the top of a section.
 *
 * This text was briefly folded into an accordion and is back in the open, which is the
 * design's call: a settings screen that hides what its settings mean is shorter and less
 * useful. It is a row only because the screen is a list; nothing about it is interactive.
 */
class ParagraphPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    var body: CharSequence = ""
        set(value) { field = value; notifyChanged() }

    init {
        layoutResource = R.layout.preference_paragraph
        isSelectable = false
        body = context.styled(attrs, R.styleable.ParagraphPreference) {
            it.getText(R.styleable.ParagraphPreference_body) ?: ""
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(R.id.paragraph) as TextView).text = body
    }
}
