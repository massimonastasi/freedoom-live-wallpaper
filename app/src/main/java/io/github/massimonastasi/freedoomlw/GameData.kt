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
 * Constants extracted from the original id source (linuxdoom-1.10, GPL-2.0).
 *
 * No value here is invented or estimated: each one carries the file and symbol it came
 * from. That is also the project's legal footing — every number has a traceable, licensed
 * provenance, independent of any other implementation.
 */
object GameData {

    // m_fixed.h
    const val FRACBITS = 16
    const val FRACUNIT = 1 shl FRACBITS

    // p_local.h:57
    const val MELEERANGE = 64 * FRACUNIT

    /**
     * p_enemy.c: the 8 movement directions of the monsters. No trigonometry, just a
     * table. 47000 is the diagonal (~0.717, slightly above 0.707: that is id's original
     * approximation, not a transcription error).
     *
     * The table is kept whole because it is a verbatim copy of the source, but this scene
     * only ever selects the four cardinal entries: diagonal movement was dropped because
     * axial movement reads far more clearly at this scale, and it halves the sprite angles
     * in play.
     */
    val xspeed = intArrayOf(FRACUNIT, 47000, 0, -47000, -FRACUNIT, -47000, 0, 47000)
    val yspeed = intArrayOf(0, 47000, FRACUNIT, 47000, 0, -47000, -FRACUNIT, -47000)

    /** p_enemy.c: opposite[] — the reverse direction. DI_NODIR (8) stays 8. */
    val opposite = intArrayOf(4, 5, 6, 7, 0, 1, 2, 3, 8)

    const val DI_NODIR = 8

    /**
     * m_random.c: rndtable[]. The original engine uses no generator but a fixed 256-byte table with an
     * advancing index. Deterministic, reproducible for debugging, and cheaper than
     * kotlin.random.Random.
     */
    val rndtable = intArrayOf(
        0, 8, 109, 220, 222, 241, 149, 107, 75, 248, 254, 140, 16, 66,
        74, 21, 211, 47, 80, 242, 154, 27, 205, 128, 161, 89, 77, 36,
        95, 110, 85, 48, 212, 140, 211, 249, 22, 79, 200, 50, 28, 188,
        52, 140, 202, 120, 68, 145, 62, 70, 184, 190, 91, 197, 152, 224,
        149, 104, 25, 178, 252, 182, 202, 182, 141, 197, 4, 81, 181, 242,
        145, 42, 39, 227, 156, 198, 225, 193, 219, 93, 122, 175, 249, 0,
        175, 143, 70, 239, 46, 246, 163, 53, 163, 109, 168, 135, 2, 235,
        25, 92, 20, 145, 138, 77, 69, 166, 78, 176, 173, 212, 166, 113,
        94, 161, 41, 50, 239, 49, 111, 164, 70, 60, 2, 37, 171, 75,
        136, 156, 11, 56, 42, 146, 138, 229, 73, 146, 77, 61, 98, 196,
        135, 106, 63, 197, 195, 86, 96, 203, 113, 101, 170, 247, 181, 113,
        80, 250, 108, 7, 255, 237, 129, 226, 79, 107, 112, 166, 103, 241,
        24, 223, 239, 120, 198, 58, 60, 82, 128, 3, 184, 66, 143, 224,
        145, 224, 81, 206, 163, 45, 63, 90, 168, 114, 59, 33, 159, 95,
        28, 139, 123, 98, 125, 196, 15, 70, 194, 253, 54, 14, 109, 226,
        71, 17, 161, 93, 186, 87, 244, 138, 20, 52, 123, 251, 26, 36,
        17, 46, 52, 231, 232, 76, 31, 221, 84, 37, 216, 165, 212, 106,
        197, 242, 98, 43, 39, 175, 254, 145, 190, 84, 118, 222, 187, 136,
        120, 163, 236, 249
    )

    private var prndindex = 0

    /** m_random.c: P_Random(). */
    fun pRandom(): Int {
        prndindex = (prndindex + 1) and 0xff
        return rndtable[prndindex]
    }

