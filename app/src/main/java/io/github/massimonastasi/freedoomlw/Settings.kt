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
import android.content.SharedPreferences

/**
 * The user's choices, and the only place their names are written down.
 *
 * ponytail: SharedPreferences directly, with no settings framework on top. There are three
 * values; a preference library would be a runtime dependency and a second way to describe
 * each of them, in exchange for widgets this does not need.
 */
object Settings {

    private const val FILE = "settings"

    private const val KEY_FPS = "fps"
    private const val KEY_READOUT = "readout"
    private const val KEY_GOD = "god"

    /** Frame rates offered. The lower one is the real battery lever, and it is visible. */
    val FPS_CHOICES = intArrayOf(20, 15, 10)

    const val DEFAULT_FPS = 20

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun fps(p: SharedPreferences): Int =
        p.getInt(KEY_FPS, DEFAULT_FPS).takeIf { it in FPS_CHOICES } ?: DEFAULT_FPS

    fun setFps(p: SharedPreferences, value: Int) = p.edit().putInt(KEY_FPS, value).apply()

    fun readout(p: SharedPreferences): Boolean = p.getBoolean(KEY_READOUT, true)

    fun setReadout(p: SharedPreferences, value: Boolean) =
        p.edit().putBoolean(KEY_READOUT, value).apply()

    /**
     * The marine cannot die. Not a cheat so much as a mood: it turns the wallpaper into an
     * endless advance rather than a loop of deaths, and the skill ladder then climbs.
     */
    fun godMode(p: SharedPreferences): Boolean = p.getBoolean(KEY_GOD, false)

    fun setGodMode(p: SharedPreferences, value: Boolean) =
        p.edit().putBoolean(KEY_GOD, value).apply()
}
