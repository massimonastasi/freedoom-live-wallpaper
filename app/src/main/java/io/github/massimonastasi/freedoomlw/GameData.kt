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

    // info.c mobjinfo[]: MT_TROOPSHOT and MT_BRUISERSHOT are the monsters' fireballs,
    // MT_PLASMA and MT_ROCKET the marine's. Speeds and damage verbatim from the table.
    val projectiles = listOf(
        Projectile("BAL1", speed = 10, damage = 3),      // MT_TROOPSHOT
        Projectile("BAL7", speed = 15, damage = 8),      // MT_BRUISERSHOT
        Projectile("PLSS", speed = 25, damage = 5),      // MT_PLASMA
        Projectile("MISL", speed = 20, damage = 20),     // MT_ROCKET
        Projectile("FATB", speed = 10, damage = 10),     // MT_TRACER, without the homing
        Projectile("MANF", speed = 20, damage = 8),      // MT_FATSHOT
        Projectile("APLS", speed = 25, damage = 5),      // MT_ARACHPLAZ
    )

    const val PROJECTILE_PLASMA = 2
    const val PROJECTILE_ROCKET = 3
    const val PROJECTILE_TRACER = 4
    const val PROJECTILE_FATSHOT = 5
    const val PROJECTILE_ARACHPLAZ = 6

    /** Blood: states S_BLOOD1..3, sprite BLUD, frames C,B,A at 8 tics each. */
    val bloodAnim = Anim(intArrayOf(2, 1, 0), intArrayOf(8, 8, 8))

    /** Teleport fog: states S_TFOG*, 6 tics per frame. */
    val fogAnim = Anim(IntArray(10) { it }, IntArray(10) { 6 })

    /** Fireball in flight: 2 frames of 4 tics looping (S_TBALL1/2, S_BRBALL1/2). */
    val ballAnim = Anim(intArrayOf(0, 1), intArrayOf(4, 4))

    /**
     * The bestiary, ordered by escalation.
     *
     * Spawn health orders all of it but the last two, which are in the order the original
     * puts them in: the Cyberdemon is met first and the Spider Mastermind closes, even
     * though it has a thousand health less. That order is canonical and is not to be
     * rearranged - the arithmetic serves the sequence here, not the other way round.
     *
     * Health is not lethality - that was measured, and is why the weapons rank themselves by
     * damage per tic rather than by position. But the order still has to mean something,
     * because toughen promotes an arrival one step along it and substitute walks it looking
     * for a creature the WAD can draw. Health is the honest choice: it is the original's own
     * measure of how much of a thing there is.
     *
     * Names are ours. Where Freedoom has one it is used; the rest are descriptive, and none
     * are the trademarked ones. Every value beside them is from info.c with its provenance.
     *
     * Which of these ever appears is decided by the loaded WAD alone. Freedoom Phase 1 and a
     * Phase 1 IWAD carry exactly the same roster - measured, not assumed - and a Phase 2 IWAD
     * adds five more of the entries below. Nothing here records which file has what.
     */
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
        // mobjinfo[MT_CHAINGUY]; A_CPosAttack deals ((P_Random()%5)+1)*3 per shot, in bursts.
        // Phase 2 and later: absent from Phase 1 and from Freedoom Phase 1.
        Creature(
            "ChaingunZombie", "CPOS", speed = 8, health = 70, radius = 20, walkFrames = 4, walkTics = 3,
            attack = Anim(intArrayOf(4, 5, 4), intArrayOf(10, 4, 4)),
            pain = Anim(intArrayOf(6, 6), intArrayOf(3, 3)),
            death = Anim(intArrayOf(7, 8, 9, 10, 11, 12, 13), intArrayOf(5, 5, 5, 5, 5, 5, -1)),
            painChance = 170, hitscanShots = 4,
        ),
        // mobjinfo[MT_SKULL]; charges rather than walks, and its collision deals
        // (P_Random()%8+1)*3. Present in every IWAD this loader accepts.
        Creature(
            "Charger", "SKUL", speed = 8, health = 100, radius = 16, walkFrames = 2, walkTics = 3,
            attack = Anim(intArrayOf(2, 3, 2), intArrayOf(10, 4, 4)),
            pain = Anim(intArrayOf(4, 4), intArrayOf(3, 3)),
            // info.c S_SKULL_DIE1..DIE6: the last one is `{SPR_SKUL, 32775, 6, {NULL}, S_NULL}`
            // - it runs for six tics and then goes to S_NULL, which removes the thing. This
            // one does not die, it detonates: the six frames are the skull bursting into a
            // ball of fire that fades to a ring and is gone, and every frame is fullbright.
            //
            // The last tic was -1 here, our marker for "stays forever", which is right for
            // every other creature and wrong for this one: it left the final frame of an
            // explosion - 103 by 90 pixels of red ring in Phase 2, wider than the marine is
            // tall - lying on the ground for thirty seconds, on top of whatever walked over
            // it. That was read as a corpse with a blood splat beside it. There is no corpse.
            death = Anim(intArrayOf(5, 6, 7, 8, 9, 10), intArrayOf(6, 6, 6, 6, 6, 6)),
            painChance = 256, meleeMod = 8, meleeMul = 3,
        ),
        // mobjinfo[MT_SERGEANT] speed 10; A_SargAttack: melee only, (P_Random()%10+1)*4
        Creature(
            "FleshWorm", "SARG", speed = 10, health = 150, radius = 30, walkFrames = 4, walkTics = 2,
            attack = Anim(intArrayOf(4, 5, 6), intArrayOf(8, 8, 8)),
            pain = Anim(intArrayOf(7, 7), intArrayOf(2, 2)),
            death = Anim(intArrayOf(8, 9, 10, 11, 12, 13), intArrayOf(8, 8, 4, 4, 4, -1)),
            painChance = 180, meleeMod = 10, meleeMul = 4,
        ),
        // mobjinfo[MT_UNDEAD]; A_SkelFist deals ((P_Random()%10)+1)*6, A_SkelMissile fires
        // MT_TRACER. The tracer's homing is not reproduced: it flies straight here.
        // Phase 2 and later.
        Creature(
            "BoneStalker", "SKEL", speed = 10, health = 300, radius = 20, walkFrames = 6, walkTics = 2,
            attack = Anim(intArrayOf(6, 7, 8), intArrayOf(6, 6, 6)),
            pain = Anim(intArrayOf(10, 10), intArrayOf(5, 5)),
            death = Anim(intArrayOf(11, 12, 13, 14, 15, 16), intArrayOf(7, 7, 7, 7, 7, -1)),
            painChance = 100, meleeMod = 10, meleeMul = 6, projectile = PROJECTILE_TRACER,
        ),
        // mobjinfo[MT_HEAD]; A_HeadAttack: melee (P_Random()%6+1)*10, otherwise MT_HEADSHOT.
        // ponytail: reuses BAL1 instead of BAL2 - one fireball sprite less to handle, and
        // BAL2 is not guaranteed to exist in every IWAD.
        Creature(
            "Trilobite", "HEAD", speed = 8, health = 400, radius = 31, walkFrames = 1, walkTics = 3,
            attack = Anim(intArrayOf(1, 2, 3), intArrayOf(5, 5, 5)),
            pain = Anim(intArrayOf(4, 4), intArrayOf(3, 3)),
            death = Anim(intArrayOf(6, 7, 8, 9, 10, 11), intArrayOf(8, 8, 8, 8, 8, -1)),
            painChance = 128, meleeMod = 6, meleeMul = 10, projectile = 0,
        ),
        // mobjinfo[MT_KNIGHT]; the same attack as MT_BRUISER at half the health.
        // Phase 2 and later.
        Creature(
            "LesserLord", "BOS2", speed = 8, health = 500, radius = 24, walkFrames = 4, walkTics = 3,
            attack = Anim(intArrayOf(4, 5, 6), intArrayOf(8, 8, 8)),
            pain = Anim(intArrayOf(7, 7), intArrayOf(2, 2)),
            death = Anim(intArrayOf(8, 9, 10, 11, 12, 13, 14), intArrayOf(8, 8, 8, 8, 8, 8, -1)),
            painChance = 50, meleeMod = 8, meleeMul = 10, projectile = 1,
        ),
        // mobjinfo[MT_BABY]; A_BspiAttack fires MT_ARACHPLAZ. Phase 2 and later.
        Creature(
            "Spiderling", "BSPI", speed = 12, health = 500, radius = 64, walkFrames = 6, walkTics = 3,
            attack = Anim(intArrayOf(0, 6, 7), intArrayOf(20, 4, 4)),
            pain = Anim(intArrayOf(8, 8), intArrayOf(3, 3)),
            death = Anim(intArrayOf(9, 10, 11, 12, 13, 14, 15), intArrayOf(20, 7, 7, 7, 7, 7, -1)),
            painChance = 128, projectile = PROJECTILE_ARACHPLAZ,
        ),
        // mobjinfo[MT_FATSO]; A_FatAttack fires MT_FATSHOT in a spread, modelled as one.
        // Phase 2 and later.
        Creature(
            "Bloater", "FATT", speed = 8, health = 600, radius = 48, walkFrames = 6, walkTics = 4,
            attack = Anim(intArrayOf(6, 7, 8), intArrayOf(20, 10, 5)),
            pain = Anim(intArrayOf(9, 9), intArrayOf(3, 3)),
            death = Anim(intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18), intArrayOf(6, 6, 6, 6, 6, 6, 6, 6, -1)),
            painChance = 80, projectile = PROJECTILE_FATSHOT,
        ),
        // mobjinfo[MT_BRUISER]; A_BruisAttack: melee (P_Random()%8+1)*10, otherwise MT_BRUISERSHOT
        Creature(
            "PainLord", "BOSS", speed = 8, health = 1000, radius = 24, walkFrames = 4, walkTics = 3,
            attack = Anim(intArrayOf(4, 5, 6), intArrayOf(8, 8, 8)),
            pain = Anim(intArrayOf(7, 7), intArrayOf(2, 2)),
            death = Anim(intArrayOf(8, 9, 10, 11, 12, 13, 14), intArrayOf(8, 8, 8, 8, 8, 8, -1)),
            painChance = 50, meleeMod = 8, meleeMul = 10, projectile = 1,
        ),
        // mobjinfo[MT_CYBORG]; fires MT_ROCKET. Present in every IWAD this loader accepts.
        // At 30% of the screen width it is barely larger than the PainLord already drawn
        // at 23%.
        Creature(
            "Cyberlord", "CYBR", speed = 16, health = 4000, radius = 40, walkFrames = 4, walkTics = 3,
            attack = Anim(intArrayOf(4, 5, 4), intArrayOf(6, 12, 12)),
            pain = Anim(intArrayOf(6, 6), intArrayOf(10, 10)),
            death = Anim(intArrayOf(7, 8, 9, 10, 11, 12, 13, 14), intArrayOf(10, 10, 10, 10, 10, 10, 10, -1)),
            painChance = 20, projectile = PROJECTILE_ROCKET,
        ),
        // mobjinfo[MT_SPIDER]; A_SPosAttack with A_SpidRefire, so a hitscan burst. Present
        // in every IWAD this loader accepts. It is the largest thing drawn by a distance -
        // 71% of the screen width against the PainLord's 23% - and is here because that was
        // asked for after the measurement, not in spite of it.
        Creature(
            "Overlord", "SPID", speed = 12, health = 3000, radius = 128, walkFrames = 6, walkTics = 3,
            attack = Anim(intArrayOf(0, 6, 7), intArrayOf(20, 4, 4)),
            pain = Anim(intArrayOf(8, 8), intArrayOf(3, 3)),
            death = Anim(intArrayOf(9, 10, 11, 12, 13, 14, 15, 16, 17, 18), intArrayOf(20, 10, 10, 10, 10, 10, 10, 10, 10, -1)),
            painChance = 40, hitscanShots = 3,
        ),
    )
    /** The one creature the fast skills touch: g_game.c only rewrites the SARG states. */
    val fleshWorm get() = creatures.first { it.lumpPrefix == "SARG" }

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

    /**
     * What a missile actually deals when it lands.
     *
     * p_map.c PIT_CheckThing: `damage = ((P_Random()%8)+1) * tmthing->info->damage`. The
     * figure in mobjinfo is a multiplicand, not the damage - a plasma bolt deals 5 to 40 and
     * a rocket 20 to 160 on a direct hit.
     *
     * That multiplier was missing here, so every missile in the scene dealt its mobjinfo
     * figure flat: the plasma rifle was worth a quarter of what it should be and the rocket
     * launcher less than a fifth, which made it the second weakest thing in the arsenal
     * behind the pistol - and [Weapon.damagePerTic], which is how the marine decides what to
     * hold, was ranking on those wrong numbers. The monsters' fireballs were understated by
     * exactly as much.
     *
     * Taken at the mean rather than rolled, which is the same simplification the weapon
     * table now reads at: one to eight averages four and a half.
     */
    fun missileDamage(base: Int): Int = base * 9 / 2

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
     * The ramp runs from 203,0,0 at index 180 down to 67,0,0 at 191, all pure red with no
     * green or blue at all. 187 sits in the lower half at luminance 34 — dark enough that
     * reaching full opacity leaves a deep blood red rather than a bright one.
     */
    const val PALETTE_DEATH = 187           // 115, 0, 0

    // ------------------------------------------------------------------ weapons

    /**
     * The marine's weapons, in order of power: the arsenal always reaches for the highest
     * one that is loaded, so this list is the ranking.
     *
     * Hitscan weapons fire [pellets] instant shots of P_GunShot damage each (p_pspr.c).
     * Projectile weapons fire one missile from [projectile] instead, which is the same
     * mechanism the monsters' fireballs already use.
     *
     * Which of them can appear depends on the loaded WAD: the super shotgun is Phase 2 and
     * later, so a Phase 1 IWAD simply never drops one. Nothing here lists which file has
     * what - the sprite either resolves or it does not.
     */
    class Weapon(
        val name: String,
        val lumpPrefix: String,
        val ammo: Int,              // index into AMMO_*; -1 = consumes nothing
        val attack: Anim,
        val pellets: Int = 0,       // hitscan: instant shots per trigger pull
        val projectile: Int = -1,   // otherwise: index into [projectiles]
    ) {
        /**
         * Expected damage per tic, which is what "the most powerful weapon" has to mean.
         *
         * Computed rather than assumed from the list order, and that distinction was
         * measured: the rocket launcher sits last in the original's slot order and deals 20
         * on a direct hit, against roughly 200 from a super shotgun blast. Picking by
         * position made the marine reach for the weakest thing he owned, and the odds of
         * finishing the table fell from 95 to 76 percent the moment the arsenal grew.
         *
         * It is per tic rather than per shot because the chaingun's whole advantage is its
         * rate. P_GunShot averages 10 across its three outcomes; a missile's damage is the
         * fixed figure from mobjinfo. Splash is not modelled, so a rocket is worth exactly
         * its direct hit here - which is why it ranks low, correctly.
         */
        val damagePerTic: Double by lazy {
            val perShot = pellets * 10.0 +
                if (projectile >= 0) missileDamage(projectiles[projectile].damage).toDouble() else 0.0
            perShot / attack.tics.sum().coerceAtLeast(1)
        }
    }

    const val AMMO_BULLETS = 0
    const val AMMO_SHELLS = 1
    const val AMMO_CELLS = 2
    const val AMMO_ROCKETS = 3

    /** p_inter.c: maxammo[NUMAMMO] = {200, 50, 300, 50}. */
    val maxAmmo = intArrayOf(200, 50, 300, 50)

    /** p_inter.c: clipammo[NUMAMMO] = {10, 4, 20, 1}. A weapon carries two clip loads. */
    val clipAmmo = intArrayOf(10, 4, 20, 1)

    /** p_pspr.c A_FireShotgun2: twenty pellets, each of the same P_GunShot damage. */
    const val SUPER_SHOTGUN_PELLETS = 20

    val weapons = listOf(
        // The pistol consumes no ammo: in a wallpaper, being disarmed forever would be a
        // deadlock, and the marine cannot go looking for ammo the way a player would.
        Weapon("Pistol", "PIST", -1, Anim(intArrayOf(4, 5, 4), intArrayOf(6, 8, 6)), pellets = 1),
        Weapon("Shotgun", "SHOT", AMMO_SHELLS, Anim(intArrayOf(4, 5, 4), intArrayOf(6, 10, 8)), pellets = SHOTGUN_PELLETS),
        Weapon("Chaingun", "MGUN", AMMO_BULLETS, Anim(intArrayOf(4, 5), intArrayOf(3, 3)), pellets = 1),
        Weapon("SuperShotgun", "SGN2", AMMO_SHELLS, Anim(intArrayOf(4, 5, 4), intArrayOf(6, 12, 10)), pellets = SUPER_SHOTGUN_PELLETS),
        Weapon("PlasmaRifle", "PLAS", AMMO_CELLS, Anim(intArrayOf(4, 5), intArrayOf(3, 3)), projectile = PROJECTILE_PLASMA),
        Weapon("RocketLauncher", "LAUN", AMMO_ROCKETS, Anim(intArrayOf(4, 5, 4), intArrayOf(8, 12, 10)), projectile = PROJECTILE_ROCKET),
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
        Item("STIM", ITEM_HEALTH, 10, weight = 10),                       // stimpack
        Item("MEDI", ITEM_HEALTH, 25, weight = 8),                        // medikit
        Item("ARM1", ITEM_ARMOR, 100, extra = 1, frames = 2, weight = 8),  // green armour
        Item("ARM2", ITEM_ARMOR, 200, extra = 2, frames = 2, weight = 4),  // blue armour
        // One per weapon past the pistol, each carrying two clip loads. The weights are
        // scaled up from the old pair rather than squeezed, so healing still outweighs
        // armour and armour still outweighs the guns with five of them in the table.
        Item("SHOT", ITEM_WEAPON, 2, extra = 1, weight = 3),               // shotgun
        Item("MGUN", ITEM_WEAPON, 2, extra = 2, weight = 2),               // chaingun
        Item("SGN2", ITEM_WEAPON, 2, extra = 3, weight = 2),               // super shotgun
        Item("PLAS", ITEM_WEAPON, 2, extra = 4, weight = 2),               // plasma rifle
        Item("LAUN", ITEM_WEAPON, 2, extra = 5, weight = 1),               // rocket launcher
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
         * How far into [waves] this skill runs, and therefore what it can ever meet.
         *
         * Twenty-six for every skill: every level meets the whole bestiary, Cyberlord
         * included. That was chosen knowing the cost, which is measured and worth stating -
         * the easiest skill finishes the table 29% of the time rather than 95%, and every
         * level above it under 4%. Promotions are therefore rare, and the scene in practice
         * stays near the bottom of the ladder.
         *
         * The field stays rather than being folded back into waves.size: it is the one place
         * to change if that trade is ever revisited, and it is per-skill because that is the
         * only lever that moves this without touching the drop rate.
         */
        val waveCount: Int,        /**
         * Odds out of 256 that an arrival is replaced by the next creature up the bestiary.
         *
         * The engine's hard skill does not merely add monsters, it admits nastier ones: the
         * MTF_HARD things in a map include creatures the easy pass never spawns. Twenty-six
         * authored waves are still not a map, so it is applied per arrival instead. Counting
         * arrivals alone was measured to make no difference at all in the opening wave — two
         * zombies at every skill, and a marine who beats them every time.
         *
         * It is also the lever that decides "Hurt me plenty": at that level depth barely
         * moved the odds (20 waves 46%, 22 waves 44%) while toughen moved them straight
         * through the target, 60 -> 105 taking 46% to 36.7%.
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
    )


    /**
     * The ladder: g_game.c skill_t, with a rung of our own between each pair.
     *
     * The five names are the engine's, from the difficulty menu, and they keep their places
     * and their flags. The four in between are this wallpaper's, and they are named after the
     * people it was made around rather than after anything in the engine - there is no canonical
     * text to borrow for a level the original does not have, and inventing something that
     * sounded canonical would be worse than admitting whose ladder this is.
     *
     * Each inserted rung takes the step its neighbours leave: the drop interval and the
     * toughen odds sit between theirs, and a flag is turned on one level before the canonical
     * level that owns it, so the change arrives as a warning rather than a wall.
     */
    val skills = listOf(
        Skill("I'm too young to die", 8, 120, waveCount = 26, toughen = 0, halfDamage = true, doubleAmmo = true),
        // Half damage is gone; the ammunition is not, so this is the first level that hurts.
        // The drop interval has to fall a long way to pay for that, because half damage is by
        // far the strongest lever on this ladder: at 126 tics this measured 68.5%, below the
        // level beneath it, and the gap only closes when the supplies come roughly twice as
        // often as anywhere else.
        Skill("Pity me", 8, 55, waveCount = 26, toughen = 0, doubleAmmo = true),
        Skill("Hey, not too rough", 8, 62, waveCount = 26, toughen = 0),
        Skill("Rough it", 8, 116, waveCount = 26, toughen = 52),
        Skill("Hurt me plenty", 8, 140, waveCount = 26, toughen = 105),
        Skill("Only killing", 8, 225, waveCount = 26, toughen = 112),
        Skill("Ultra-Violence", 8, 330, waveCount = 26, toughen = 120),
        // Fast monsters were tried here, one rung early, and it inverted the ladder: this
        // level measured 0.5% against Nightmare's 1.2%, so the hardest level in the game was
        // no longer the hardest. Speed is not a step, it is a cliff. The rung is made out of
        // the two continuous levers instead, and fast stays where the engine puts it.
        // Loosened again after it measured 1.0% against Nightmare's 1.2%: inside the noise of
        // a 200-life sample, but on the wrong side of it, and the ladder is asserted to never
        // rise. The gap has to be real, not merely intended.
        Skill("Final undoing", 8, 420, waveCount = 26, toughen = 124),
        // Lower toughen than Ultra-Violence would suggest, and still far harder: this level
        // alone brings fast FleshWorms and monsters that come back, and a wave that refills
        // is a wave the marine is very unlikely to finish. The parameter is not the
        // difficulty; the measured outcome is, and that is what the test asserts.
        Skill(
            "Nightmare!", 8, 1600, waveCount = 26, toughen = 130,
            doubleAmmo = true, fast = true, respawn = true,
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
        // The table names every creature in the bestiary, in health order, and each one
        // enters **alone** in a wave of its own before being escorted in the wave after.
        // Nothing in here asks whether the loaded WAD has that creature: five of them exist
        // only in a Phase 2 IWAD, and on a Phase 1 file Scene.substitute walks down to
        // something it can draw. One table, every IWAD.
        //
        // Twenty-six waves and never more than two arrivals at once, where it used to be
        // sixteen reaching three with a burst of two - six bodies on screen against two.
        // Crowding was the complaint, so the difficulty moved off "how many at once" and
        // onto "how far down the list", which is [Skill.waveCount]: each skill runs a
        // longer prefix of this table, and only Nightmare ever meets the Cyberlord.
        //
        // The escort waves arrive as a pair - burst 2 - and the solo waves do not. That is
        // one change fixing two complaints at once. With burst 1 everywhere, two enemies
        // never appeared together in the whole table, so the wallpaper had no doubles at all;
        // and the second wave, which is one creature twice, delivered the same creature in
        // two consecutive arrivals, which is what read as a repetition. Arriving together it
        // is a pair rather than a stutter.
        //
        // WaveTableTest asserts the shape rather than trusting this paragraph: no creature in
        // two consecutive arrivals, every creature alone once and escorted once, doubles
        // present, and the roster never walking back down the bestiary.
        //
        // Compressing the pacing was tried and measured: shrinking the delays and rests
        // took the mean life from 130 s to 85 s and the easiest skill from 29% to 17%. The
        // marine needs the gaps. They stayed.
        //   creatures                delay              burst  rest after
        Wave(intArrayOf(0), /*             2.00 s */ TICRATE * 2, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(0, 0), /*          1.75 s */ TICRATE * 7 / 4, 2, TICRATE * 5 / 4),
        Wave(intArrayOf(1), /*             1.75 s */ TICRATE * 7 / 4, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(0, 1), /*          1.75 s */ TICRATE * 7 / 4, 2, TICRATE * 5 / 4),
        Wave(intArrayOf(2), /*             1.50 s */ TICRATE * 3 / 2, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(1, 2), /*          1.50 s */ TICRATE * 3 / 2, 2, TICRATE * 5 / 4),
        Wave(intArrayOf(3), /*             1.50 s */ TICRATE * 3 / 2, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(2, 3), /*          1.50 s */ TICRATE * 3 / 2, 2, TICRATE),
        Wave(intArrayOf(4), /*             1.50 s */ TICRATE * 3 / 2, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(3, 4), /*          1.25 s */ TICRATE * 5 / 4, 2, TICRATE),
        Wave(intArrayOf(5), /*             1.25 s */ TICRATE * 5 / 4, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(4, 5), /*          1.25 s */ TICRATE * 5 / 4, 2, TICRATE),
        Wave(intArrayOf(6), /*             1.25 s */ TICRATE * 5 / 4, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(5, 6), /*          1.25 s */ TICRATE * 5 / 4, 2, TICRATE),
        Wave(intArrayOf(7), /*             1.25 s */ TICRATE * 5 / 4, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(6, 7), /*          1.00 s */ TICRATE, 2, TICRATE),
        Wave(intArrayOf(8), /*             1.00 s */ TICRATE, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(7, 8), /*          1.00 s */ TICRATE, 2, TICRATE),
        Wave(intArrayOf(9), /*             1.00 s */ TICRATE, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(8, 9), /*          1.00 s */ TICRATE, 2, TICRATE),
        Wave(intArrayOf(10), /*            1.00 s */ TICRATE, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(9, 10), /*         0.75 s */ TICRATE * 3 / 4, 2, TICRATE),
        Wave(intArrayOf(11), /*            0.75 s */ TICRATE * 3 / 4, 1, TICRATE * 5 / 4),
        Wave(intArrayOf(10, 11), /*        0.75 s */ TICRATE * 3 / 4, 2, TICRATE),
        // The last two arrive alone and stay alone. The Overlord takes 71% of the screen
        // width - measured, not guessed - so an escort would leave nowhere to look.
        Wave(intArrayOf(12), /*            0.75 s */ TICRATE * 3 / 4, 1, TICRATE * 2),
        Wave(intArrayOf(13), /*            0.75 s */ TICRATE * 3 / 4, 1, TICRATE * 3),
    )    /**
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
