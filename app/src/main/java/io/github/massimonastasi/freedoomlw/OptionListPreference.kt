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
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.radiobutton.MaterialRadioButton

/**
 * Reads styled attributes and always recycles them.
 *
 * The custom preferences here each wrote this out differently before - one extension, two
 * hand-rolled try/finally blocks - which was three chances to forget the recycle.
 * androidx.core ships the same thing, but only as a reason to add a dependency for four
 * lines.
 */
internal inline fun <R> Context.styled(
    attrs: AttributeSet?,
    styleable: IntArray,
    block: (TypedArray) -> R,
): R {
    val a = obtainStyledAttributes(attrs, styleable)
    return try { block(a) } finally { a.recycle() }
}

/**
 * A choice of one, drawn as Material 3 list rows rather than behind a dialog.
 *
 * ListPreference is the library's answer to this and it always opens a dialog: a row you tap
 * to find out what the options were. On a screen with three such choices that is three taps
 * to see nine options, and the settings never show their own state at rest.
 *
 * Each row carries a label, an optional supporting line, and an optional trailing button -
 * the photo row opens a picker, an imported WAD can be deleted. The trailing button is a
 * separate target from the row itself, which matters: tapping "Image" chooses it, tapping
 * the chevron on it goes and picks the file.
 *
 * Nothing here duplicates ListPreference: the value still goes through the preference's own
 * persistence, so it is written like any other.
 */
class OptionListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    private var entries: Array<CharSequence> = emptyArray()
    private var captions: Array<CharSequence> = emptyArray()
    private var values: Array<CharSequence> = emptyArray()
    private var current: String? = null

    /** Locked when there is only one thing to choose, so the row reads as fixed, not broken. */
    var choosable: Boolean = true
        set(value) { field = value; notifyChanged() }

    /**
     * Icon for a row's trailing button, or 0 for none. Asked per row rather than set once,
     * because the rows differ: one opens a picker, another deletes a file.
     */
    var trailingIcon: (Int) -> Int = { 0 }

    /** What that button does. */
    var onTrailing: (Int) -> Unit = {}

    /** Called when a row is chosen, after the value is persisted. */
    var onChosen: (String) -> Unit = {}

    init {
        layoutResource = R.layout.preference_option_list
        isSelectable = false
        context.styled(attrs, R.styleable.OptionListPreference) {
            entries = it.getTextArray(R.styleable.OptionListPreference_android_entries) ?: emptyArray()
            values = it.getTextArray(R.styleable.OptionListPreference_android_entryValues) ?: emptyArray()
            captions = it.getTextArray(R.styleable.OptionListPreference_captions) ?: emptyArray()
        }
    }

    /** Replaces the options at runtime: the sprite list grows when a WAD is imported. */
    fun setOptions(
        labels: Array<CharSequence>,
        ids: Array<CharSequence>,
        supporting: Array<CharSequence> = emptyArray(),
    ) {
        entries = labels
        values = ids
        captions = supporting
        notifyChanged()
    }

    fun value(): String = current ?: values.firstOrNull()?.toString().orEmpty()

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any? =
        a.getString(index)

    override fun onSetInitialValue(defaultValue: Any?) {
        current = getPersistedString(defaultValue as? String)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val rows = holder.findViewById(R.id.option_rows) as LinearLayout
        rows.removeAllViews()
        val inflater = LayoutInflater.from(rows.context)

        // Nothing matched, so fall back to the first: a stored value can name an option that
        // no longer exists - a WAD that has since been deleted - and a group with no
        // selection looks like a bug rather than like a stale setting.
        val chosen = values.indexOfFirst { it.toString() == current }.let {
            if (it >= 0) it else 0
        }

        entries.forEachIndexed { i, label ->
            val row = inflater.inflate(R.layout.list_row, rows, false)
            (row.findViewById<TextView>(R.id.row_label)).text = label
            row.findViewById<TextView>(R.id.row_caption).apply {
                val caption = captions.getOrNull(i)
                text = caption
                visibility = if (caption.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            row.findViewById<MaterialRadioButton>(R.id.row_radio).isChecked = i == chosen
            row.isActivated = i == chosen
            row.isEnabled = choosable

            val icon = trailingIcon(i)
            row.findViewById<ImageButton>(R.id.row_action).apply {
                if (icon == 0) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    setImageResource(icon)
                    setOnClickListener { onTrailing(i) }
                }
            }

            if (choosable) row.setOnClickListener { choose(i) }
            // A gap between rows, not a margin on the row: the last one must not push the
            // group taller than it is.
            if (i > 0) {
                (row.layoutParams as ViewGroup.MarginLayoutParams).topMargin =
                    (2 * rows.resources.displayMetrics.density).toInt()
            }
            rows.addView(row)
        }
    }

    private fun choose(index: Int) {
        val chosen = values.getOrNull(index)?.toString() ?: return
        if (chosen == current) return
        if (!callChangeListener(chosen)) return
        current = chosen
        persistString(chosen)
        notifyChanged()
        onChosen(chosen)
    }
}
