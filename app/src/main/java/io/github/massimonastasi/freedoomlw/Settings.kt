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

    /** Shared with the preference screen, which is told to use this file rather than the default. */
    const val FILE = "settings"

    const val KEY_FPS = "fps"
    const val KEY_READOUT = "readout"
    const val KEY_GOD = "god"
    const val KEY_BACKGROUND = "background"
    const val KEY_BACKGROUND_PHOTO = "background_photo"
    const val KEY_BACKGROUND_COLOUR = "background_colour"
    const val KEY_WAD = "wad"
    const val KEY_WAD_RESET = "wad_reset"
    const val KEY_LICENCES = "licences"
    const val KEY_SET_WALLPAPER = "set_wallpaper"

    const val DEFAULT_FPS = 20

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Stored as a string because that is what a ListPreference writes, and one format is
     * better than two with a conversion between them.
     */
    fun fps(p: SharedPreferences): Int =
        p.getString(KEY_FPS, null)?.toIntOrNull()?.takeIf { it in 5..60 } ?: DEFAULT_FPS

    fun readout(p: SharedPreferences): Boolean = p.getBoolean(KEY_READOUT, true)

    /**
     * The marine cannot die. Not a cheat so much as a mood: it turns the wallpaper into an
     * endless advance rather than a loop of deaths, and the skill ladder then climbs.
     */
    fun godMode(p: SharedPreferences): Boolean = p.getBoolean(KEY_GOD, false)

    /** What is drawn behind the fight. */
    enum class Background { DYNAMIC, PHOTO, COLOUR }

    /**
     * The dynamic ground is the default and stays so: it is the only one that reports the
     * difficulty, and it is the thing the wallpaper was built around.
     */
    fun background(p: SharedPreferences): Background = when (p.getString(KEY_BACKGROUND, null)) {
        "photo" -> Background.PHOTO
        "colour" -> Background.COLOUR
        else -> Background.DYNAMIC
    }

    /**
     * Palette index for the flat colour, from the active WAD's own PLAYPAL rather than an
     * ARGB value, so the choice follows whichever WAD is loaded.
     */
    fun backgroundColour(p: SharedPreferences): Int =
        p.getString(KEY_BACKGROUND_COLOUR, null)?.toIntOrNull()?.coerceIn(0, 255) ?: 0
}
