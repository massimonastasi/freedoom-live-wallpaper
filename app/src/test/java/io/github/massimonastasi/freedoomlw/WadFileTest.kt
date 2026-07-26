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

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Check on the WAD parser: if anyone breaks the column decoding or the sprite naming
 * convention, this fails.
 *
 * The .wad is not under version control (see .gitignore), so the test skips itself rather
 * than failing when the file is missing.
 */
class WadFileTest {

    private val wadFile = File("src/main/assets/freedoom1.wad")

    private fun openWad(): WadFile {
        assumeTrue("freedoom1.wad missing: test skipped", wadFile.exists())
        val ch = RandomAccessFile(wadFile, "r").channel
        return WadFile(ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size()))
    }

    @Test
    fun `the shipped WAD satisfies everything the code asks for`() {
        val wad = openWad()

        // The bundled WAD is reduced at build time by the reduceWad task, which keeps its
        // own list of lump names. This is the check that catches the list drifting away
        // from the code: it asks for exactly what the wallpaper asks for at runtime.
        assertTrue(wad.indexOf("PLAYPAL") >= 0, "PLAYPAL missing: no palette, no colours")
        assertTrue(wad.flatIndex("CEIL5_1") >= 0, "the floor flat is missing")
        for (d in 0..9) assertTrue(wad.indexOf("STTNUM$d") >= 0, "readout digit $d missing")

        for (prefix in GameData.spritePrefixes) {
            assertTrue(wad.lumpsStartingWith(prefix).isNotEmpty(), "no $prefix sprite at all")
        }

        // Stronger than presence: every frame each creature can be drawn in must resolve at
        // each of the four angles still in use. A missing rotation would show up on screen
        // as a creature vanishing at one heading.
        for (c in GameData.creatures + GameData.player) {
            val set = SpriteSet(wad, c.lumpPrefix)
            val frames = (0 until c.walkFrames).toList() +
                c.attack.frames.toList() + c.pain.frames.toList() + c.death.frames.toList()
            for (f in frames.distinct()) {
                for (rot in intArrayOf(1, 3, 5, 7)) {
                    assertTrue(
                        set.resolve(f, rot) >= 0,
                        "${c.name}: frame $f has no sprite at rotation $rot",
                    )
                }
            }
        }
    }

    @Test
    fun `decodes a patch with sensible size and pixels`() {
        val wad = openWad()
        val i = wad.indexOf("TROOA1")
        assertTrue(i >= 0, "TROOA1 missing")
        val p = wad.decodePatch(i)
        assertTrue(p.width in 8..200, "suspicious width: ${p.width}")
        assertTrue(p.height in 8..200, "suspicious height: ${p.height}")
        assertEquals(p.width * p.height, p.pixels.size)
        // A monster is not entirely transparent: if it were, the posts were not read.
        val opaque = p.pixels.count { it ushr 24 != 0 }
        assertTrue(opaque > p.pixels.size / 10, "almost fully transparent: $opaque opaque pixels")
        // ...nor entirely opaque: the column format leaves the corners transparent.
        assertTrue(opaque < p.pixels.size, "no transparent pixel: columns read incorrectly")
    }

    @Test
    fun `decodes a floor flat as a fully opaque 64x64 tile`() {
        val wad = openWad()
        val i = wad.flatIndex("CEIL5_1")
        assertTrue(i >= 0, "CEIL5_1 missing")
        val f = wad.decodeFlat(i)
        assertEquals(64, f.width)
        assertEquals(64, f.height)
        // Flats are raw palette indices with no transparency: every pixel must be opaque,
        // otherwise the backdrop would show holes when tiled.
        assertTrue(f.pixels.all { it ushr 24 == 0xFF }, "a flat must be fully opaque")
        // It has to stay dark and neutral, or it competes with the launcher icons.
        val avg = f.pixels.map { (it shr 16 and 0xFF) * 0.299 + (it shr 8 and 0xFF) * 0.587 + (it and 0xFF) * 0.114 }.average()
        assertTrue(avg < 60, "backdrop too bright: $avg")
        val chroma = f.pixels.map {
            val r = it shr 16 and 0xFF; val g = it shr 8 and 0xFF; val b = it and 0xFF
            maxOf(r, g, b) - minOf(r, g, b)
        }.average()
        assertTrue(chroma < 20, "backdrop too saturated: $chroma")
    }

    @Test
    fun `mirrored rotations share a single lump`() {
        val wad = openWad()
        val set = SpriteSet(wad, "TROO")
        assertTrue(set.frameCount >= 4, "not enough walk frames: ${set.frameCount}")

        // TROOA3A7: the same lump for angles 3 and 7, the second one mirrored. The pair used
        // to be 2 and 8, but those are diagonal views and no longer ship.
        val a3 = set.resolve(0, 3)
        val a7 = set.resolve(0, 7)
        assertTrue(a3 >= 0 && a7 >= 0, "frame A rotations 3/7 missing")
        assertEquals(a3 shr 1, a7 shr 1, "the two rotations should share the lump")
        assertEquals(0, a3 and 1, "rotation 3 must not be mirrored")
        assertEquals(1, a7 and 1, "rotation 7 must be mirrored")
        assertEquals("TROOA3A7", wad.nameAt(a3 shr 1))
    }
}
