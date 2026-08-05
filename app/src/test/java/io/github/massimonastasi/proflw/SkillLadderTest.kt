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
 * When the rung moves, and so when the background does: only the kill that empties the last
 * wave of the table moves it. Reported twice - the floor kept turning over mid-table, and a
 * death kept taking it back to the first one.
 */
class SkillLadderTest {

    @Test
    fun `the rung only moves on the last wave of the table`() {
        GameData.clearRandom()
        val scene = Scene(720, 1600)

        var previousWave = scene.wave
        var previousSkill = scene.skill
        for (t in 1..TICRATE * 900) {
            scene.tick(t)
            if (scene.skill != previousSkill) {
                // The counter has not moved yet: the wave just cleared is still the current one.
                assertEquals(
                    GameData.waves.lastIndex,
                    previousWave,
                    "tic $t: the rung moved on wave $previousWave, mid-table",
                )
            }
            previousWave = scene.wave
            previousSkill = scene.skill
        }
        assertTrue(scene.skill >= 0, "the rung went below the bottom")
    }
}