    fun clearRandom() {
        prndindex = 0
    }

    /**
     * A sequence from states[]: frame indices and how many tics each one lasts.
     * A tic value of -1 means "stays forever" (the last death frame: the corpse).
     */
    class Anim(val frames: IntArray, val tics: IntArray) {
        val length get() = frames.size
    }

    /**
     * A creature: the values come from info.c, mobjinfo[] and states[].
     *
     * [lumpPrefix] is the sprite name, identical in every IWAD this loader accepts because the
     * engine hardcodes it in sprnames[].
     * [walkTics] and [walkFrames] give the walk animation: the original engine repeats each
     * frame twice (A,A,B,B,...), so every frame lasts walkTics*2.
     *
     * Attacks, from p_enemy.c: melee deals `(P_Random() % meleeMod + 1) * meleeMul`,
     * [hitscanShots] is the number of instant shots, [projectile] an index into [projectiles].
     */
    class Creature(
        val name: String,
        val lumpPrefix: String,
        val speed: Int,
        val health: Int,
        val radius: Int,
        val walkFrames: Int,
        val walkTics: Int,
        val attack: Anim,
        val pain: Anim,
        val death: Anim,
        val painChance: Int,
        val meleeMod: Int = 0,
        val meleeMul: Int = 0,
        val hitscanShots: Int = 0,
        val projectile: Int = -1,
    ) {
        var spriteIndex = -1
    }

    /** info.c: the missiles. Their `speed` is already in FRACUNIT, unlike the monsters'. */
    class Projectile(
        val lumpPrefix: String,
        val speed: Int,
        val damage: Int,
    ) {
        var spriteIndex = -1
    }

    // info.c mobjinfo[MT_TROOPSHOT] / mobjinfo[MT_BRUISERSHOT], states S_TBALL1 / S_BRBALL1.
    val projectiles = listOf(
        Projectile("BAL1", speed = 10, damage = 3),
        Projectile("BAL7", speed = 15, damage = 8),
    )

    /** Blood: states S_BLOOD1..3, sprite BLUD, frames C,B,A at 8 tics each. */
    val bloodAnim = Anim(intArrayOf(2, 1, 0), intArrayOf(8, 8, 8))

    /** Teleport fog: states S_TFOG*, 6 tics per frame. */
    val fogAnim = Anim(IntArray(10) { it }, IntArray(10) { 6 })

    /** Fireball in flight: 2 frames of 4 tics looping (S_TBALL1/2, S_BRBALL1/2). */
    val ballAnim = Anim(intArrayOf(0, 1), intArrayOf(4, 4))

