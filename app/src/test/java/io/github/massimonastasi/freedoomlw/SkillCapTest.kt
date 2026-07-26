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
 * What happens at the end of the ladder: the marine clears the whole table on the hardest
 * skill and there is nothing above it.
 *
 * The answer has to be that the table simply replays on Nightmare, forever, with no wrap
 * back to the bottom and no stall. Restarting the ladder from the easiest level would
 * reward finishing it by making the game easier, and stalling would leave the scene empty.
 */
class SkillCapTest {

    @Test
    fun `the hardest skill is a ceiling, and the waves keep coming on it`() {
        GameData.clearRandom()
        val scene = Scene(720, 1600, startSkill = GameData.skills.size - 1)
        scene.invulnerable = true                    // only a death may reset the skill

        val top = GameData.skills.size - 1
        var wavesSeen = 0
        var previousWave = scene.wave
        var wrapsAtTop = 0

        for (t in 1..TICRATE * 3600) {
            scene.tick(t)
            assertEquals(top, scene.skill, "tic $t: the skill left Nightmare without a death")
            if (scene.wave != previousWave) {
                wavesSeen++
                // Wrapping to the first wave is the moment a promotion would have fired.
                if (scene.wave == 0) wrapsAtTop++
                previousWave = scene.wave
            }
        }

        println("Nightmare: $wavesSeen wave changes, $wrapsAtTop full passes of the table")
        assertTrue(wavesSeen > 0, "the waves stopped advancing on the hardest skill")
    }

    @Test
    fun `a death on the hardest skill drops all the way back to the first`() {
        GameData.clearRandom()
        val scene = Scene(720, 1600, startSkill = GameData.skills.size - 1)

        var t = 0
        while (t < TICRATE * 600 && scene.skill == GameData.skills.size - 1) scene.tick(++t)

        assertEquals(0, scene.skill, "a death must take the ladder back to the bottom")
        assertEquals(0, scene.wave, "and back to the first wave")
    }
}
