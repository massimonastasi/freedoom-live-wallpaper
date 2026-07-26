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

/**
 * How far up the table a single life gets, and what the table costs in time.
 *
 * This measures rather than asserts. The question it answers is whether the ladder can move
 * at all: a promotion needs the whole table cleared in one life, so if a life never reaches
 * the last wave the skill never rises.
 */
class WaveReachTest {

    @Test
    fun `how far one life gets, and what the table costs`() {
        val last = GameData.waves.size - 1

        // What the table costs in time if nothing goes wrong: every arrival waits its
        // spawnDelay, and every wave is followed by its rest.
        var tics = 0
        for (w in GameData.waves) {
            val arrivals = (w.order.size + w.burst - 1) / w.burst
            tics += arrivals * w.spawnDelay + w.rest
        }
        println("table floor: ${"%.1f".format(tics.toFloat() / TICRATE)} s of pure waiting, " +
            "${GameData.waves.sumOf { it.order.size }} arrivals over ${GameData.waves.size} waves")

        val runs = 400
        val reached = IntArray(GameData.waves.size)
        var lives = 0
        var totalLife = 0L
        var reachedLast = 0

        for (r in 0 until runs) {
            GameData.clearRandom()
            repeat(r) { GameData.pRandom() }
            val scene = Scene(720, 1600)

            var t = 0
            var best = 0
            var born = 0
            var current: Actor? = null
            var done = false

            // Deliberately does *not* stop at the first death: this run measures the mean
            // life and how far the scene gets over a long stretch. Whether a *single* life
            // reaches the end is a different question, and SceneTest asks it — an earlier
            // version of this loop conflated the two and reported 84% against its 18%.
            while (t < TICRATE * 900 && !done) {
                scene.tick(++t)
                best = maxOf(best, scene.wave)
                if (scene.wave == last) { reachedLast++; done = true }

                val p = scene.actors.firstOrNull { it.isPlayer && !it.dead }
                if (p != null && p !== current) {
                    if (current != null) { lives++; totalLife += (t - born) }
                    current = p
                    born = t
                }
            }
            reached[best]++
        }

        println("reached the last wave in one life: ${reachedLast * 100 / runs}% of $runs runs")
        println("mean life: ${"%.1f".format(totalLife.toFloat() / maxOf(1, lives) / TICRATE)} s over $lives deaths")
        println("highest wave a life reached, by wave:")
        for (i in reached.indices) {
            if (reached[i] > 0) println("  wave ${i + 1}: ${reached[i] * 100 / runs}%")
        }
    }
}
