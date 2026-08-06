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

import kotlin.test.assertTrue
import org.junit.Test

/**
 * The two things 0.16.0 changed in the run itself: how much of one pickup the floor will
 * carry, and what happens when a wave table is finished on a rung that is not the last.
 *
 * Both are invariants over a whole run rather than values, so both are checked by playing
 * one out and watching it, which is also the only way to reach them: nothing here places an
 * item or finishes a table by hand.
 */
class ClutterAndLapTest {

    @Test
    fun `never more than two of the same pickup on the floor`() {
        GameData.clearRandom()
        // A floor far wider than a phone's. On a real screen the marine is never more than a
        // few steps from a drop and picks it up long before a third could land on top of it,
        // so the cap is what stops the pile-up on a tablet, on a lifetime-long lull, or on any
        // run where he is busy at one end. This is that run, and the only way to reach the cap.
        val scene = Scene(20000, 1600)

        var seen = 0
        for (t in 1..TICRATE * 900) {
            scene.tick(t)
            val counts = HashMap<GameData.Item, Int>()
            for (a in scene.actors) {
                if (a.mode != Mode.ITEM) continue
                val item = a.item ?: continue
                val n = (counts[item] ?: 0) + 1
                counts[item] = n
                seen = maxOf(seen, n)
                assertTrue(n <= Scene.MAX_SAME_ON_FLOOR, "tic $t: $n of one pickup lying there")
            }
        }
        assertTrue(seen >= 2, "the floor never carried two of anything: the cap was never tested")
    }

    @Test
    fun `a table finished below the hardest rung laps without the curtain`() {
        GameData.clearRandom()
        val scene = Scene(720, 1600)
        // The marine has to survive the whole table for it to be finished at all, which a run
        // at the easy end of the ladder does not do on its own.
        scene.invulnerable = true

        var lapped = false
        var wave = scene.wave
        for (t in 1..TICRATE * 3600) {
            scene.tick(t)
            if (scene.winning) {
                assertTrue(
                    scene.skill == GameData.skills.lastIndex,
                    "tic $t: won on rung ${scene.skill}, which is not the last one",
                )
                break
            }
            if (scene.wave < wave) {
                lapped = true
                assertTrue(scene.coverFade == 0f, "tic $t: the curtain came down on a lap")
            }
            wave = scene.wave
        }
        assertTrue(lapped, "no table was finished in an hour: this test verified nothing")
    }
}