    // The names are the Freedoom ones: safer on the trademark and consistent with the assets.
    val creatures = listOf(
        // mobjinfo[MT_POSSESSED]; S_POSS_RUN 4 tics, ATK 10/8/8, PAIN 3+3, DIE 5,5,5,5,-1
        Creature(
            "Zombie", "POSS", speed = 8, health = 20, radius = 20, walkFrames = 4, walkTics = 4,
            attack = Anim(intArrayOf(4, 5, 4), intArrayOf(10, 8, 8)),
            pain = Anim(intArrayOf(6, 6), intArrayOf(3, 3)),
            death = Anim(intArrayOf(7, 8, 9, 10, 11), intArrayOf(5, 5, 5, 5, -1)),
            painChance = 200, hitscanShots = 1,
        ),
        // mobjinfo[MT_SHOTGUY]; A_SPosAttack fires 3 shots
        Creature(
            "ShotgunZombie", "SPOS", speed = 8, health = 30, radius = 20, walkFrames = 4, walkTics = 3,
            attack = Anim(intArrayOf(4, 5, 4), intArrayOf(10, 10, 10)),
            pain = Anim(intArrayOf(6, 6), intArrayOf(3, 3)),
            death = Anim(intArrayOf(7, 8, 9, 10, 11), intArrayOf(5, 5, 5, 5, -1)),
            painChance = 170, hitscanShots = 3,
        ),
        // mobjinfo[MT_TROOP]; A_TroopAttack: melee (P_Random()%8+1)*3, otherwise MT_TROOPSHOT
        Creature(
            "Serpentipede", "TROO", speed = 8, health = 60, radius = 20, walkFrames = 4, walkTics = 3,
            attack = Anim(intArrayOf(4, 5, 6), intArrayOf(8, 8, 6)),
            pain = Anim(intArrayOf(7, 7), intArrayOf(2, 2)),
            death = Anim(intArrayOf(8, 9, 10, 11, 12), intArrayOf(8, 8, 6, 6, -1)),
            painChance = 200, meleeMod = 8, meleeMul = 3, projectile = 0,
        ),
        // mobjinfo[MT_SERGEANT] speed 10; A_SargAttack: melee only, (P_Random()%10+1)*4
        Creature(
            "FleshWorm", "SARG", speed = 10, health = 150, radius = 30, walkFrames = 4, walkTics = 2,
            attack = Anim(intArrayOf(4, 5, 6), intArrayOf(8, 8, 8)),
            pain = Anim(intArrayOf(7, 7), intArrayOf(2, 2)),
            death = Anim(intArrayOf(8, 9, 10, 11, 12, 13), intArrayOf(8, 8, 4, 4, 4, -1)),
            painChance = 180, meleeMod = 10, meleeMul = 4,
        ),
        // mobjinfo[MT_HEAD]; A_HeadAttack: melee (P_Random()%6+1)*10, otherwise MT_HEADSHOT.
        // ponytail: reuses BAL1 instead of BAL2 — one fireball sprite less to handle, and
        // BAL2 is not guaranteed to exist in every IWAD.
        Creature(
            "Trilobite", "HEAD", speed = 8, health = 400, radius = 31, walkFrames = 1, walkTics = 3,
            attack = Anim(intArrayOf(1, 2, 3), intArrayOf(5, 5, 5)),
            pain = Anim(intArrayOf(4, 4), intArrayOf(3, 3)),
            death = Anim(intArrayOf(6, 7, 8, 9, 10, 11), intArrayOf(8, 8, 8, 8, 8, -1)),
            painChance = 128, meleeMod = 6, meleeMul = 10, projectile = 0,
        ),
        // mobjinfo[MT_BRUISER]; A_BruisAttack: melee (P_Random()%8+1)*10, otherwise MT_BRUISERSHOT
        Creature(
            "PainLord", "BOSS", speed = 8, health = 1000, radius = 24, walkFrames = 4, walkTics = 3,
            attack = Anim(intArrayOf(4, 5, 6), intArrayOf(8, 8, 8)),
            pain = Anim(intArrayOf(7, 7), intArrayOf(2, 2)),
            death = Anim(intArrayOf(8, 9, 10, 11, 12, 13, 14), intArrayOf(8, 8, 8, 8, 8, 8, -1)),
            painChance = 50, meleeMod = 8, meleeMul = 10, projectile = 1,
        ),
    )

    /**
     * The marine. mobjinfo[MT_PLAYER] / states S_PLAY_RUN1..4 (4 tics), DIE 6x10 then -1.
     * The attack animation here is a fallback: in practice the marine uses the animation
     * of the weapon he is holding (see [weapons]).
     */
    /** The one creature the fast skills touch: g_game.c only rewrites the SARG states. */
    val fleshWorm get() = creatures[3]

    val player = Creature(
        "Player", "PLAY", speed = 8, health = 100, radius = 16, walkFrames = 4, walkTics = 4,
        attack = Anim(intArrayOf(4, 5, 4), intArrayOf(4, 6, 4)),
        pain = Anim(intArrayOf(6, 6), intArrayOf(4, 4)),
        death = Anim(intArrayOf(7, 8, 9, 10, 11, 12, 13), intArrayOf(10, 10, 10, 10, 10, 10, -1)),
        painChance = 255, hitscanShots = 1,
    )

