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

/**
 * Chooses the floor for each skill level by measuring the WAD's own flats.
 *
 * This used to be a list of names — AQF069, RROCK13, GRNROCK and so on — with a fallback
 * chain behind it. Names are the wrong thing to hold: they are Freedoom's, and a commercial
 * IWAD carries none of them, so every level fell down the same chain and landed on the same
 * flat. Measured on a real import, four of the five became CEIL5_1 and the ground stopped
 * saying anything about the difficulty.
 *
 * Measuring instead means the rule cannot refer to something that is not there. Any WAD with
 * flats gets a floor per skill; a WAD with too few gets what it has.
 *
 * ## What it selects on
 *
 *  - **Luminance 20 to 45.** A wallpaper sits behind the launcher icons and has to lose that
 *    contest. Brighter competes; darker stops reading as a floor at all.
 *  - **No channel above [MAX_CHANNEL].** Luminance alone is not enough, and the flat that
 *    proved it is AQF035: bright blue panels measuring 29, because the formula weights blue
 *    at 0.114. A backdrop has to be quiet in every channel, not merely quiet on average.
 *  - **Contrast no louder than [MAX_RELATIVE_CONTRAST].** The mean cannot tell an even stone
 *    floor from bright dots on black. Measured across both WADs, the speckled ones stand out
 *    cleanly on the standard deviation of pixel luminance divided by the mean: TLITE6_1 is
 *    1.33 and AQF072 is 0.87, while every flat that reads as ground is 0.57 or below.
 *
 * ## The order it puts them in
 *
 * Colour family first, saturation second: grey, then brown, then green, then red. The family
 * carries the progression and the saturation carries the step inside it, so the ladder starts
 * on ground that says nothing and ends on ground that is unmistakably blood.
 *
 * Ranking by saturation alone was tried first and does not produce this: it is a single
 * ordering, so it cannot say "brown before green" at all, and on a WAD where one family
 * dominates it simply returns that family. Measured on freedoom1, an even spread over such an
 * ordering picks four browns and no green at all — 25 of the 46 usable flats are brown.
 *
 * **Blue is deliberately not a rung.** Freedoom has two blue flats inside the band and they
 * are the same texture twice; both fail the contrast test anyway. Phase 2 has none at all.
 * A family that one WAD cannot supply and the other supplies once is not a step, it is a hole,
 * so the ladder skips it and spends the step on a third red instead.
 */
object FloorPicker {

    /** How many floors the ladder wants: one per skill level. */
    private val WANTED get() = GameData.skills.size

    /** The band a backdrop has to sit in. Widened only if too few flats qualify. */
    private const val MIN_LUMINANCE = 20.0
    private const val MAX_LUMINANCE = 45.0

    /** Ceiling on any single colour channel. */
    private const val MAX_CHANNEL = 90.0

    /** Ceiling on the texture's own contrast, as a fraction of its mean luminance. */
    private const val MAX_RELATIVE_CONTRAST = 0.60

    /**
     * Below this much colour a flat is grey whatever its hue says. Hue is meaningless on an
     * almost-neutral colour - a channel spread of two can point anywhere around the circle -
     * so the family test has to answer "grey" before it ever looks at the angle.
     */
    private const val NEUTRAL_CHROMA = 8.0

    /**
     * How many rungs each family gets, in the order the ladder travels them. Nine in total,
     * which is [GameData.skills] today; a different skill count rescales these proportionally.
     */
    private val QUOTA = intArrayOf(2, 2, 2, 3)

    /** Grey, brown, green, red — the index into [QUOTA] a flat belongs to, or -1 for none. */
    private fun family(f: Flat): Int = when {
        f.chroma < NEUTRAL_CHROMA -> 0
        f.hue < 25.0 -> 3                       // red, and the wrap-around end of the circle
        f.hue < 55.0 -> 1                       // brown and orange
        f.hue < 170.0 -> 2                      // green
        f.hue < 300.0 -> -1                     // blue: see the class comment
        else -> 3
    }

    class Flat(
        val index: Int,
        val name: String,
        val luminance: Double,
        val chroma: Double,
        val peak: Double,
        /** Standard deviation of per-pixel luminance: how loud the texture is within itself. */
        val contrast: Double,
        /** Degrees around the colour circle, 0 being red. Meaningless when chroma is 0. */
        val hue: Double,
    )

