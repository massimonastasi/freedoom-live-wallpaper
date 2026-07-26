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
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * A row whose explanation is folded away until it is asked for.
 *
 * Every section here used to end in a paragraph of prose that was always on screen. Read once
 * and then scrolled past forever, it pushed the controls down and made a screen of five
 * choices feel like a document. The text is worth keeping — it is the only place that says
 * what the frame rate really changes, or that an imported WAD never leaves the device — so it
 * folds instead of going away.
 *
 * The expanded state is view state, not a setting: it lives here and resets when the screen
 * is rebuilt. Persisting it would put a preference in the store that is about the store's own
 * appearance.
 */
class AccordionPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    /** The folded text. Set from XML for the fixed ones, in code for the licences. */
    var body: CharSequence = ""
        set(value) { field = value; notifyChanged() }

    private var expanded = false

    init {
        layoutResource = R.layout.preference_accordion
        // The whole row handles its own tap, so the preference framework must not also treat
        // it as clickable: two ripples on one row, and the outer one wins the touch.
        isSelectable = false
        body = attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.AccordionPreference)
            try { a.getText(R.styleable.AccordionPreference_body) ?: "" } finally { a.recycle() }
        } ?: ""
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val header = holder.findViewById(R.id.accordion_header)!!
        val chevron = holder.findViewById(R.id.accordion_chevron) as ImageView
        val text = holder.findViewById(R.id.accordion_body) as TextView

        (holder.findViewById(R.id.accordion_title) as TextView).text = title
        text.text = body

        fun apply() {
            text.visibility = if (expanded) View.VISIBLE else View.GONE
            chevron.rotation = if (expanded) 180f else 0f
            // Announced rather than only drawn: a chevron says nothing to a screen reader.
            header.contentDescription = context.getString(
                if (expanded) R.string.accordion_collapse else R.string.accordion_expand,
                title,
            )
        }
        apply()

        header.setOnClickListener {
            expanded = !expanded
            apply()
        }
    }
}
