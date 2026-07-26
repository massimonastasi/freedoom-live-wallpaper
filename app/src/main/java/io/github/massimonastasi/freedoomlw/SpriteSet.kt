package io.github.massimonastasi.freedoomlw

import android.graphics.Bitmap
import android.util.LruCache

/**
 * The sprites of one actor (e.g. "TROO" = Serpentipede), indexed by frame and rotation.
 *
 * Naming convention, from r_things.c: 4 characters for the actor, one letter for the
 * frame, one digit for the rotation (0-8). A second letter+digit pair at positions 6-7
 * means the same lump also serves that angle, **mirrored** (e.g. TROOA2A8). Rotation 0
 * means "valid for every direction".
 *
 * Mirroring does not create a second Bitmap: the sprite is drawn with a negative X scale,
 * which the hardware Canvas does for free. That halves sprite memory.
 */
class SpriteSet(private val wad: WadFile, val prefix: String) {

    // slot[frame][rot]: (lump index shl 1) or flip, -1 when absent. rot 0 = all directions.
    private val slot = Array(26) { IntArray(9) { -1 } }

    /** Number of consecutive frames present starting from 'A'. */
    val frameCount: Int

    /** A decoded lump, with the anchor point from the patch format. */
    class Sprite(val bitmap: Bitmap, val xOffset: Int, val yOffset: Int)

    // The cache is measured in bytes rather than entries: sprites range from a few KB
    // (a blood drop) to several tens (a PainLord), so counting them as equivalent would
    // over- or under-estimate memory by an order of magnitude.
    private val cache = object : LruCache<Int, Sprite>(CACHE_BYTES) {
        override fun sizeOf(key: Int, value: Sprite) = value.bitmap.byteCount
    }

    init {
        for (i in wad.lumpsStartingWith(prefix)) {
            val n = wad.nameAt(i)
            if (n.length != 6 && n.length != 8) continue
            put(n[4], n[5], i, flip = false)
            if (n.length == 8) put(n[6], n[7], i, flip = true)
        }
        var f = 0
        while (f < 26 && hasFrame(f)) f++
        frameCount = f
    }

    private fun put(frameChar: Char, rotChar: Char, lump: Int, flip: Boolean) {
        val f = frameChar - 'A'
        val r = rotChar - '0'
        if (f !in 0..25 || r !in 0..8) return
        slot[f][r] = (lump shl 1) or (if (flip) 1 else 0)
    }

    private fun hasFrame(f: Int): Boolean = slot[f][0] >= 0 || slot[f][1] >= 0

    /**
     * Lump and mirror flag for frame [f] and rotation [rot] (1-8), packed into one int:
     * `index shl 1 or flip`. -1 when absent. Pure logic with no Android types: this is the
     * part the tests cover.
     */
    fun resolve(f: Int, rot: Int): Int {
        if (f !in 0..25) return -1
        // Rotation 0 = a single sprite valid for every angle (r_things.c: rotate = false).
        return if (slot[f][0] >= 0) slot[f][0] else slot[f][rot.coerceIn(1, 8)]
    }

    /**
     * The decoded sprite for the lump index returned by [resolve].
     *
     * ponytail: resolve() + sprite() instead of a single get() returning a pair — the
     * draw loop allocates nothing.
     */
    fun sprite(lump: Int): Sprite = cache.get(lump) ?: wad.decodePatch(lump).let {
        Sprite(
            Bitmap.createBitmap(it.pixels, it.width, it.height, Bitmap.Config.ARGB_8888),
            it.xOffset,
            it.yOffset,
        )
    }.also { cache.put(lump, it) }

    private companion object {
        /** Budget per actor. A creature's full sprite set stays comfortably below it. */
        const val CACHE_BYTES = 1 shl 20
    }
}
