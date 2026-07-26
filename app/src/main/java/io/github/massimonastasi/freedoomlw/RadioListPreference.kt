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
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * A list of radio buttons shown in place, rather than behind a dialog.
 *
 * ListPreference is the library's answer to a choice of one, and it always opens a dialog:
 * a row you tap to find out what the options were. On a screen with three such choices that
 * is three taps to see nine options, and the settings never show their own state at rest.
 * This draws the options where they are.
 *
 * Nothing here duplicates ListPreference — the value still goes through the preference's own
 * persistence, so the staged data store sees it like any other write.
 */
class RadioListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    private var entries: Array<CharSequence> = emptyArray()
    private var values: Array<CharSequence> = emptyArray()
    private var current: String? = null
    private var group: RadioGroup? = null

    /** Locked when there is only one thing to choose, so the row reads as fixed, not broken. */
    var choosable: Boolean = true
        set(value) { field = value; notifyChanged() }

    init {
        layoutResource = R.layout.preference_radio_list
        isSelectable = false
        context.obtainStyledAttributes(attrs, R.styleable.RadioListPreference).use {
            entries = it.getTextArray(R.styleable.RadioListPreference_android_entries) ?: emptyArray()
            values = it.getTextArray(R.styleable.RadioListPreference_android_entryValues) ?: emptyArray()
        }
    }

    private inline fun <T : android.content.res.TypedArray, R> T.use(block: (T) -> R): R =
        try { block(this) } finally { recycle() }

    /** Replaces the options at runtime: the sprite list grows when a WAD is imported. */
    fun setOptions(labels: Array<CharSequence>, ids: Array<CharSequence>) {
        entries = labels
        values = ids
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

        // A row with no title or no summary should not reserve the space for one. The base
        // class leaves both views in place and merely empty, which on a screen made mostly of
        // these leaves gaps that read as misalignment.
        holder.findViewById(android.R.id.title)?.visibility =
            if (title.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        holder.findViewById(android.R.id.summary)?.visibility =
            if (summary.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE

        val container = holder.findViewById(R.id.radio_group) as RadioGroup
        group = container
        container.setOnCheckedChangeListener(null)
        container.removeAllViews()

        entries.forEachIndexed { i, label ->
            container.addView(RadioButton(context).apply {
                id = i
                text = label
                isEnabled = choosable
                isChecked = values.getOrNull(i)?.toString() == current
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            })
        }
        // Nothing matched, so show the first: a stored value can name an option that no
        // longer exists, and a group with no selection looks like a bug.
        if (container.checkedRadioButtonId == -1 && entries.isNotEmpty()) {
            (container.getChildAt(0) as? RadioButton)?.isChecked = true
        }

        container.setOnCheckedChangeListener { _, id ->
            val chosen = values.getOrNull(id)?.toString() ?: return@setOnCheckedChangeListener
            if (chosen == current) return@setOnCheckedChangeListener
            if (!callChangeListener(chosen)) return@setOnCheckedChangeListener
            current = chosen
            persistString(chosen)
        }
    }
}