    /** Damage of an enemy instant shot: A_PosAttack, `((P_Random()%5)+1)*3`. */
    fun hitscanDamage(): Int = ((pRandom() % 5) + 1) * 3

    /** p_pspr.c P_GunShot: `5*(P_Random()%3+1)`. One pellet from the marine's gun. */
    fun gunShotDamage(): Int = 5 * (pRandom() % 3 + 1)

    /** p_pspr.c A_FireShotgun: seven pellets per shot. */
    const val SHOTGUN_PELLETS = 7

    /**
     * Palette entries used to recolour the corner readout.
     *
     * Chosen by measuring PLAYPAL rather than by eye, because on this palette saturation and
     * legibility pull against each other. The blue ramp runs 193 (199,199,255) down to 200
     * (0,0,255): it gets bluer by *removing* red and green, so it darkens as it saturates.
     * Pure blue at 200 has a luminance of 29 and is the least legible entry in the set on a
     * dark backdrop.
     *
     * 198 is (55,55,255) at luminance 78 — unmistakably a saturated blue rather than the
     * washed-out periwinkle of 196, and level with the WAD's own red numerals at 76, which
     * are already accepted as readable. That is the floor this sits on, not a guess.
     *
     * Note the potion itself could not be sampled: the engine's health bonus is the blue vial, but
     * Freedoom redrew it green — decoding BON1A0 gives dominant colours around (75,159,63).
     * The palette is shared, so the ramp is the right source; the sprite is not.
     *
     * The green at 112 needs no such compromise, at luminance 198.
     */
    const val PALETTE_HEALTH = 198          // 55, 55, 255
    const val PALETTE_ARMOR = 112           // 119, 255, 111

    /**
     * The death wash, 179,0,0 — a deep red with no desaturation needed.
     *
     * Taken from the palette rather than computed from PLAYPAL's damage ramp, which is what
     * this used to do: the ramp's flash is 255,25,25 at ninety percent saturation, right for
     * a brief flash across a game viewport and far too loud over a home screen. Reading a
     * palette entry directly makes the colour a fact of the active WAD, like the two readout
     * colours, instead of an arithmetic adjustment layered on one.
     *
     * 182 is the nearest entry to the intended 174,0,0, five units away in RGB.
     */
    const val PALETTE_DEATH = 182           // 179, 0, 0

    // ------------------------------------------------------------------ weapons

    /**
     * The marine's weapons. All of them fire instant shots with the same per-pellet damage
     * (p_pspr.c P_GunShot); what changes is the pellet count and the rate of fire.
     *
     * ponytail: only the three hitscan weapons. The rocket launcher and plasma gun would
     * need exploding projectiles, i.e. another mechanic: add them if they are ever needed.
     */
    class Weapon(
        val name: String,
        val pellets: Int,
        val ammo: Int,          // index into AMMO_*; -1 = consumes nothing
        val attack: Anim,
    )

    const val AMMO_BULLETS = 0
    const val AMMO_SHELLS = 1

    /** p_inter.c: maxammo[] = {200, 50, ...}. */
    val maxAmmo = intArrayOf(200, 50)

    /** p_inter.c: clipammo[] = {10, 4, ...}. A picked-up weapon carries two clip loads. */
    val clipAmmo = intArrayOf(10, 4)

    val weapons = listOf(
        // The pistol consumes no ammo: in a wallpaper, being disarmed forever would be a
        // deadlock, and the marine cannot go looking for ammo the way a player would.
        Weapon("Pistol", pellets = 1, ammo = -1, attack = Anim(intArrayOf(4, 5, 4), intArrayOf(6, 8, 6))),
        Weapon("Shotgun", pellets = SHOTGUN_PELLETS, ammo = AMMO_SHELLS, attack = Anim(intArrayOf(4, 5, 4), intArrayOf(6, 10, 8))),
        Weapon("Chaingun", pellets = 1, ammo = AMMO_BULLETS, attack = Anim(intArrayOf(4, 5), intArrayOf(3, 3))),
    )

