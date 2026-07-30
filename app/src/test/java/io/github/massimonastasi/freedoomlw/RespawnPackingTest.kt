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

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one thing about a respawn that no other test looks at: which creature comes back.
 *
 * On the skills that respawn, a fallen monster is remembered as a single Int - the tic it is
 * due at, shifted up, with its index in the low bits. The index field used to be three bits
 * wide while the bestiary held fourteen creatures, so the six that need a fourth bit wrote it
 * into the tic: a PainLord was remembered as a ChaingunZombie and came back a tic late, and
 * every existing test still passed, because they count arrivals rather than identify them.
 *
 * That is the shape of bug this file exists for. It does not exercise the scene: it asserts
 * the arithmetic directly, and it fails the day the bestiary outgrows the field rather than
 * the day someone notices the wrong monster on their home screen.
 */
class RespawnPackingTest {

    private fun pack(tic: Int, index: Int) = (tic shl Scene.RESPAWN_INDEX_BITS) or index
    private fun ticOf(packed: Int) = packed shr Scene.RESPAWN_INDEX_BITS
    private fun indexOf(packed: Int) = packed and Scene.RESPAWN_INDEX_MASK

    @Test
    fun `every creature in the bestiary survives the round trip`() {
        val tic = 1_234_567
        for (i in GameData.creatures.indices) {
            val packed = pack(tic, i)
            assertEquals(i, indexOf(packed), "creature $i came back as ${indexOf(packed)}")
            assertEquals(tic, ticOf(packed), "creature $i shifted the tic it was due at")
        }
    }

    @Test
    fun `the index field is wide enough for the bestiary, and says so before it is not`() {
        val capacity = Scene.RESPAWN_INDEX_MASK + 1
        assertTrue(
            GameData.creatures.size <= capacity,
            "the bestiary has ${GameData.creatures.size} entries and the packed field holds " +
                "$capacity: widen Scene.RESPAWN_INDEX_BITS, or respawns will hand back the " +
                "wrong creature at a slightly wrong time",
        )
    }

    /**
     * The horizon, stated rather than discovered. The tic advances only while the wallpaper is
     * visible, so this is screen-on time.
     */
    @Test
    fun `a packed entry stays valid for weeks of visible running`() {
        val maxTic = Int.MAX_VALUE shr Scene.RESPAWN_INDEX_BITS
        val days = maxTic / TICRATE / 3600.0 / 24.0
        println("respawn packing holds tics up to $maxTic: %.0f days of screen-on time".format(days))
        assertTrue(days > 30, "the packed tic field only reaches %.1f days".format(days))
    }
}
