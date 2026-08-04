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
import kotlin.test.assertTrue

/**
 * What a file missing half the bestiary actually plays.
 *
 * Every Phase 1 IWAD - the commercial one, Freedoom Phase 1, the shareware release - carries
 * nine of the fourteen creatures named here. The five it does not are spread through the
 * middle of the table, and the substitution that fills those holes decides what that half of
 * the wave table looks like.
 *
 * Before this was rewritten it stepped down to the nearest survivor and stopped there, which
 * collapsed the run of missing creatures onto one: waves 17 to 22 came out as six consecutive
 * waves of Trilobites, three of them identical two apart. The table had stopped escalating
 * and stopped changing, and nothing failed.
 */
class SubstitutionTest {

    /** The nine creatures every Phase 1 file has. */
    private val phase1 = setOf("POSS", "SPOS", "TROO", "SARG", "SKUL", "HEAD", "BOSS", "CYBR", "SPID", "PLAY")

    private fun phase1Scene(): Scene {
        val drawable = BooleanArray(GameData.creatures.size) {
            GameData.creatures[it].lumpPrefix in phase1
        }
        return Scene(720, 1600, drawable = drawable)
    }

    /**
     * Arrivals, grouped by the tic they landed on: two creatures spawned in the same tic are
     * one event on screen, however many they are.
     */
    private fun arrivals(scene: Scene, tics: Int): List<List<String>> {
        val groups = ArrayList<List<String>>()
        var seen = HashSet<Actor>()
        for (t in 1..tics) {
            scene.tick(t)
            val fresh = scene.actors.filter { a ->
                a.creature != null && !a.isPlayer && a.spawnTic == t && a !in seen
            }
            seen.addAll(fresh)
            if (fresh.isNotEmpty()) groups.add(fresh.map { it.creature!!.name })
        }
        return groups
    }

    @Test
    fun `a Phase 1 roster still gets a table that changes`() {
        GameData.clearRandom()
        val scene = phase1Scene()
        scene.invulnerable = true                    // so it plays the whole table, not one life
        val groups = arrivals(scene, TICRATE * 900)

        assertTrue(groups.size > 40, "only ${groups.size} arrivals in fifteen minutes")

        // No creature arrives alone twice running. A pair of the same creature landing
        // together is one event and is allowed - that is what the compensation for a deep
        // substitution looks like, and it reads as a pair rather than as a stutter.
        var worst = 0
        var run = 1
        for (i in 1 until groups.size) {
            val previous = groups[i - 1]
            val current = groups[i]
            val same = previous.size == 1 && current.size == 1 && previous[0] == current[0]
            run = if (same) run + 1 else 1
            if (run > worst) worst = run
        }
        assertTrue(worst <= 2, "the same creature arrived alone $worst times in a row")

        // And the table is not one creature wearing every hat: a Phase 1 file has nine, and
        // the substitution should be reaching most of them rather than piling onto one.
        val distinct = groups.flatten().toSet()
        assertTrue(distinct.size >= 6, "only ${distinct.size} distinct creatures ever arrived: $distinct")
    }

    @Test
    fun `a boss is never used as a stand-in`() {
        val scene = phase1Scene()
        for (i in GameData.creatures.indices) {
            val s = scene.substitute(i)
            if (s == i) continue
            assertTrue(
                GameData.creatures[s].health < Scene.BOSS_FROM,
                "${GameData.creatures[i].name} was replaced by ${GameData.creatures[s].name}, " +
                    "which is a boss and would make an ordinary wave read as the finale",
            )
        }
    }
}