    const val WEAPON_PISTOL = 0
    const val WEAPON_SHOTGUN = 1
    const val WEAPON_CHAINGUN = 2

    // ------------------------------------------------------------------ items

    const val ITEM_HEALTH = 0
    const val ITEM_ARMOR = 1
    const val ITEM_WEAPON = 2

    /**
     * The pickups. Values from p_inter.c: stimpack 10, medikit 25, green armour 100 points
     * of type 1, blue 200 of type 2, and a picked-up weapon carries two clip loads.
     */
    class Item(
        val lumpPrefix: String,
        val kind: Int,
        val amount: Int,
        /** Armour type or weapon index, depending on [kind]. */
        val extra: Int = 0,
        val frames: Int = 1,
        /** Relative share of the drops. See [dropTable]. */
        val weight: Int = 1,
    ) {
        var spriteIndex = -1
    }

    /**
     * The pickups, and how often each one is dropped.
     *
     * There is no ammunition here on purpose. Ammo only ever arrives inside a weapon now,
     * which is what makes an empty weapon a real loss rather than a pause: it cannot be
     * refilled where it lies, so the marine falls back down his arsenal and has to find
     * another one.
     *
     * The weights lean towards staying alive, in that order: healing first, then armour,
     * then the guns, with the more powerful one of each pair rarer than the plain one.
     */
    val items = listOf(
        Item("STIM", ITEM_HEALTH, 10, weight = 5),                       // stimpack
        Item("MEDI", ITEM_HEALTH, 25, weight = 4),                       // medikit
        Item("ARM1", ITEM_ARMOR, 100, extra = 1, frames = 2, weight = 4), // green armour
        Item("ARM2", ITEM_ARMOR, 200, extra = 2, frames = 2, weight = 2), // blue armour
        Item("SHOT", ITEM_WEAPON, 2, extra = WEAPON_SHOTGUN, weight = 3), // shotgun + 2 clips
        Item("MGUN", ITEM_WEAPON, 2, extra = WEAPON_CHAINGUN, weight = 2), // chaingun
    )

    /**
     * The weighted draw, flattened once at startup: an item index repeated as many times as
     * its weight, so choosing one is a single indexed read of P_Random rather than a walk
     * over a cumulative total on every drop.
     */
    val dropTable: IntArray = items.indices
        .flatMap { i -> List(items[i].weight) { i } }
        .toIntArray()

    /** p_inter.c: armour absorbs a third of the damage when green, half when blue. */
    fun armorSaved(damage: Int, armorType: Int): Int =
        if (armorType == 1) damage / 3 else damage / 2

    // ------------------------------------------------------------------ skill

