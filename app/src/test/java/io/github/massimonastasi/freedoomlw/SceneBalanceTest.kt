package io.github.massimonastasi.freedoomlw

import org.junit.Test

/**
 * This does not verify: it measures. It runs the scene and prints the gameplay statistics,
 * so balance decisions are made on numbers rather than by eye.
 */
class SceneBalanceTest {

    @Test
    fun `ten minute statistics`() {
        the engineData.clearRandom()
        val scene = Scene(720, 1600)

        val spawned = HashMap<String, Int>()
        val killed = HashMap<String, Int>()
        val seen = HashSet<Actor>()
        val counted = HashSet<Actor>()
        var playerDeaths = 0
        var playerAlive = 0
        val aliveSamples = ArrayList<Int>()
        var lastPlayer: Actor? = null

        val totalTics = TICRATE * 600
        for (t in 1..totalTics) {
            scene.tick(t)

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
        println("highest wave reached: ${scene.wave + 1}")
        println()
        println("%-16s %8s %8s %10s".format("creature", "spawned", "killed", "% killed"))
        for (c in the engineData.creatures) {
            val s = spawned[c.name] ?: 0
            val k = killed[c.name] ?: 0
            println("%-16s %8d %8d %9d%%".format(c.name, s, k, if (s > 0) k * 100 / s else 0))
        }
    }
}
