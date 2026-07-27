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
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * An on/off row, drawn as the same Material 3 list row as everything else on this screen.
 *
 * SwitchPreferenceCompat exists and does the persistence, but it draws the library's own row
 * - a different height, a different switch, no filled background - so on a screen made of
 * these rows it would be the one that looks wrong. The persistence is four lines; the row is
 * the part worth sharing.
 */
class SwitchRowPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    private var checked = false

    init {
        layoutResource = R.layout.preference_row_host
        isSelectable = false
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any =
        a.getBoolean(index, false)

    override fun onSetInitialValue(defaultValue: Any?) {
        checked = getPersistedBoolean(defaultValue as? Boolean ?: false)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val host = holder.findViewById(R.id.row_host) as FrameLayout
        host.removeAllViews()
        val row = LayoutInflater.from(host.context).inflate(R.layout.list_row, host, false)

        row.findViewById<View>(R.id.row_radio).visibility = View.GONE
        row.findViewById<TextView>(R.id.row_label).text = title
        row.findViewById<TextView>(R.id.row_caption).apply {
            text = summary
            visibility = if (summary.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        val switch = row.findViewById<MaterialSwitch>(R.id.row_switch)
        switch.visibility = View.VISIBLE
        switch.isChecked = checked
        row.isActivated = checked

        row.setOnClickListener {
            val next = !checked
            if (!callChangeListener(next)) return@setOnClickListener
            checked = next
            persistBoolean(next)
            switch.isChecked = next
            row.isActivated = next
        }
        host.addView(row, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