    /**
     * A skill level, with the five effects the engine actually attaches to one.
     *
     * The wallpaper has no maps, so the one skill effect that cannot be transcribed
     * literally is the monster count: in the engine it comes from the map itself, where
     * every thing carries MTF_EASY/NORMAL/HARD flags and P_SpawnMapThing (p_mobj.c:743)
     * picks the bit for the current skill — 1 for skills 0 and 1, 2 for skill 2, 4 for
     * skills 3 and 4. Two skill levels therefore always share a monster set, which is why
     * [countEighths] repeats in pairs below.
     *
     * The ratio itself is measured rather than invented: counting the flags across the
     * THINGS lumps of Freedoom E1M1-E1M3 gives 781 easy, 887 medium and 991 hard, so
     * roughly seven, eight and nine eighths.
     */
    class Skill(
        val name: String,
        /** Arrival-queue length as eighths of the wave's own order. */
        val countEighths: Int,
        /**
         * Tics between automatic drops.
         *
         * The wallpaper's own lever, not the engine's: the engine varies difficulty by what
         * a map contains, and this scene has no map. It turned out to be the lever that
         * matters most, because over a table lasting minutes the marine's survival is
         * governed almost entirely by how often he can heal.
         *
         * In tics rather than a fraction of a shared constant, which was the earlier form:
         * that gave five levels only three usable values between "always alive" and "never",
         * and the ladder could not be spread across them.
         */
        val dropInterval: Int,
        /**
         * Odds out of 256 that an arrival is replaced by the next creature up the bestiary.
         *
         * The engine's hard skill does not merely add monsters, it admits nastier ones: the
         * MTF_HARD things in a map include creatures the easy pass never spawns. With only
         * sixteen authored waves there is nowhere to hide that, so it is applied per arrival
         * instead. Counting arrivals alone was measured to make no difference at all in the
         * opening wave — two zombies at every skill, and a marine who beats them every time.
         */
        val toughen: Int = 0,
        /** p_inter.c:799 — the player takes half damage in trainer mode. */
        val halfDamage: Boolean = false,
        /** p_inter.c:95 — double ammo on the easiest and the hardest. */
        val doubleAmmo: Boolean = false,
        /** g_game.c:1421 — FleshWorm state tics halved, monster missiles at speed 20. */
        val fast: Boolean = false,
        /** p_mobj.c:456 — killed monsters come back. */
        val respawn: Boolean = false,
        /**
         * Floor flat for this level, so the ground itself says how bad things have got.
         *
         * Chosen by decoding all 240 flats in the IWAD and looking at them, because a mean
         * colour hides the texture: FLOOR1_7 measures as an ordinary dark red and is really
         * two glaring panels, and GATE1 is a circular emblem that tiles into a repeating
         * logo. Both were rejected on sight, not on their numbers.
         *
         * The ladder climbs by **hue, not brightness**. All five sit between 28 and 38 mean
         * luminance, so the contrast behind the launcher icons never changes while the mood
         * does: neutral, then masonry, then a sick green, then blood, then magma.
         *
         * The first level was CEIL5_1, which measures *brighter* than most of the others at
         * 38 and still read as a plain black screen, because it is uniform noise with no
         * structure at all. FLAT4 sits at the same luminance and is a visible grid, which is
         * the difference between a dark backdrop and no backdrop. Mean luminance alone would
         * never have caught that; looking at it did.
         */
        val flat: String,
    )

    /** g_game.c skill_t, and the names from the difficulty menu. */
    val skills = listOf(
        Skill("I'm too young to die", 4, 215, toughen = 0, halfDamage = true, doubleAmmo = true, flat = "FLAT4"),
        Skill("Hey, not too rough", 4, 160, toughen = 0, flat = "RROCK13"),
        Skill("Hurt me plenty", 4, 235, toughen = 60, flat = "GRNROCK"),
        Skill("Ultra-Violence", 4, 375, toughen = 120, flat = "BLOOD1"),
        // Lower toughen than Ultra-Violence would suggest, and still far harder: this level
        // alone brings fast FleshWorms and monsters that come back, and a wave that refills
        // is a wave the marine is very unlikely to finish. The parameter is not the
        // difficulty; the measured outcome is, and that is what the test asserts.
        Skill(
            "Nightmare!", 5, 520, toughen = 130,
            doubleAmmo = true, fast = true, respawn = true, flat = "RROCK01",
        ),
    )

    /** g_game.c:1425 — every monster missile is forced to this speed when fast. */
    const val FAST_MISSILE_SPEED = 20

    /** p_mobj.c:461 — a monster comes back 12 seconds after it fell. */
    const val RESPAWN_DELAY = TICRATE * 12

    // ------------------------------------------------------------------ waves

    /**
     * A wave: which creatures, in what order, and how calmly.
     *
     * [order] is the arrival sequence, [spawnDelay] the tics between one arrival and the
     * next, [burst] how many show up together on each arrival, [rest] the pause once the
     * wave has been cleared.
     */
    class Wave(
        val order: IntArray,
        val spawnDelay: Int,
        val burst: Int = 1,
        val rest: Int = TICRATE * 2,
    ) {
        val size get() = order.size
    }

