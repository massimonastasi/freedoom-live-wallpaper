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
 * This does not verify: it measures. It runs the scene and prints the gameplay statistics,
 * so balance decisions are made on numbers rather than by eye.
 */
class SceneBalanceTest {

    @Test
    fun `ten minute statistics`() {
        GameData.clearRandom()
        val scene = Scene(720, 1600)

        val spawned = HashMap<String, Int>()
        val killed = HashMap<String, Int>()
        val seen = HashSet<Actor>()
        val counted = HashSet<Actor>()
        var playerDeaths = 0
        var playerAlive = 0
        val aliveSamples = ArrayList<Int>()
        var lastPlayer: Actor? = null

        var highestWave = 0
        var highestSkill = 0
        val skillTics = IntArray(GameData.skills.size)
        val totalTics = TICRATE * 600
        for (t in 1..totalTics) {
            scene.tick(t)
            if (scene.wave > highestWave) highestWave = scene.wave
            if (scene.skill > highestSkill) highestSkill = scene.skill
            skillTics[scene.skill]++

            for (a in scene.actors) {
                val c = a.creature ?: continue
                if (seen.add(a)) spawned[c.name] = (spawned[c.name] ?: 0) + 1
                if (a.dead && counted.add(a)) killed[c.name] = (killed[c.name] ?: 0) + 1
            }
            val p = scene.actors.firstOrNull { it.isPlayer && !it.dead }
            if (p != null) {
                playerAlive++
                if (lastPlayer !== p && lastPlayer != null) playerDeaths++
                lastPlayer = p
            }
            if (t % TICRATE == 0) aliveSamples.add(scene.actors.count { it.creature != null && !it.dead && !it.isPlayer })
        }

        println("=== 10 minutes of simulation ===")
        println("total actors spawned: ${seen.size}")
        println("marine deaths: $playerDeaths")
        println("marine alive: ${playerAlive * 100 / totalTics}% of the time")
        println("average demons alive: ${aliveSamples.average().let { "%.1f".format(it) }}")
        println("highest wave reached: ${highestWave + 1}")
        println("highest skill reached: ${GameData.skills[highestSkill].name}")
        for (i in GameData.skills.indices) {
            if (skillTics[i] > 0) println("  %-22s %2d%% of the time".format(GameData.skills[i].name, skillTics[i] * 100 / totalTics))
        }
        println()
        println("%-16s %8s %8s %10s".format("creature", "spawned", "killed", "% killed"))
        for (c in GameData.creatures) {
            val s = spawned[c.name] ?: 0
            val k = killed[c.name] ?: 0
            println("%-16s %8d %8d %9d%%".format(c.name, s, k, if (s > 0) k * 100 / s else 0))
        }
    }
}
