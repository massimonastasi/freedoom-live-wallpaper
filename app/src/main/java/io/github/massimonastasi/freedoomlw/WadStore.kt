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
import android.net.Uri
import android.util.Log
import java.io.File
import java.nio.channels.FileChannel

/**
 * The user's own IWAD: imported, checked, and used in place of the bundled assets.
 *
 * **Nothing here is ever redistributed.** The file is read from the device the user chose it
 * on, copied into this application's private storage so it can be memory-mapped, and never
 * leaves. That is the whole legal position: a commercial IWAD may not be shipped, but there
 * is nothing wrong with reading one the user already owns.
 *
 * Copied rather than read through the content resolver because the loader maps the file and
 * seeks around it constantly — random access through a ContentResolver stream is slow, and a
 * document URI's permission can be revoked between one launch and the next.
 */
object WadStore {

    private const val DIR = "wads"
    private const val TAG = "FreedoomLW"

    /** The one imported WAD, or null when the bundled assets are in use. */
    fun active(context: Context): File? =
        File(File(context.filesDir, DIR), "active.wad").takeIf { it.isFile && it.length() > 0 }

    fun clear(context: Context) {
        active(context)?.delete()
    }

    /**
     * Copies [uri] in, checks it, and keeps it only if it can actually drive the wallpaper.
     *
     * Returns null on success, or a reason to show the user. A rejected file is deleted
     * rather than left behind: a WAD is tens of megabytes, and one that cannot be used is
     * just occupied storage.
     */
    fun import(context: Context, uri: Uri): String? {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val staged = File(dir, "staged.wad")

        try {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return context.getString(R.string.wad_unreadable)
                staged.outputStream().use { input.copyTo(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "WAD copy failed", e)
            staged.delete()
            return context.getString(R.string.wad_unreadable)
        }

        val problem = check(context, staged)
        if (problem != null) {
            staged.delete()
            return problem
        }

        val target = File(dir, "active.wad")
        target.delete()
        if (!staged.renameTo(target)) {
            staged.delete()
            return context.getString(R.string.wad_unreadable)
        }
        Log.i(TAG, "WAD imported: ${target.length() / 1024} KB")
        return null
    }

    /**
     * Opens the file the way the wallpaper will and asks it for what the wallpaper needs.
     *
     * Checking the header alone would accept a WAD full of Heretic or Hexen sprites, whose
     * names are entirely different, and the wallpaper would come up empty. So the test is
     * the real one: the palette must be there, and the creatures must resolve.
     */
    private fun check(context: Context, file: File): String? = try {
        val wad = file.inputStream().use { s ->
            WadFile(s.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length()))
        }
        val present = GameData.spritePrefixes.count { wad.lumpsStartingWith(it).isNotEmpty() }
        when {
            present == 0 -> context.getString(R.string.wad_no_sprites)
            present < GameData.spritePrefixes.size / 2 -> context.getString(R.string.wad_too_few, present)
            else -> null
        }
    } catch (e: WadFile.NotAWadException) {
        context.getString(R.string.wad_not_a_wad, e.message ?: "")
    } catch (e: Exception) {
        Log.w(TAG, "WAD check failed", e)
        context.getString(R.string.wad_unreadable)
    }
}
