package io.github.massimonastasi.freedoomlw

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scene has no Android dependency, so the whole simulation runs on the JVM: ten minutes of
 * play in a few milliseconds. This is the net that catches inconsistent-state crashes
 * (animation indices out of sequence, actors outside the world) before the phone does.
 */
class SceneTest {

    private val worldWidth = 720
    private val worldHeight = 1600

    @Test
    fun `ten minutes of simulation with no inconsistent state`() {
        the engineData.clearRandom()
        val scene = Scene(worldWidth, worldHeight)

        for (t in 1..TICRATE * 600) {
            scene.tick(t)

            for (a in scene.actors) {
                val anim = a.anim
                if (anim != null) {
                    assertTrue(
                        a.animStep in anim.frames.indices,
                        "tic $t: animStep ${a.animStep} outside 0..${anim.length - 1}",
                    )
                }
                // frame() is called on every draw: it must never be able to throw.
                a.frame(t)

                if (a.creature != null) {
                    val r = a.radius * the engineData.FRACUNIT
                    assertTrue(a.x >= r - 1 && a.x <= worldWidth * the engineData.FRACUNIT - r + 1, "tic $t: x outside the world")
                    assertTrue(a.y >= r - 1 && a.y <= worldHeight * the engineData.FRACUNIT - r + 1, "tic $t: y outside the world")
                }
            }
        }
    }

    @Test
    fun `the scene stays populated and does not grow without bound`() {
        the engineData.clearRandom()
        val scene = Scene(worldWidth, worldHeight)

        // A single instant is not enough: between waves there is a pause where zero demons
        // is correct. Look at the last half minute instead.
        var maxRecent = 0
        val biggestWave = the engineData.waves.maxOf { it.size }
        for (t in 1..TICRATE * 600) {
            scene.tick(t)
            val n = scene.actors.count { it.creature != null && !it.isPlayer && !it.dead }
            if (t > TICRATE * 570) maxRecent = maxOf(maxRecent, n)
            assertTrue(n <= biggestWave, "tic $t: $n demons, the largest wave holds $biggestWave")
        }

        assertTrue(maxRecent > 0, "no demon appeared during the last half minute")
        // Corpses, projectiles and effects must not accumulate indefinitely.
        assertTrue(scene.actors.size < 60, "too many actors on stage: ${scene.actors.size}")
    }

    @Test
    fun `the marine arrives first and enemies one at a time`() {
        the engineData.clearRandom()
        val scene = Scene(worldWidth, worldHeight)

        fun demons() = scene.actors.count { it.creature != null && !it.isPlayer }

        scene.tick(1)
        assertTrue(scene.actors.any { it.isPlayer }, "the marine must appear first")
        assertEquals(0, demons(), "no enemy alongside the marine")

        // The first one arrives after the wave 1 delay: three seconds.
        val firstDelay = the engineData.waves[0].spawnDelay
        for (t in 2 until 1 + firstDelay) scene.tick(t)
        assertEquals(0, demons(), "an enemy arrived before the expected $firstDelay tics")

        scene.tick(1 + firstDelay)
        assertEquals(1, demons(), "exactly one must arrive after the delay")

        // The second does not come with the first, but after another interval.
        scene.tick(2 + firstDelay)
        assertEquals(1, demons(), "two enemies together in the first wave")
    }

    @Test
    fun `waves get denser as they progress`() {
        // The delay must fall monotonically: that is the tension curve.
        val delays = the engineData.waves.map { it.spawnDelay }
        for (i in 1 until delays.size) {
            assertTrue(delays[i] <= delays[i - 1], "wave ${i + 1} is slower than the previous one")
        }
        assertTrue(delays.first() > delays.last(), "no acceleration between the first and last wave")
        // Paired arrivals only exist in the second half.
        val firstBurst = the engineData.waves.indexOfFirst { it.burst > 1 }
        assertTrue(firstBurst >= the engineData.waves.size / 2, "multiple arrivals too early: wave ${firstBurst + 1}")
    }

    @Test
    fun `nobody arrives while the marine is dead`() {
        the engineData.clearRandom()
        val scene = Scene(worldWidth, worldHeight)

        fun demons() = scene.actors.count { it.creature != null && !it.isPlayer && !it.dead }

        var previous = 0
        var deathTics = 0
        for (t in 1..TICRATE * 600) {
            scene.tick(t)
            val now = demons()
            if (scene.deathFade > 0f) {
                deathTics++
                assertTrue(
                    now <= previous,
                    "tic $t: ${now - previous} enemies arrived while the screen is red",
                )
            }
            previous = now
        }
        assertTrue(deathTics > 0, "the marine never died: this test verified nothing")
    }

    @Test
    fun `creatures appear well inside the visible area`() {
        the engineData.clearRandom()
        val scene = Scene(worldWidth, worldHeight)
        val seen = HashSet<Actor>()
        // The widest sprite reaches about a hundred map units from its anchor, so anything
        // appearing closer than that to an edge starts partly off screen.
        val margin = 80 * the engineData.FRACUNIT

        for (t in 1..TICRATE * 300) {
            scene.tick(t)
            for (a in scene.actors) {
                if (a.creature == null || !seen.add(a)) continue
                assertTrue(
                    a.x >= margin && a.x <= worldWidth * the engineData.FRACUNIT - margin,
                    "tic $t: appeared at x=${a.x / the engineData.FRACUNIT}, too close to the edge",
                )
                assertTrue(
                    a.y >= margin && a.y <= worldHeight * the engineData.FRACUNIT - margin,
                    "tic $t: appeared at y=${a.y / the engineData.FRACUNIT}, too close to the edge",
                )
            }
        }
        assertTrue(seen.size > 10, "too few spawns to judge: ${seen.size}")
    }

