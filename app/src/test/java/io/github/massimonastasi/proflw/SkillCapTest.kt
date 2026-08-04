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

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What happens at the end of the ladder: the marine clears the whole table on the hardest
 * skill and there is nothing above it.
 *
 * There are only two ways off Nightmare and both end the run. Dying drops to the bottom, as
 * it does from anywhere. Finishing the table there is the other, and it is the only thing in
 * this scene that can be called winning: it is counted, it is washed green, and it starts
 * again from the first level. Stalling on Nightmare forever was the earlier answer and it
 * left the ladder with no top - a run that could never end and never be tallied.
 */
class SkillCapTest {

    @Test
    fun `finishing the table on the hardest skill is a win, and the run starts over`() {
        GameData.clearRandom()
        val scene = Scene(720, 1600, startSkill = GameData.skills.size - 1)
        scene.invulnerable = true                    // so the only way off the top is winning

        val top = GameData.skills.size - 1
        var wavesSeen = 0
        var previousWave = scene.wave
        var lowestSkill = top
        // The green wash, which fires on every finish. It is the observable for winning now
        // that the counter is not: god mode is what makes this test able to reach the end at
        // all, and god mode is exactly what stops the end being tallied.
        var won = false

        for (t in 1..TICRATE * 3600) {
            scene.tick(t)
            if (scene.winFade > 0f) won = true
            if (scene.skill < lowestSkill) lowestSkill = scene.skill
            // Invulnerable, so the skill may only ever be at the top or back at the bottom
            // having just won; anything in between would be a promotion that cannot happen.
            assertTrue(
                scene.skill == top || won,
                "tic $t: the skill left Nightmare without winning",
            )
            if (scene.wave != previousWave) {
                wavesSeen++
                previousWave = scene.wave
            }
        }

        println("Nightmare: $wavesSeen wave changes, won=$won, ${scene.completions} completions")
        assertTrue(wavesSeen > 0, "the waves stopped advancing on the hardest skill")
        assertTrue(won, "an invulnerable marine never finished the table in an hour")
        assertEquals(0, scene.completions, "a win under god mode must not be counted")
        // Where it *went*, not where it ended: an invulnerable marine climbs the whole ladder
        // again inside the same hour, so reading the skill at the end measures the second run
        // rather than the win.
        assertEquals(0, lowestSkill, "a win restarts the ladder from the first level")
    }

    /**
     * The latch itself, which is the part the hour-long test cannot show: it can prove a
     * god-mode win is not counted, but not that an honest one would be, because an honest
     * marine does not survive Nightmare - that is the point of Nightmare.
     */
    @Test
    fun `god mode taints the run it is switched on in, and only that run`() {
        GameData.clearRandom()
        val scene = Scene(720, 1600)

        assertTrue(!scene.cheated, "a fresh scene has nothing to hide")

        scene.invulnerable = true
        assertTrue(scene.cheated, "switching god mode on must taint the run in progress")

        scene.invulnerable = false
        assertTrue(scene.cheated, "switching it off again must not launder the same run")

        // A death is the only way out with god mode off, and it is what starts a clean run.
        var t = 0
        while (t < TICRATE * 600 && scene.cheated) scene.tick(++t)
        assertTrue(!scene.cheated, "a restart with god mode off must begin a countable run")

        // The converse - a restart while god mode is still on - is what `cheated = invulnerable`
        // in restart() is for, and the hour-long test above is where it is exercised: that
        // marine wins with it on, restarts, and must still be uncounted afterwards.
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
