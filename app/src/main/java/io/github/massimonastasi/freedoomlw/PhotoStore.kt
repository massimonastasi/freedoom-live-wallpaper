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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * The user's chosen photograph, used as a still backdrop behind the scene.
 *
 * Copied in like the WAD, and for the same reason: a document URI's permission does not
 * survive indefinitely, and the wallpaper process reads this on every launch.
 */
object PhotoStore {

    private const val NAME = "background.jpg"
    private const val TAG = "FreedoomLW"

    fun file(context: Context): File? =
        File(context.filesDir, NAME).takeIf { it.isFile && it.length() > 0 }

    fun clear(context: Context) {
        file(context)?.delete()
    }

    fun import(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            File(context.filesDir, NAME).outputStream().use { input.copyTo(it) }
        }
        // Decoded once here so an unreadable image is refused now rather than leaving the
        // wallpaper with a backdrop it cannot draw.
        load(context, 1080, 2400) != null
    } catch (e: Exception) {
        Log.w(TAG, "photo import failed", e)
        clear(context)
        false
    }

    /**
     * Decodes the photo scaled down to roughly the surface size.
     *
     * A modern phone photograph is far larger than any screen, and decoding it at full size
     * would hold tens of megabytes in the wallpaper process for pixels nobody can see.
     * inSampleSize halves until it fits, which is the cheap path the decoder is built for.
     */
    fun load(context: Context, width: Int, height: Int): Bitmap? {
        val f = file(context) ?: return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) {
                sample *= 2
            }
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            Log.w(TAG, "photo decode failed", e)
            null
        }
    }
}
