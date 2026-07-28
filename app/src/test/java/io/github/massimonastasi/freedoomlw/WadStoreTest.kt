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

/**
 * The tripwire on [WadStore.REDUCTION_FORMAT].
 *
 * An imported WAD is not the user's file, it is what the reducer made of it, and what the
 * reducer keeps is decided by rules that live in this app. When those rules change, every
 * copy already on a device becomes something the app would no longer build - and it goes on
 * working, quietly, from a set of lumps that no longer matches. Measured on a real device
 * before this existed: a your.wad reduced by the old rules held nine flats, and two of the
 * nine the new rules ask for were not among them.
 *
 * The format number is what lets the app notice, and a number somebody has to remember to
 * raise is a promise, not a mechanism. This is the mechanism: it pins what the rules produce
 * today, so changing them fails here, in a message that says what to do about it, instead of
 * on somebody's home screen weeks later.
 *
 * When this test fails because the rules were meant to change: raise REDUCTION_FORMAT and
 * update the list below in the same commit.
 */
class WadStoreTest {

    private val wadFile = File("src/main/assets/freedoom2.wad")

    @Test
    fun `the reduction rules have not moved without the format number moving`() {
        assumeTrue("freedoom2.wad missing: test skipped", wadFile.exists())
        val ch = RandomAccessFile(wadFile, "r").channel
        val wad = WadFile(ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size()))

        // Format 2: band 20..45, no channel over 90, relative contrast at most 0.60, then
        // colour family before saturation with two grey, two brown, two green and three red.
        //
        // Taken from the implementation, not from the model used to preview it: the first
        // run of this test disagreed with that preview on two rungs. Both are ties or near
        // ties inside a family - the greys both measure chroma 0, and the middle red sits
        // between neighbours a few points apart - which a sort resolves by whatever order it
        // was handed. The app's answer is the one that ships, so the app's answer is the one
        // pinned here.
        val expected = listOf(
            "CEIL5_1", "RROCK03", "AQF049", "SLIME05", "GRNROCK",
            "NUKAGE1", "RROCK08", "RROCK06", "FLOOR6_1",
        )
        assertEquals(
            expected,
            FloorPicker.choose(wad).map { it.name },
            "the floors chosen from the bundled WAD have changed. If that was intended, " +
                "raise WadStore.REDUCTION_FORMAT (currently ${WadStore.REDUCTION_FORMAT}) so " +
                "copies already on devices are discarded, and update this list",
        )
    }
}