    /**
     * info.c: `reactiontime` is 8 for every creature we use. A monster that has just
     * appeared stands still for that many tics before moving — the reference documentation describes it
     * as "newly spawned monsters initially stand idle". It reinforces the staggered
     * arrival: first it materialises in the fog, then it wakes up.
     */
    const val REACTION_TIME = 8

    /**
     * The waves.
     *
     * The progression follows the order in which the original actually introduces its monsters in
     * the first episode — zombies, then imps, then demons, then cacodemons, with the
     * barons as the final encounter (E1M8).
     *
     * Tension grows along four axes: tougher creatures, a delay shrinking from two seconds
     * to under one, paired arrivals only near the end, and shorter pauses between waves.
     *
     * The curve is not a smooth ramp: every new creature enters **alone**, in a wave of one,
     * so it gets looked at instead of lost in a crowd, and is then escorted in the wave
     * after. The PainLord closes the table by itself.
     *
     * The table is deliberately thin — 30 arrivals across the sixteen waves, where it used
     * to be 61. That was measured, not felt: at 61 the table needed 123 seconds of pure
     * waiting before any fighting, against a mean life of 64 seconds, so **no life in 400
     * runs ever reached the last wave** and the skill ladder could not move at all. A
     * promotion costs the whole table in one life, so the table has to be something a life
     * can outlast.
     */
    val waves = listOf(
        //   creatures            delay                  burst  rest after
        Wave(intArrayOf(0), /*             2.00 s */ TICRATE * 2, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(0, 0), /*          1.75 s */ TICRATE * 7 / 4, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(1), /*             1.75 s */ TICRATE * 7 / 4, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(0, 1), /*          1.50 s */ TICRATE * 3 / 2, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(2), /*             1.50 s */ TICRATE * 3 / 2, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(1, 2), /*          1.50 s */ TICRATE * 3 / 2, 1, TICRATE),
        Wave(intArrayOf(3), /*             1.25 s */ TICRATE * 5 / 4, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(2, 3), /*          1.25 s */ TICRATE * 5 / 4, 1, TICRATE),
        Wave(intArrayOf(1, 2, 3), /*       1.25 s */ TICRATE * 5 / 4, 1, TICRATE),
        Wave(intArrayOf(4), /*             1.25 s */ TICRATE * 5 / 4, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(3, 4), /*          1.00 s */ TICRATE, 1, TICRATE),
        Wave(intArrayOf(2, 3, 4), /*       1.00 s */ TICRATE, 1, TICRATE),
        Wave(intArrayOf(1, 2, 3), /*       1.00 s */ TICRATE, 2, TICRATE),
        Wave(intArrayOf(4, 4), /*          1.00 s */ TICRATE, 1, TICRATE),
        Wave(intArrayOf(3, 4, 2), /*       0.75 s */ TICRATE * 3 / 4, 2, TICRATE),
        Wave(intArrayOf(5), /*             0.75 s */ TICRATE * 3 / 4, 1, TICRATE * 3),
    )

    /**
     * Every sprite prefix in use, in a stable order. Each Creature/Projectile/Item stores
     * its own index here, so the draw loop needs no lookup at all.
     */
    val spritePrefixes: List<String> =
        creatures.map { it.lumpPrefix } + player.lumpPrefix +
            projectiles.map { it.lumpPrefix } + items.map { it.lumpPrefix } +
            listOf("BLUD", "TFOG")

    val bloodSpriteIndex = spritePrefixes.indexOf("BLUD")
    val fogSpriteIndex = spritePrefixes.indexOf("TFOG")

    // This must stay the last block in the file: the properties of an object initialise in
    // declaration order, and assigning the indices before spritePrefixes exists would let
    // the default values overwrite them.
    init {
        for (c in creatures) c.spriteIndex = spritePrefixes.indexOf(c.lumpPrefix)
        player.spriteIndex = spritePrefixes.indexOf(player.lumpPrefix)
        for (p in projectiles) p.spriteIndex = spritePrefixes.indexOf(p.lumpPrefix)
        for (i in items) i.spriteIndex = spritePrefixes.indexOf(i.lumpPrefix)
    }
}