    /**
     * Every flat in the WAD, measured. Public so the reducer can keep exactly what the
     * loader will later choose, rather than guessing at a name list twice.
     */
    fun measure(wad: WadFile): List<Flat> {
        val start = wad.indexOf("F_START")
        val end = wad.indexOf("F_END")
        if (start < 0 || end <= start) return emptyList()

        val out = ArrayList<Flat>()
        for (i in start + 1 until end) {
            if (wad.sizeAt(i) != FLAT_BYTES) continue
            val f = try { wad.decodeFlat(i) } catch (e: Exception) { continue }

            var r = 0L; var g = 0L; var b = 0L
            // Second moment as well as the first, in one pass: the variance is what separates
            // a floor from a field of dots, and a second pass over 4096 pixels to get it would
            // be the same work twice.
            var sum = 0.0; var sumSquares = 0.0
            for (p in f.pixels) {
                val pr = (p shr 16) and 0xFF
                val pg = (p shr 8) and 0xFF
                val pb = p and 0xFF
                r += pr; g += pg; b += pb
                val l = 0.299 * pr + 0.587 * pg + 0.114 * pb
                sum += l; sumSquares += l * l
            }
            val n = f.pixels.size.toDouble()
            val mr = r / n; val mg = g / n; val mb = b / n
            val mean = sum / n
            out += Flat(
                index = i,
                name = wad.nameAt(i),
                luminance = 0.299 * mr + 0.587 * mg + 0.114 * mb,
                chroma = maxOf(mr, mg, mb) - minOf(mr, mg, mb),
                peak = maxOf(mr, mg, mb),
                contrast = kotlin.math.sqrt(maxOf(0.0, sumSquares / n - mean * mean)),
                hue = hueOf(mr, mg, mb),
            )
        }
        return out
    }

    /** Degrees around the colour circle. Standard HSV hue, on the channel means. */
    private fun hueOf(r: Double, g: Double, b: Double): Double {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        if (d == 0.0) return 0.0
        val h = when (max) {
            r -> 60.0 * (((g - b) / d) % 6.0)
            g -> 60.0 * (((b - r) / d) + 2.0)
            else -> 60.0 * (((r - g) / d) + 4.0)
        }
        return if (h < 0) h + 360.0 else h
    }

    /**
     * One flat per skill, calmest first, or fewer when the WAD cannot supply that many.
     *
     * Distinct by construction: every rung comes from a different position in a sorted list,
     * and the families do not overlap.
     */
    fun choose(wad: WadFile): List<Flat> {
        val all = measure(wad)
        if (all.isEmpty()) return emptyList()

        // Widened in steps rather than all at once, so an ordinary WAD is judged by the
        // criteria that were actually reasoned about and an unusual one still gets floors.
        fun inBand(slack: Double) = all.filter {
            it.luminance in (MIN_LUMINANCE - slack)..(MAX_LUMINANCE + slack) &&
                it.peak <= MAX_CHANNEL + slack &&
                it.contrast / it.luminance.coerceAtLeast(1.0) <= MAX_RELATIVE_CONTRAST
        }
        var band = inBand(0.0)
        var slack = 0.0
        while (band.size < WANTED && slack < 60.0) {
            slack += 10.0
            band = inBand(slack)
        }
        if (band.isEmpty()) band = all.sortedBy { it.luminance }.take(WANTED)

        // The quota is written for nine rungs; a shorter or longer ladder keeps the shape.
        val quota = scaleQuota(WANTED)
        val out = ArrayList<Flat>(WANTED)
        for (f in quota.indices) {
            val pool = band.filter { family(it) == f }.sortedBy { it.chroma }
            spread(pool, quota[f], out)
        }

        // A WAD that could not fill a family leaves the ladder short; the remaining steps go
        // to whatever is left, least saturated first, rather than repeating a rung.
        if (out.size < WANTED) {
            for (f in band.sortedBy { it.chroma }) {
                if (out.size >= WANTED) break
                if (out.none { it.index == f.index }) out += f
            }
        }
        return out
    }

    /** [QUOTA] rescaled to a ladder of [wanted] rungs, keeping every family represented. */
    private fun scaleQuota(wanted: Int): IntArray {
        val total = QUOTA.sum()
        if (wanted == total) return QUOTA
        val out = IntArray(QUOTA.size) { (QUOTA[it] * wanted / total).coerceAtLeast(1) }
        // Rounding leaves a remainder either way; it goes on the reds, which is the end of
        // the ladder and the family every WAD has most of.
        out[out.lastIndex] += wanted - out.sum()
        return out
    }

    /**
     * Takes [n] entries spread evenly across [pool], which is already in order.
     *
     * A family holding fewer flats than its quota contributes each of them once rather than
     * the same one twice: a repeated rung would read as the ladder standing still.
     */
    private fun spread(pool: List<Flat>, n: Int, out: MutableList<Flat>) {
        if (pool.isEmpty() || n <= 0) return
        if (n == 1 || pool.size == 1) { out += pool[0]; return }
        for (i in 0 until n) {
            val pick = pool[i * (pool.size - 1) / (n - 1)]
            if (out.none { it.index == pick.index }) out += pick
        }
    }

    /** A flat is 64 by 64 raw palette indices, with no header. */
    const val FLAT_BYTES = 64 * 64
}
