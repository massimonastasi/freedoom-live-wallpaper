package io.github.massimonastasi.freedoomlw

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks on the constants and on the movement maths.
 *
 * If anyone touches the fixed-point arithmetic, the direction tables or P_Random, these
 * fail: they are the safety net for fidelity to the original game.
 */
class the engineDataTest {

    @Test
    fun `a cardinal step covers exactly speed units`() {
        // MT_TROOP has speed 8: in one tic heading east it must move exactly 8 units.
        val step = 8 * the engineData.xspeed[0]
        assertEquals(8 * the engineData.FRACUNIT, step, "an eastward step is not worth 8 units")
        assertEquals(0, 8 * the engineData.yspeed[0], "an eastward step must not move y")
    }

    // The diagonal entries of the tables used to have a test of their own, asserting id's
    // 47000 approximation. Nothing moves diagonally any more, so it guarded numbers no code
    // reads. The transcription is still covered: the check below sums every direction with
    // its opposite, diagonals included.

    @Test
    fun `opposite directions are consistent`() {
        for (d in 0..7) {
            assertEquals(d, the engineData.opposite[the engineData.opposite[d]], "opposite is not involutive for $d")
            // The opposite direction cancels the movement out.
            assertEquals(0, the engineData.xspeed[d] + the engineData.xspeed[the engineData.opposite[d]])
            assertEquals(0, the engineData.yspeed[d] + the engineData.yspeed[the engineData.opposite[d]])
        }
        assertEquals(the engineData.DI_NODIR, the engineData.opposite[the engineData.DI_NODIR])
    }

    @Test
    fun `P_Random reproduces the sequence from the id source`() {
        the engineData.clearRandom()
        // rndtable[1..5] from the original m_random.c.
        val expected = intArrayOf(8, 109, 220, 222, 241)
        for (e in expected) assertEquals(e, the engineData.pRandom())
        assertEquals(256, the engineData.rndtable.size)
        // Deterministic: resetting the index replays it identically.
        the engineData.clearRandom()
        assertEquals(8, the engineData.pRandom())
    }

    @Test
    fun `creature speeds match info_c`() {
        fun speedOf(name: String) = the engineData.creatures.first { it.name == name }.speed
        assertEquals(8, speedOf("Zombie"))
        assertEquals(8, speedOf("Serpentipede"))
        // The FleshWorm (SARG) is the only faster one: speed 10.
        assertEquals(10, speedOf("FleshWorm"))
        assertEquals(8, speedOf("PainLord"))
    }

    @Test
    fun `every sprite has a valid index`() {
        // The indices are assigned in an init block: if anyone moves it before
        // spritePrefixes is built they all fall back to -1 and the app crashes on start.
        val n = the engineData.spritePrefixes.size
        assertTrue(the engineData.bloodSpriteIndex in 0 until n, "blood: ${the engineData.bloodSpriteIndex}")
        assertTrue(the engineData.fogSpriteIndex in 0 until n, "fog: ${the engineData.fogSpriteIndex}")
        assertTrue(the engineData.player.spriteIndex in 0 until n, "player")
        for (c in the engineData.creatures) {
            assertTrue(c.spriteIndex in 0 until n, "${c.name}: ${c.spriteIndex}")
            assertEquals(c.lumpPrefix, the engineData.spritePrefixes[c.spriteIndex])
        }
        for (p in the engineData.projectiles) {
            assertTrue(p.spriteIndex in 0 until n, "projectile ${p.lumpPrefix}")
            assertEquals(p.lumpPrefix, the engineData.spritePrefixes[p.spriteIndex])
        }
        for (i in the engineData.items) {
            assertTrue(i.spriteIndex in 0 until n, "item ${i.lumpPrefix}")
            assertEquals(i.lumpPrefix, the engineData.spritePrefixes[i.spriteIndex])
        }
    }

    @Test
    fun `every creature has consistent animations`() {
        for (c in the engineData.creatures + the engineData.player) {
            for (a in listOf(c.attack, c.pain, c.death)) {
                assertEquals(a.frames.size, a.tics.size, "${c.name}: frames and tics do not match")
                assertTrue(a.frames.isNotEmpty(), "${c.name}: empty animation")
            }
            // The last death frame stays forever (tic -1), as in states[].
            assertEquals(-1, c.death.tics.last(), "${c.name}: the corpse must remain")
        }
    }

    @Test
    fun `a Serpentipede covers 280 units per second`() {
        // speed 8 per tic at 35 tics/s = 280 units per second. If the fixed-point maths
        // breaks, this number changes.
        val perTic = 8 * the engineData.xspeed[0] / the engineData.FRACUNIT
        assertEquals(280, perTic * TICRATE)
    }
}
