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

import java.io.File
import java.io.OutputStream

/**
 * Keeps only the lumps this wallpaper draws, and throws the rest away.
 *
 * An IWAD is mostly things a wallpaper never asks for: maps, wall textures, menu graphics,
 * music and sound. Freedoom's full file is 28 MB and the bundled subset is 580 KB, so the
 * part in use is around two percent. A user's own IWAD is no different, and there is no
 * reason for their storage to carry the other ninety-eight.
 *
 * The rule lives here rather than only in the Gradle task that trims the bundled asset,
 * because it now has to run on a file that arrives at runtime. WadFileTest checks the
 * bundled asset against this same rule, which is what keeps the two from drifting apart.
 */
object WadReducer {

    /**
     * Rotations still drawn. Diagonal movement was dropped, so half the sprite angles in a
     * WAD are dead weight: 0 serves every angle, and 1/3/5/7 are the four cardinals.
     */
    private val KEPT_ROTATIONS = charArrayOf('0', '1', '3', '5', '7')

    /**
     * Lumps wanted by exact name, as opposed to by sprite prefix.
     *
     * The floors are not named here. They are whichever flats FloorPicker chooses from this
     * particular WAD, so the reducer asks it rather than carrying a list that would be
     * Freedoom's names applied to somebody else's file.
     */
    fun exactNames(source: WadFile): Set<String> = buildSet {
        add("PLAYPAL")                                  // the palette, and every colour read from it
        add("FREEDOOM")                                 // Freedoom's own marker, when present
        add("F_START"); add("F_END")                    // flat markers: flatIndex searches between them
        addAll(FloorPicker.choose(source).map { it.name })
        for (d in 0..9) add("STTNUM$d")                 // readout numerals
    }

    fun needed(name: String, exact: Set<String>): Boolean {
        if (name in exact) return true
        val prefix = GameData.spritePrefixes.firstOrNull { name.startsWith(it) } ?: return false
        // prefix + frame + rotation, optionally a second pair for the mirrored angle.
        if (name.length != prefix.length + 2 && name.length != prefix.length + 4) return false
        return name.drop(prefix.length)
            .filterIndexed { i, _ -> i % 2 == 1 }
            .any { it in KEPT_ROTATIONS }
    }

    /**
     * Writes a WAD holding only what [needed] accepts.
     *
     * Returns how many lumps were kept, or -1 if nothing usable was found — which the caller
     * should treat as a rejection rather than write an empty file.
     */
    fun reduce(source: WadFile, target: File): Int {
        val exact = exactNames(source)
        val kept = (0 until source.lumpCount).filter { needed(source.nameAt(it), exact) }
        if (kept.isEmpty()) return -1

        // Sizes are known before anything is written, so the directory offset can be
        // computed rather than patched afterwards.
        val bodySize = kept.sumOf { source.sizeAt(it) }
        val dirOffset = 12 + bodySize

        target.outputStream().buffered().use { out ->
            out.write("IWAD".toByteArray(Charsets.US_ASCII))
            out.writeIntLE(kept.size)
            out.writeIntLE(dirOffset)

            val offsets = IntArray(kept.size)
            var at = 12
            kept.forEachIndexed { i, lump ->
                offsets[i] = at
                val bytes = source.rawLump(lump)
                out.write(bytes)
                at += bytes.size
            }

            kept.forEachIndexed { i, lump ->
                out.writeIntLE(offsets[i])
                out.writeIntLE(source.sizeAt(lump))
                val name = source.nameAt(lump).take(8)
                out.write(name.toByteArray(Charsets.US_ASCII))
                repeat(8 - name.length) { out.write(0) }
            }
        }
        return kept.size
    }

    private fun OutputStream.writeIntLE(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF)
        write((v shr 24) and 0xFF)
    }
}
