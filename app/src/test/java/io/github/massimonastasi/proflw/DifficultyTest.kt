/*
 * Prof Live Wallpaper
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
package io.github.massimonastasi.proflw

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * What separates one rung of the ladder from the next, and what the screen does at the two
 * ends of a run. Both are new in 0.10.0 and neither is visible to the other tests: the drop
 * interval is the only combat lever the skill still moves, and the curtain is the only thing
 * left that covers the whole screen.
 */
class DifficultyTest {

    @Test
    fun `the ladder gets harder and the two tables cover the drops`() {
        val top = GameData.skills.size - 1
        for (s in 1..top) {
            assertTrue(
                GameData.dropInterval(s) > GameData.dropInterval(s - 1),
                "rung $s drops no slower than rung ${s - 1}: the ladder is flat there",
            )
        }
        assertTrue(GameData.supplyTable.isNotEmpty(), "nothing to heal with")
        assertTrue(GameData.weaponTable.isNotEmpty(), "nothing to shoot with")
        assertEquals(
            GameData.dropTable.size,
            GameData.supplyTable.size + GameData.weaponTable.size,
            "the two halves do not add up to the whole table",
        )
    }

    @Test
    fun `drops alternate between supplies and guns`() {
        GameData.clearRandom()
        val scene = Scene(720, 1600)

        // Nothing in this run places an item by hand, so every one came from the timer and
        // the categories have to alternate - within a run. A death starts the sequence over
        // from a supply, which is the point of it: the marine who just arrived is given
        // something to survive with before he is given something to shoot with.
        val seen = HashSet<Actor>()
        var previous: Boolean? = null
        var pairs = 0
        for (t in 1..TICRATE * 600) {
            scene.tick(t)
            if (scene.dying) previous = null
            for (a in scene.actors) {
                if (a.mode != Mode.ITEM || !seen.add(a)) continue
                val gun = a.item?.kind == GameData.ITEM_WEAPON
                if (previous != null) {
                    assertTrue(gun != previous, "tic $t: this drop repeats the category before it")
                    pairs++
                }
                previous = gun
            }
        }

        assertTrue(pairs >= 6, "only $pairs consecutive drops in ten minutes: too little to check")
    }

    @Test
    fun `the curtain closes on a death and opens on the fight that follows`() {
        GameData.clearRandom()
        val scene = Scene(720, 1600)

        var closed = false
        var reopened = false
        var breathed = false
        for (t in 1..TICRATE * 600) {
            scene.tick(t)
            val cover = scene.coverFade
            assertTrue(cover in 0f..1f, "tic $t: the curtain is at $cover")
            if (!closed) {
                if (scene.dying) {
                    // The three breaths are whole ones: the first opens on a dark border.
                    if (!breathed) {
                        assertTrue(scene.glowPulse < 0.05f, "tic $t: the glow started lit, at ${scene.glowPulse}")
                        breathed = true
                    }
                    if (cover >= 1f) closed = true
                }
            } else if (!scene.dying && cover == 0f) {
                reopened = true
                break
            }
        }
        assertTrue(closed, "the marine never died: this test verified nothing")
        assertTrue(reopened, "the curtain closed and never lifted")
    }
}
