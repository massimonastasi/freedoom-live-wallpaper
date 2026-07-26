package io.github.massimonastasi.freedoomlw

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A decoded image, in ARGB. Plain data with no Android types, so it can be tested on the JVM.
 *
 * [xOffset]/[yOffset] come from the patch format and mark the anchor point (for monsters,
 * their feet). Ignoring them makes sprites jitter while animating, because frames have
 * different sizes from one another.
 */
class Patch(
    val width: Int,
    val height: Int,
    val xOffset: Int,
    val yOffset: Int,
    val pixels: IntArray,
)

/**
 * Reader for WAD files in the classic the engine/Freedoom format.
 *
 * A single load path serves both Freedoom and a user-supplied IWAD: lump names are
 * identical across every the engine-compatible IWAD because the original engine hardcodes them
 * in sprnames[] (info.c), so swapping the sprites works by construction.
 *
 * ponytail: column-based patch format only, no Zthe engine PNG or TEXTURES lumps. Freedoom and
 * the commercial WADs all use the classic format.
 */
class WadFile(private val buf: ByteBuffer) {

    class NotAWadException(message: String) : Exception(message)

    private val lumpPos: IntArray
    private val lumpSize: IntArray
    private val lumpName: Array<String>
    private val byName: HashMap<String, Int>

    /** PLAYPAL palette 0, already converted to ARGB. */
    private val palette = IntArray(256)

    /** Colour of the red damage flash, taken from PLAYPAL palette 8. */
    val damageTint: Int

    init {
        buf.order(ByteOrder.LITTLE_ENDIAN)
        if (buf.capacity() < 12) throw NotAWadException("file too short")
        val magic = StringBuilder(4).apply {
            for (i in 0..3) append(buf.get(i).toInt().toChar())
        }.toString()
        if (magic != "IWAD" && magic != "PWAD") throw NotAWadException("header '$magic', expected IWAD or PWAD")

        val count = buf.getInt(4)
        val dirOffset = buf.getInt(8)
        if (count <= 0 || dirOffset <= 0 || dirOffset + count * 16 > buf.capacity()) {
            throw NotAWadException("invalid directory ($count lumps @ $dirOffset)")
        }

        lumpPos = IntArray(count)
        lumpSize = IntArray(count)
        lumpName = Array(count) { "" }
        byName = HashMap(count * 2)

        val name = StringBuilder(8)
        for (i in 0 until count) {
            val e = dirOffset + i * 16
            lumpPos[i] = buf.getInt(e)
            lumpSize[i] = buf.getInt(e + 4)
            name.setLength(0)
            for (c in 0..7) {
                val b = buf.get(e + 8 + c).toInt() and 0xFF
                if (b == 0) break
                name.append(b.toChar())
            }
            lumpName[i] = name.toString()
            // A duplicate name is not an error: the last one wins, as in the original engine.
            byName[lumpName[i]] = i
        }

        val pal = byName["PLAYPAL"] ?: throw NotAWadException("PLAYPAL missing")
        val p = lumpPos[pal]
        for (i in 0 until 256) {
            val r = buf.get(p + i * 3).toInt() and 0xFF
            val g = buf.get(p + i * 3 + 1).toInt() and 0xFF
            val b = buf.get(p + i * 3 + 2).toInt() and 0xFF
            palette[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        // PLAYPAL holds 14 palettes of 768 bytes: 0 is the normal one, 1-8 the red damage
        // ramp, 9-12 the yellow item flash, 13 the green radiation suit. The damage colour
        // is read from the WAD rather than invented: take the brightest index and see what
        // palette 8, the most intense one, turns it into.
        damageTint = if (lumpSize[pal] >= 9 * 768) {
            var whitest = 0
            var best = -1
            for (i in 0 until 256) {
                val c = palette[i]
                val sum = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
                if (sum > best) { best = sum; whitest = i }
            }
            val q = p + 8 * 768 + whitest * 3
            (0xFF shl 24) or
                ((buf.get(q).toInt() and 0xFF) shl 16) or
                ((buf.get(q + 1).toInt() and 0xFF) shl 8) or
                (buf.get(q + 2).toInt() and 0xFF)
        } else {
            0xFFAA1400.toInt()
        }
    }

    val lumpCount: Int get() = lumpPos.size

    fun indexOf(name: String): Int = byName[name] ?: -1

    fun nameAt(index: Int): String = lumpName[index]

    /** Indices of every lump whose name starts with [prefix]. */
    fun lumpsStartingWith(prefix: String): List<Int> =
        lumpName.indices.filter { lumpName[it].startsWith(prefix) }

    /**
     * Decodes a lump in the patch format.
     *
     * The format is natively sparse: each column is a list of posts (topdelta, length,
     * pixels) terminated by 0xFF, and uncovered areas stay transparent. No colour key
     * to deal with.
     */
    fun decodePatch(index: Int): Patch {
        val p = lumpPos[index]
        val w = buf.getShort(p).toInt() and 0xFFFF
        val h = buf.getShort(p + 2).toInt() and 0xFFFF
        val xOff = buf.getShort(p + 4).toInt()      // signed
        val yOff = buf.getShort(p + 6).toInt()
        require(w in 1..4096 && h in 1..4096) { "patch ${lumpName[index]} has size $w x $h" }

        val pixels = IntArray(w * h)                 // 0 = transparent
        for (x in 0 until w) {
            var o = p + buf.getInt(p + 8 + x * 4)
            while (true) {
                val topDelta = buf.get(o).toInt() and 0xFF
                if (topDelta == 0xFF) break
                val len = buf.get(o + 1).toInt() and 0xFF
                o += 3                                // skip the unused byte
                for (k in 0 until len) {
                    val y = topDelta + k
                    if (y < h) pixels[y * w + x] = palette[buf.get(o + k).toInt() and 0xFF]
                }
                o += len + 1                          // skip the trailing unused byte
            }
        }
        return Patch(w, h, xOff, yOff, pixels)
    }
}
