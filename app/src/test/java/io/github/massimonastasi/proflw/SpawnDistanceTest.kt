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

import kotlin.math.abs
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Where a creature arrives, watched over a long run (point 4 of the roadmap).
 *
 * Two things must hold, and the earlier "opposite half" rule broke both:
 *  - no arrival lands on top of the marine — every spawn is at least SPAWN_MIN_DISTANCE away;
 *  - arrivals are not mirrored across the midline — a healthy share land on the marine's own
 *    side of it, which the old branch made impossible by construction.
 *
 * Every existing scene test counts arrivals; none asks *where* they land, so this is the one
 * that fails if the spawn point ever tracks the marine again.
 */
class SpawnDistanceTest {

    private val worldWidth = 720
    private val worldHeight = 1600

    // Reconstructed exactly as Scene derives them, so "same side of the midline" here means
    // the same line the spawn code splits on.
    private val marginTop = minOf(Scene.TOP_MARGIN, worldHeight / 3)
    private val spawnMarginBottom = minOf(Scene.SPAWN_MARGIN, worldHeight / 4)
    private val middle = (marginTop + (worldHeight - spawnMarginBottom)) / 2

    @Test
    fun `arrivals keep their distance and do not mirror the marine`() {
        val fr = GameData.FRACUNIT

        var spawns = 0
        var sameSide = 0
        var minDistUnits = Int.MAX_VALUE
        // One step of the fastest creature is 10 units; a spawn may take that one step in the
        // tic it is first seen, so the observed distance can undershoot the floor by that much.
        val slack = 16

        // Many short runs with different seeds put the marine in a wide spread of positions,
        // so "same side of the midline" is exercised with him high, low and on the line.
        for (r in 0 until 30) {
            GameData.clearRandom()
            repeat(r) { GameData.pRandom() }
            val scene = Scene(worldWidth, worldHeight)
            val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Actor, Boolean>())

            for (t in 1..TICRATE * 120) {
                scene.tick(t)
                val marine = scene.actors.firstOrNull { it.isPlayer && !it.dead } ?: continue
                for (a in scene.actors) {
                    if (a.creature == null || a.isPlayer) continue
                    if (!seen.add(a)) continue          // only the tic it first appears
                    spawns++

                    val dx = abs(a.x - marine.x)
                    val dy = abs(a.y - marine.y)
                    val d = (if (dx > dy) dx + (dy shr 1) else dy + (dx shr 1)) / fr
                    if (d < minDistUnits) minDistUnits = d

                    val spawnY = a.y / fr
                    val marineY = marine.y / fr
                    if ((spawnY - middle).sign() == (marineY - middle).sign()) sameSide++
                }
            }
        }

        println("spawns observed: $spawns")
        println("closest arrival to the marine: $minDistUnits units (floor ${Scene.SPAWN_MIN_DISTANCE})")
        println("landed on the marine's own side of the midline: ${sameSide * 100 / maxOf(1, spawns)}%")

        assertTrue(spawns > 500, "too few spawns to conclude anything: $spawns")
        assertTrue(
            minDistUnits >= Scene.SPAWN_MIN_DISTANCE - slack,
            "an arrival landed $minDistUnits units from the marine, inside the ${Scene.SPAWN_MIN_DISTANCE} floor",
        )
        // The old branch put this at ~0. Anything comfortably above zero proves the spawn is
        // no longer a mirror of his position; a fifth is a wide margin over that.
        assertTrue(
            sameSide * 5 > spawns,
            "only $sameSide of $spawns arrivals shared the marine's half — spawns look mirrored again",
        )
    }

    private fun Int.sign() = if (this >= 0) 1 else -1
}
