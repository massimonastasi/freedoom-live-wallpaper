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

import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore

/**
 * Holds edits in memory until they are saved.
 *
 * The preference library writes straight through to SharedPreferences, which is the Android
 * convention and is not what this screen wants: it has a Save button, so a choice must not
 * reach the wallpaper until that button is pressed. PreferenceDataStore is the library's own
 * seam for exactly this — the screen reads and writes here instead, and the file underneath
 * is untouched until [commit].
 *
 * Reads fall through to the real preferences, so an unedited row still shows what is in
 * force rather than a default.
 */
class StagedStore(private val real: SharedPreferences) : PreferenceDataStore() {

    private val pending = HashMap<String, Any?>()

    /** True while there is something a Save would write and a discard would lose. */
    val dirty: Boolean get() = pending.isNotEmpty()

    fun commit() {
        if (pending.isEmpty()) return
        real.edit().apply {
            for ((k, v) in pending) {
                when (v) {
                    is String -> putString(k, v)
                    is Boolean -> putBoolean(k, v)
                    is Int -> putInt(k, v)
                    null -> remove(k)
                }
            }
        }.apply()
        pending.clear()
    }

    fun discard() = pending.clear()

    override fun putString(key: String, value: String?) { pending[key] = value }

    override fun getString(key: String, defValue: String?): String? =
        if (pending.containsKey(key)) pending[key] as String? else real.getString(key, defValue)

    override fun putBoolean(key: String, value: Boolean) { pending[key] = value }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        if (pending.containsKey(key)) pending[key] as Boolean else real.getBoolean(key, defValue)

    override fun putInt(key: String, value: Int) { pending[key] = value }

    override fun getInt(key: String, defValue: Int): Int =
        if (pending.containsKey(key)) pending[key] as Int else real.getInt(key, defValue)
}
