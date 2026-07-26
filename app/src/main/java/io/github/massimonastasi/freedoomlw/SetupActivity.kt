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

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * The launcher entry: opens the wallpaper picker on this wallpaper, and gets out of the way.
 *
 * Without it the application has no icon at all and the only route in is the system
 * wallpaper settings, which is a poor way to be found. There is deliberately no screen of
 * its own: an activity that explains how to press the button it could have pressed itself is
 * worse than the button.
 *
 * ponytail: no layout, no theme of its own beyond being transparent, no state. It starts an
 * intent and finishes inside onCreate, so nothing is ever drawn.
 */
class SetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targeted = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, FreedoomWallpaperService::class.java),
            )

        try {
            startActivity(targeted)
        } catch (e: ActivityNotFoundException) {
            // Not every launcher ships the targeted picker. The generic chooser is part of
            // the framework, so it is a fallback that cannot itself be missing — the user
            // just has to find this wallpaper in the list.
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(this, R.string.no_wallpaper_picker, Toast.LENGTH_LONG).show()
            }
        }

        finish()
    }
}