    @Test
    fun `the marine faces where he walks and turns only to shoot`() {
        the engineData.clearRandom()
        val scene = Scene(worldWidth, worldHeight)

        var sawWalkFacing = false
        var sawAttackFacing = false
        var previous: Actor? = null
        var px = 0
        var py = 0

        for (t in 1..TICRATE * 300) {
            scene.tick(t)
            val p = scene.actors.firstOrNull { it.isPlayer && !it.dead }
            if (p == null) { previous = null; continue }

            // The invariant is about actually moving, not about being in WALK: on the tic a
            // pain state ends the actor is back in WALK without having moved that tic.
            if (p === previous && (p.x != px || p.y != py)) {
                assertEquals(p.moveDir, p.facing, "tic $t: moved but facing elsewhere")
                sawWalkFacing = true
            }
            // While firing he must look at the target, which is usually not where he is
            // heading, because he backs away as he shoots.
            if (p.mode == Mode.ATTACK && p.facing != p.moveDir) sawAttackFacing = true

            previous = p
            px = p.x
            py = p.y
        }
        assertTrue(sawWalkFacing, "the marine never moved facing his direction of travel")
        assertTrue(sawAttackFacing, "the marine never turned away from his path to shoot")
    }

    @Test
    fun `an actor walking towards the camera is drawn from the front`() {
        val a = Actor(0)

        // Screen y grows downwards and DI_NORTH moves towards +y, so DI_NORTH is towards
        // the viewer. Getting this backwards draws everyone walking away from where they go.
        a.facing = 2                                   // DI_NORTH, down the screen
        assertEquals(1, a.spriteRotation(), "walking towards the camera must show the front")

        a.facing = 6                                   // DI_SOUTH, up the screen
        assertEquals(5, a.spriteRotation(), "walking away must show the back")

        // The other six angles must be distinct and cover the range once each.
        val all = (0..7).map { a.facing = it; a.spriteRotation() }
        assertEquals((1..8).toList().sorted(), all.sorted(), "the eight angles must map one to one")

        a.facing = the engineData.DI_NODIR
        assertEquals(1, a.spriteRotation(), "a still actor faces the viewer")
    }

    @Test
    fun `tapping drops a pickup, dropping an icon sends demons`() {
        the engineData.clearRandom()
        val scene = Scene(worldWidth, worldHeight)
        for (t in 1..TICRATE) scene.tick(t)

        val itemsBefore = scene.actors.count { it.mode == Mode.ITEM }
        scene.tapAt(300 * the engineData.FRACUNIT, 800 * the engineData.FRACUNIT)
        assertEquals(itemsBefore + 1, scene.actors.count { it.mode == Mode.ITEM }, "the tap dropped nothing")

        val demonsBefore = scene.actors.count { it.creature != null && !it.isPlayer }
        scene.dropAt(400 * the engineData.FRACUNIT, 900 * the engineData.FRACUNIT)
        assertTrue(
            scene.actors.count { it.creature != null && !it.isPlayer } > demonsBefore,
            "the icon drop summoned nobody",
        )

        // Even a tap in the corner has to land where the whole sprite is visible.
        scene.tapAt(0, 0)
        val corner = scene.actors.last { it.mode == Mode.ITEM }
        assertTrue(corner.x >= 80 * the engineData.FRACUNIT, "item dropped too close to the edge")
        assertTrue(corner.y >= 80 * the engineData.FRACUNIT, "item dropped too close to the edge")
    }

    @Test
    fun `interaction is ignored while the marine is dead`() {
        the engineData.clearRandom()
        val scene = Scene(worldWidth, worldHeight)

        var checked = false
        for (t in 1..TICRATE * 600) {
            scene.tick(t)
            if (scene.deathFade <= 0f) continue
            // The red wash is a pause: it must not be possible to litter it with pickups
            // nobody can collect, or with demons attacking a corpse.
            val items = scene.actors.count { it.mode == Mode.ITEM }
            val demons = scene.actors.count { it.creature != null && !it.isPlayer }
            scene.tapAt(300 * the engineData.FRACUNIT, 800 * the engineData.FRACUNIT)
            scene.dropAt(300 * the engineData.FRACUNIT, 800 * the engineData.FRACUNIT)
            assertEquals(items, scene.actors.count { it.mode == Mode.ITEM }, "tic $t: tap accepted while dead")
            assertEquals(demons, scene.actors.count { it.creature != null && !it.isPlayer }, "tic $t: drop accepted while dead")
            checked = true
        }
        assertTrue(checked, "the marine never died: this test verified nothing")
    }

    @Test
    fun `combat actually happens`() {
        the engineData.clearRandom()
        val scene = Scene(worldWidth, worldHeight)
        var sawBlood = false
        var sawDeath = false
        var sawProjectile = false

        for (t in 1..TICRATE * 600) {
            scene.tick(t)
            for (a in scene.actors) {
                if (a.spriteIndex == the engineData.bloodSpriteIndex) sawBlood = true
                if (a.mode == Mode.PROJECTILE) sawProjectile = true
                if (a.dead) sawDeath = true
            }
        }
        assertTrue(sawBlood, "no hit landed in ten minutes")
        assertTrue(sawProjectile, "no fireball was thrown")
        assertTrue(sawDeath, "nobody died")
    }
}
