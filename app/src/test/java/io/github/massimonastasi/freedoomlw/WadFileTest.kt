package io.github.massimonastasi.freedoomlw

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Check on the WAD parser: if anyone breaks the column decoding or the sprite naming
 * convention, this fails.
 *
 * The .wad is not under version control (see .gitignore), so the test skips itself rather
 * than failing when the file is missing.
 */
class WadFileTest {

    private val wadFile = File("src/main/assets/freedoom1.wad")

    private fun openWad(): WadFile {
        assumeTrue("freedoom1.wad missing: test skipped", wadFile.exists())
        val ch = RandomAccessFile(wadFile, "r").channel
        return WadFile(ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size()))
    }

    @Test
    fun `reads the directory and finds the expected sprites`() {
        val wad = openWad()
        assertTrue(wad.lumpCount > 1000, "too few lumps: ${wad.lumpCount}")
        assertTrue(wad.indexOf("PLAYPAL") >= 0, "PLAYPAL missing")
        // Every creature in the plan must have its sprites inside freedoom1.wad.
        for (prefix in listOf("PLAY", "POSS", "SPOS", "TROO", "SARG", "HEAD", "BOSS")) {
            assertTrue(wad.lumpsStartingWith(prefix).isNotEmpty(), "no $prefix sprite")
        }
    }

    @Test
    fun `decodes a patch with sensible size and pixels`() {
        val wad = openWad()
        val i = wad.indexOf("TROOA1")
        assertTrue(i >= 0, "TROOA1 missing")
        val p = wad.decodePatch(i)
        assertTrue(p.width in 8..200, "suspicious width: ${p.width}")
        assertTrue(p.height in 8..200, "suspicious height: ${p.height}")
        assertEquals(p.width * p.height, p.pixels.size)
        // A monster is not entirely transparent: if it were, the posts were not read.
        val opaque = p.pixels.count { it ushr 24 != 0 }
        assertTrue(opaque > p.pixels.size / 10, "almost fully transparent: $opaque opaque pixels")
        // ...nor entirely opaque: the column format leaves the corners transparent.
        assertTrue(opaque < p.pixels.size, "no transparent pixel: columns read incorrectly")
    }

    @Test
    fun `mirrored rotations share a single lump`() {
        val wad = openWad()
        val set = SpriteSet(wad, "TROO")
        assertTrue(set.frameCount >= 4, "not enough walk frames: ${set.frameCount}")

        // TROOA2A8: the same lump for angles 2 and 8, the second one mirrored.
        val a2 = set.resolve(0, 2)
        val a8 = set.resolve(0, 8)
        assertTrue(a2 >= 0 && a8 >= 0, "frame A rotations 2/8 missing")
        assertEquals(a2 shr 1, a8 shr 1, "the two rotations should share the lump")
        assertEquals(0, a2 and 1, "rotation 2 must not be mirrored")
        assertEquals(1, a8 and 1, "rotation 8 must be mirrored")
        assertEquals("TROOA2A8", wad.nameAt(a2 shr 1))
    }
}
