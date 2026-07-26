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
 * flats gets five distinct floors; a WAD with fewer than five gets what it has.
 *
 * The criteria are the ones arrived at by hand when the list was first drawn up, from
 * ranking all 240 flats in Freedoom:
 *
 *  - **Luminance 20 to 45.** A wallpaper sits behind the launcher icons and has to lose that
 *    contest. Brighter competes; darker stops reading as a floor at all.
 *  - **Climbing by hue, not brightness.** All five sit in that same narrow band, so the
 *    contrast behind the icons never changes while the mood does: the most neutral flat opens
 *    the ladder and the most saturated closes it.
 */
object FloorPicker {

    /** How many floors the ladder wants: one per skill level. */
    private val WANTED get() = GameData.skills.size

    /** The band a backdrop has to sit in. Widened only if too few flats qualify. */
    private const val MIN_LUMINANCE = 20.0
    private const val MAX_LUMINANCE = 45.0

    /**
     * Ceiling on any single colour channel.
     *
     * Luminance alone is not enough, and the flat that proved it is AQF035: bright blue
     * panels that measure a luminance of 29 and sit comfortably inside the band, because the
     * formula weights blue at 0.114. On screen it is a glaring blue. A backdrop has to be
     * quiet in every channel, not merely quiet on average.
     */
    private const val MAX_CHANNEL = 90.0

    class Flat(
        val index: Int,
        val name: String,
        val luminance: Double,
        val chroma: Double,
        val peak: Double,
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
            for (p in f.pixels) {
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            val n = f.pixels.size.toDouble()
            val mr = r / n; val mg = g / n; val mb = b / n
            out += Flat(
                index = i,
                name = wad.nameAt(i),
                luminance = 0.299 * mr + 0.587 * mg + 0.114 * mb,
                chroma = maxOf(mr, mg, mb) - minOf(mr, mg, mb),
                peak = maxOf(mr, mg, mb),
            )
        }
        return out
    }

    /**
     * One flat per skill, quietest first, or fewer when the WAD cannot supply that many.
     *
     * Distinct by construction: the result is a subset of a list, never the same entry twice,
     * which is what the name-and-fallback approach could not promise.
     */
    fun choose(wad: WadFile): List<Flat> {
        val all = measure(wad)
        if (all.isEmpty()) return emptyList()

        // Widened in steps rather than all at once, so an ordinary WAD is judged by the
        // criteria that were actually reasoned about and an unusual one still gets floors.
        fun inBand(slack: Double) = all.filter {
            it.luminance in (MIN_LUMINANCE - slack)..(MAX_LUMINANCE + slack) &&
                it.peak <= MAX_CHANNEL + slack
        }
        var band = inBand(0.0)
        var slack = 0.0
        while (band.size < WANTED && slack < 60.0) {
            slack += 10.0
            band = inBand(slack)
        }
        if (band.isEmpty()) band = all.sortedBy { it.luminance }.take(WANTED)

        // Sorted by how coloured they are, then spread across that order: the ladder opens on
        // the most neutral ground the WAD has and closes on its most saturated.
        val ranked = band.sortedBy { it.chroma }
        if (ranked.size <= WANTED) return ranked

        return (0 until WANTED).map { i ->
            ranked[i * (ranked.size - 1) / (WANTED - 1)]
        }
    }

    /** A flat is 64 by 64 raw palette indices, with no header. */
    const val FLAT_BYTES = 64 * 64
}
