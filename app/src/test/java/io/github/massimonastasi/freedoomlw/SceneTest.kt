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
        // The hardest skills stretch the queue to nine eighths and can respawn the fallen,
        // so the ceiling is no longer the wave's own size. Doubling it still catches a
        // runaway spawn, which is what this bound is for.
        val biggestWave = the engineData.waves.maxOf { it.size } * 2
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

    /**
     * The difficulty ladder: finishing the wave table promotes the marine one skill level,
     * it stops at Nightmare, and dying puts him back at the bottom.
     */
    @Test
    fun `the skill climbs with the table and a death resets it`() {
        the engineData.clearRandom()
        val scene = Scene(worldWidth, worldHeight)
        scene.invulnerable = true

        assertEquals(0, scene.skill, "the first run must start on the lowest skill")

        var t = 0
        var reached = 0
        // Long enough to climb several levels while nothing can kill him.
        while (t < TICRATE * 3600) {
            scene.tick(++t)
            assertTrue(scene.skill in the engineData.skills.indices, "skill ${scene.skill} off the table")
            if (scene.skill > reached) {
                reached = scene.skill
                assertEquals(0, scene.wave, "the promotion must land on the first wave")
            }
        }
        assertTrue(reached > 0, "the skill never rose in an hour of invulnerable play")

        // Now let him be killed: the ladder must collapse back to the bottom.
        scene.invulnerable = false
        val hardened = scene.skill
        assertTrue(hardened > 0, "expected to be above the lowest skill before dying")
        while (t < TICRATE * 5400 && scene.skill == hardened) scene.tick(++t)
        assertEquals(0, scene.skill, "death must restart from the lowest skill")
    }

    /**
     * How often the marine clears the opening wave on his own, with nobody touching the
     * screen — only the drops the scene hands him.
     *
     * Runs the same opening from many points in the P_Random table, since the table is
     * fixed and a single run would only ever measure one shuffle of the drops.
     */
    private fun clearedWaveOneAlone(skill: Int, runs: Int = 60): Int {
        var wins = 0
        for (r in 0 until runs) {
            the engineData.clearRandom()
            repeat(r * 4) { the engineData.pRandom() }
            val scene = Scene(worldWidth, worldHeight, startSkill = skill)
            var t = 0
            var died = false
            while (t < TICRATE * 120 && scene.wave == 0 && !died) {
                scene.tick(++t)
                if (scene.deathFade > 0f) died = true
            }
            if (!died && scene.wave > 0) wins++
        }
        return wins * 100 / runs
    }

    @Test
    fun `the drops carry the marine through the first wave, less and less as it hardens`() {
        val odds = the engineData.skills.indices.map { clearedWaveOneAlone(it) }
        println("cleared wave 1 unaided: " + the engineData.skills.indices.joinToString {
            "${the engineData.skills[it].name} ${odds[it]}%"
        })

        // Measured shape: 100, 90, 85, 35, 1.
        assertTrue(odds.first() >= 90, "the lowest skill must manage alone: ${odds.first()}%")
        assertTrue(odds.last() <= 20, "the hardest must almost never manage alone: ${odds.last()}%")
        assertTrue(odds[2] <= 90, "the middle skill must already be a real risk: ${odds[2]}%")
        for (i in 1 until odds.size) {
            assertTrue(odds[i] <= odds[i - 1], "the odds must never rise with the skill: $odds")
        }
    }

    @Test
    fun `the arsenal keeps the best loaded weapon and falls back to the pistol`() {
        val kit = Loadout()
        assertEquals(0, kit.owned, "the marine starts with the pistol alone, which is not owned")

        kit.take(the engineData.WEAPON_SHOTGUN)
        kit.take(the engineData.WEAPON_CHAINGUN)
        assertTrue(kit.has(the engineData.WEAPON_SHOTGUN) && kit.has(the engineData.WEAPON_CHAINGUN), "both must be carried")

        // Emptying the chaingun takes it away, and the shotgun is what is left loaded.
        kit.drop(the engineData.WEAPON_CHAINGUN)
        assertTrue(kit.has(the engineData.WEAPON_SHOTGUN), "the shotgun must survive losing the chaingun")
        assertTrue(!kit.has(the engineData.WEAPON_CHAINGUN), "an empty weapon must be gone, not merely unused")

        kit.drop(the engineData.WEAPON_SHOTGUN)
        assertEquals(0, kit.owned, "with nothing loaded he is back to the pistol")
    }

    @Test
    fun `ammunition is never dropped on its own`() {
        assertTrue(
            the engineData.items.none { it.kind !in intArrayOf(the engineData.ITEM_HEALTH, the engineData.ITEM_ARMOR, the engineData.ITEM_WEAPON) },
            "the drop table must hold only health, armour and weapons",
        )
        // Healing outweighs armour, which outweighs the guns.
        fun share(kind: Int) = the engineData.items.filter { it.kind == kind }.sumOf { it.weight }
        assertTrue(share(the engineData.ITEM_HEALTH) > share(the engineData.ITEM_ARMOR), "health must lead")
        assertTrue(share(the engineData.ITEM_ARMOR) > share(the engineData.ITEM_WEAPON), "armour must come before the guns")
    }

    @Test
    fun `nightmare makes the FleshWorm fast and nothing else`() {
        the engineData.clearRandom()
        val fast = Actor(0).apply { creature = the engineData.fleshWorm; this.fast = true }
        val normal = Actor(0).apply { creature = the engineData.fleshWorm }

        // g_game.c halves the run tics, which in the engine doubles both the animation and
        // the stepping, since movement happens inside A_Chase.
        val walked = (1..40).count { fast.frame(it) != fast.frame(it - 1) }
        val strolled = (1..40).count { normal.frame(it) != normal.frame(it - 1) }
        assertEquals(strolled * 2, walked, "the fast FleshWorm must animate at twice the rate")
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
    fun `sprite rotation matches the artwork on all four axes`() {
        val a = Actor(0)

        // Taken from the sprites themselves, not from the engine formula: decoding the
        // eight rotations of the walk frame shows 1 facing the camera, 5 facing away,
        // 3 a profile facing left and 7 a profile facing right.
        //
        // Checking only the vertical pair is what let a mirrored horizontal mapping through
        // once already: 2 and 6 survive a reflection of the horizontal axis unchanged, so
        // they cannot tell a rotation from a reflection. All four are needed.
        a.facing = 2                                   // DI_NORTH, down the screen
        assertEquals(1, a.spriteRotation(), "walking towards the camera must show the front")

        a.facing = 6                                   // DI_SOUTH, up the screen
        assertEquals(5, a.spriteRotation(), "walking away must show the back")

        a.facing = 4                                   // DI_WEST, leftwards
        assertEquals(3, a.spriteRotation(), "walking left must show the left-facing profile")

        a.facing = 0                                   // DI_EAST, rightwards
        assertEquals(7, a.spriteRotation(), "walking right must show the right-facing profile")

        // The diagonals follow from the four above, and the eight must map one to one.
        assertEquals(8, (0..7).map { a.facing = it; a.spriteRotation() }.toSet().size)

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
    fun `nothing ever moves diagonally`() {
        the engineData.clearRandom()
        val scene = Scene(worldWidth, worldHeight)

        var moves = 0
        for (t in 1..TICRATE * 600) {
            scene.tick(t)
            for (a in scene.actors) {
                if (a.creature == null || a.moveDir == the engineData.DI_NODIR) continue
                moves++
                assertTrue(
                    a.moveDir % 2 == 0,
                    "tic $t: ${a.creature?.name} moving diagonally, moveDir=${a.moveDir}",
                )
                // Only the four axial sprite angles can ever be needed.
                assertTrue(
                    a.spriteRotation() in intArrayOf(1, 3, 5, 7),
                    "tic $t: rotation ${a.spriteRotation()} is a diagonal view",
                )
            }
        }
        assertTrue(moves > 5000, "not enough movement sampled: $moves")
    }

    @Test
    fun `a hurt marine goes for supplies instead of shooting`() {
        the engineData.clearRandom()
        val scene = Scene(worldWidth, worldHeight)

        var hurtTics = 0
        var attackingWhileHurt = 0
        for (t in 1..TICRATE * 600) {
            scene.tick(t)
            val p = scene.actors.firstOrNull { it.isPlayer && !it.dead } ?: continue
            if (p.health * 2 >= the engineData.player.health) continue
            // Only counts when there is actually something to go and fetch.
            if (scene.actors.none { it.mode == Mode.ITEM }) continue
            hurtTics++
            if (p.mode == Mode.ATTACK) attackingWhileHurt++
        }
        assertTrue(hurtTics > 100, "the marine was never hurt with an item available")
        // He may still be finishing an attack begun before dropping below half health, so
        // this is about not starting new ones rather than never being in the state.
        assertTrue(
            attackingWhileHurt * 4 < hurtTics,
            "hurt with supplies around but still shooting for $attackingWhileHurt of $hurtTics tics",
        )
    }

    @Test
    fun `the marine stands still for a moment after materialising`() {
        the engineData.clearRandom()
        val scene = Scene(worldWidth, worldHeight)

        scene.tick(1)
        val p = scene.actors.first { it.isPlayer }
        val x = p.x
        val y = p.y

        // Long enough to read as an arrival: the creatures' own reactiontime of 8 tics is
        // under a quarter of a second and goes unnoticed.
        for (t in 2..TICRATE / 3) {
            scene.tick(t)
            assertEquals(x, p.x, "tic $t: the marine moved before his pause was over")
            assertEquals(y, p.y, "tic $t: the marine moved before his pause was over")
        }

        // ...and then he does get going.
        for (t in TICRATE / 3..TICRATE * 3) scene.tick(t)
        assertTrue(p.x != x || p.y != y, "the marine never started moving")
    }

    @Test
    fun `a small surface still keeps everyone on screen`() {
        // The world is derived from the surface, so it follows the display. What it must
        // also survive is a surface small enough that the spawn margin no longer fits:
        // split screen, a cover display, or the thumbnail in a wallpaper picker.
        the engineData.clearRandom()
        val narrow = 180
        val short = 320
        val scene = Scene(narrow, short)

        for (t in 1..TICRATE * 120) {
            scene.tick(t)
            for (a in scene.actors) {
                assertTrue(
                    a.x >= 0 && a.x <= narrow * the engineData.FRACUNIT,
                    "tic $t: x=${a.x / the engineData.FRACUNIT} outside a $narrow unit world",
                )
                assertTrue(
                    a.y >= 0 && a.y <= short * the engineData.FRACUNIT,
                    "tic $t: y=${a.y / the engineData.FRACUNIT} outside a $short unit world",
                )
            }
        }
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
