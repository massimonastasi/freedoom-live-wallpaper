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

import io.github.massimonastasi.freedoomlw.GameData.DI_NODIR
import io.github.massimonastasi.freedoomlw.GameData.FRACUNIT
import io.github.massimonastasi.freedoomlw.GameData.MELEERANGE
import io.github.massimonastasi.freedoomlw.GameData.opposite
import io.github.massimonastasi.freedoomlw.GameData.pRandom
import io.github.massimonastasi.freedoomlw.GameData.xspeed
import io.github.massimonastasi.freedoomlw.GameData.yspeed
import kotlin.math.abs

/** What an actor is doing. Mirrors the states[] groups of the engine. */
enum class Mode { WALK, ATTACK, PAIN, DEATH, PROJECTILE, EFFECT, ITEM }

/**
 * What the marine is carrying. Kept apart from [Actor] because only one actor in the scene
 * ever has it: every blood splat, fog puff and fireball is an actor too, and giving them
 * all an ammo array was an allocation per spawn for state they can never use.
 */
class Loadout {
    var armorPoints = 0
    var armorType = 0

    /**
     * One bit per weapon: the marine carries an arsenal, not a single gun, and always
     * reaches for the most powerful thing in it.
     *
     * A set rather than a "highest owned" index, because a weapon that runs dry is taken
     * away entirely. With ammunition no longer dropped on its own, an empty gun can never be
     * refilled where it stands, so keeping it would only leave a hole in the arsenal that
     * nothing could fill. The pistol is never in the set — it is the floor everything falls
     * back to, and it needs no ammunition.
     */
    var owned = 0

    val ammo = IntArray(GameData.maxAmmo.size)

    fun has(weapon: Int) = owned and (1 shl weapon) != 0

    fun take(weapon: Int) { owned = owned or (1 shl weapon) }

    fun drop(weapon: Int) { owned = owned and (1 shl weapon).inv() }

}

/**
 * An actor in the scene: creature, projectile, effect or pickup.
 *
 * Positions are in map units, 16.16 fixed-point like the original: no drift, and the
 * numbers stay comparable 1:1 with the id source.
 */
class Actor(val spriteIndex: Int) {
    var x = 0
    var y = 0
    var moveDir = DI_NODIR
    var moveCount = 0
    var spawnTic = 0

    var mode = Mode.WALK
    var health = 0
    var creature: GameData.Creature? = null
    var isPlayer = false

    /** Animation in progress for every mode other than WALK. */
    var anim: GameData.Anim? = null
    var animStep = 0
    var animTics = 0

    /** Projectiles only: momentum and damage. */
    var momX = 0
    var momY = 0
    var damage = 0
    var firedByPlayer = false

    var targetX = 0
    var targetY = 0
    var dead = false

    /**
     * How far above its own position this is drawn, in map units. Rendering only.
     *
     * The world here has no third dimension: x is across and y is depth, and an actor's
     * position is where it stands. A shot leaving the marine's chest is therefore not
     * expressible as a position at all - it is the same spot on the floor, drawn higher.
     *
     * Writing the height into y instead was tried and is wrong in a way that does not show
     * up as a wrong picture: y is depth, so it decides the drawing order, the distance to a
     * target and whether a projectile has reached one. A fireball raised that way flies at a
     * depth thirty-four units behind where it appears to be, sorts against the wrong actors,
     * and can pass through something narrow enough that the radius test misses. This field
     * keeps the simulation reading the position the actor actually occupies.
     */
    var drawHeight = 0

    /** Tics of stillness after appearing: info.c mobjinfo.reactiontime. */
    var reactionTime = 0

    /**
     * g_game.c:1423 halves the FleshWorm's run and pain tics on the fast skills. In the
     * engine that alone doubles its speed, because movement happens inside A_Chase and the
     * state calls A_Chase twice as often; here the chase runs every tic, so the doubling has
     * to be applied to the step as well as to the animation.
     */
    var fast = false

    /** Already came back once, and so will not be queued to respawn again. */
    var respawned = false

    /**
     * Direction the sprite is drawn facing, which is not always the direction of travel.
     * It follows movement while walking, and snaps to the target when an attack starts —
     * that is what A_FaceTarget does in the engine. Without it the marine, who backs away
     * while shooting, was drawn with his back to the demon he was firing at.
     */
    var facing = DI_NODIR

    /** Marine only: everything he is carrying. Null for every other actor. */
    var loadout: Loadout? = null

    /** Pickups lying on the ground only. */
    var item: GameData.Item? = null

    val radius get() = creature?.radius ?: 6

    /** The frame to draw right now. */
    fun frame(tic: Int): Int {
        val a = anim
        if (a != null) return a.frames[animStep]
        item?.let { return if (it.frames > 1) ((tic - spawnTic) / 6) % it.frames else 0 }
        val c = creature ?: return 0
        // Walking: the original engine repeats each frame twice (A,A,B,B,...), so it lasts walkTics*2.
        val per = if (fast) c.walkTics else c.walkTics * 2
        return ((tic - spawnTic) / per) % c.walkFrames
    }

    /**
     * Sprite rotation (1-8) as seen from a fixed camera below the scene.
     *
     * Read off the artwork rather than derived, because deriving it got the handedness
     * wrong twice. Decoding the eight rotations of the walk frame and looking at them shows
     * the convention plainly:
     *
     *   1 faces the camera, 5 faces away, 3 is a profile facing left, 7 a profile facing
     *   right, and the four in between are the diagonals.
     *
     * With DI_NORTH moving towards +y and screen y growing downwards, DI_NORTH is towards
     * the camera and must give 1, DI_WEST must give 3, DI_SOUTH 5 and DI_EAST 7. Those four
     * fix the mapping as a rotation by two eighths.
     *
     * The engine formula in r_things.c looks like a reflection instead, because the original
     * measures angles anticlockwise with +y pointing north on the map, while our screen has
     * +y pointing down. That flip of handedness reverses the direction of rotation, and
     * ignoring it produced a version with the vertical directions right and the horizontal
     * ones mirrored.
     */
    fun spriteRotation(): Int {
        if (facing == DI_NODIR) return 1
        return ((facing - 2) and 7) + 1
    }
}

/**
 * The scene: actors, movement, combat.
 *
 * The world is the original engine's x/y plane in map units. The projection onto the screen is oblique:
 * x horizontal, y into the depth. There are no walls, so the P_CheckSight line-of-sight
 * test is unnecessary — here the firing line really is always clear, it is not a shortcut.
 */
class Scene(
    private val worldWidth: Int,
    private val worldHeight: Int,
    /** Skill to open on. Only the first run uses it: a death always drops back to zero. */
    startSkill: Int = 0,
    /**
     * Skip the wait before the very first arrival.
     *
     * For the picker preview, where the wallpaper has a few seconds to make its case and
     * three of them spent watching one marine stand alone is most of that budget. Only the
     * opening wave is rushed; everything after it keeps the authored pacing, so the preview
     * is not a different game.
     */
    instantStart: Boolean = false,
    /** Which creatures the active WAD can draw. See [drawable]. */
    drawable: BooleanArray? = null,
) {

    val actors = ArrayList<Actor>()

    /** Setting: the marine never dies. Used by the balance measurements. */
    var invulnerable = false

    /**
     * Which creatures the active WAD can actually draw, by index into GameData.creatures.
     *
     * A user's IWAD need not carry all of them: the shareware release has no Trilobite or
     * PainLord, and a partial or unusual one may be missing more. Without this they were
     * still spawned and still fought — the draw loop skips a sprite it cannot resolve, so
     * they were invisible rather than absent, which is far worse than not appearing at all.
     *
     * Null when every creature is available, which is what keeps Scene testable with no WAD
     * in sight.
     */
    var drawable: BooleanArray? = drawable

    /**
     * Which pickups the active WAD can draw, by index into GameData.items.
     *
     * The same problem as the creatures, and the reason the arsenal can grow at all: the
     * super shotgun arrived with Phase 2, so a Phase 1 IWAD has no SGN2 sprite and the weapon
     * must simply never be dropped. Nothing anywhere lists which file carries what.
     */
    var drawableItems: BooleanArray? = null

    /**
     * Which creatures leave a body big enough to be drawn under everything else.
     *
     * Measured from the loaded WAD rather than named here, because it is a fact about the
     * artwork and the artwork is swappable: the Overlord rests 100 pixels tall in Freedoom
     * and 38 in Phase 2, and the Cyberdemon the other way round. See the wallpaper service,
     * which owns the sprites and therefore the measurement.
     *
     * Indexed by sprite, not by creature, because that is the index an actor already carries:
     * this is read for every body on every frame, and a lookup by identity would be a scan of
     * the bestiary each time.
     *
     * Null when nothing has been measured, which is what keeps Scene testable with no WAD.
     */
    var tallCorpses: BooleanArray? = null

    /**
     * True when this actor's body should be drawn beneath the fight rather than in it.
     *
     * Only once it has settled on its resting frame: a death animation belongs where the
     * fight is, and only what it leaves behind is furniture. That also settles the Charger
     * without naming it - it detonates and is removed rather than resting, so it never
     * qualifies, and its explosion plays where everything else can see it.
     */
    fun restsBelow(a: Actor): Boolean =
        a.mode == Mode.DEATH &&
            a.animTics == -1 &&
            tallCorpses?.getOrNull(a.spriteIndex) == true

    /** Current wave, zero-based. */
    var wave = 0
        private set

    /**
     * Current skill level, an index into [GameData.skills]. It climbs by one every time the
     * wave table is finished, so the marine walks the whole difficulty ladder from
     * "I'm too young to die" up to "Nightmare!" — and a death drops him back to the bottom.
     */
    var skill = startSkill.coerceIn(GameData.skills.indices)
        private set

    private val rules get() = GameData.skills[skill]

    /** Waves cleared since the last death, which is what earns the promotion. */
    private var cleared = 0

    /** Consumed by the first wave that is armed, so only that one skips its delay. */
    private var rushOpening = instantStart

    /**
     * The tic the marine is due to arrive on, held empty until then.
     *
     * The preview has no time to spend on an empty floor, so it skips the wait entirely —
     * the same reasoning as [rushOpening], and the same exception.
     */
    private var playerDueAt = if (instantStart) 0 else ARRIVAL_DELAY

    /** What the corner readout shows. Zero while the marine is down. */
    val playerHealth: Int get() = player?.takeIf { !it.dead }?.health?.coerceAtLeast(0) ?: 0
    val playerArmor: Int get() = player?.takeIf { !it.dead }?.loadout?.armorPoints ?: 0

    private var tic = 0
    private var player: Actor? = null
    private var nextWaveAt = 0
    private var deadUntil = 0

    /** Arrival queue for the current wave, and how far along it we are. */
    private val queue = ArrayList<Int>()
    private var spawnIndex = 0
    private var nextSpawnAt = 0

    /**
     * Monsters waiting to come back, on the skills that respawn them. Each entry packs the
     * tic it is due at together with its creature index, so this stays one flat list of ints
     * with no allocation per corpse.
     */
    private val respawns = ArrayList<Int>()

    /**
     * 0 while the marine is alive, rising to 1 as the screen sinks into red after his
     * death. The colour itself is chosen by the renderer, read from PLAYPAL.
     */
    val deathFade: Float
        get() {
            if (deadUntil == 0) return 0f
            val left = deadUntil - tic
            if (left <= 0) return 1f
            return 1f - left.toFloat() / DEATH_DELAY
        }

    fun tick(now: Int) {
        tic = now

        updateWaves()

        // An item every now and then: it gives the marine a reason to cross the scene and
        // makes the fighting less predictable.
        // Rarer the harder it gets. Over a table lasting minutes this is what decides
        // whether the marine survives it, far more than what he is fighting.
        //
        // Only once he is on the field: at the easiest skill the drops come every fifty
        // tics, which is sooner than he arrives, and supplies were materialising onto empty
        // ground with nobody there to want them.
        if (player != null && deadUntil == 0 && tic % rules.dropInterval == 0) spawnItem()

        var i = 0
        while (i < actors.size) {
            val a = actors[i]
            when (a.mode) {
                Mode.ITEM -> if (!updateItem(a)) { actors.removeAt(i); continue }
                Mode.EFFECT -> if (!advanceAnim(a)) { actors.removeAt(i); continue }
                Mode.PROJECTILE -> if (!moveProjectile(a)) { actors.removeAt(i); continue }
                Mode.DEATH -> if (!advanceCorpse(a)) { actors.removeAt(i); continue }
                Mode.PAIN -> if (!advanceAnim(a)) { a.mode = Mode.WALK; a.anim = null }
                Mode.ATTACK -> advanceAttack(a)
                Mode.WALK -> chase(a, i)
            }
            i++
        }

        sortByDepth()
    }

    private fun countDemons(): Int {
        var n = 0
        for (a in actors) if (a.creature != null && !a.isPlayer && !a.dead) n++
        return n
    }

    /**
     * The pace of the game: the marine arrives first and stays alone for a few seconds,
     * then the enemies come in one at a time. The next wave only starts once nobody is
     * left. If the marine falls, the screen goes red and everything restarts.
     */
    private fun updateWaves() {
        if (deadUntil > 0) {
            if (tic < deadUntil) return                  // no arrivals during the death fade
            restart()
            return
        }

        val p = player
        if (p == null) {
            // The scene opens on empty ground for a moment before he arrives, so his
            // teleport fog is something that happens rather than something already there
            // when you looked. The wave is only armed once he is in, so the first enemy is
            // still a full spawnDelay behind him and the whole opening simply shifts.
            if (tic < playerDueAt) return
            spawnPlayer()
            startWave()
            return
        }
        if (p.dead) {
            deadUntil = tic + DEATH_DELAY
            return
        }

        updateRespawns()

        // Staggered arrivals: until the whole wave is in, we do not judge it finished.
        if (spawnIndex < queue.size) {
            if (tic >= nextSpawnAt) {
                val w = GameData.waves[wave]
                var n = w.burst
                while (n-- > 0 && spawnIndex < queue.size) {
                    spawnDemon(GameData.creatures[queue[spawnIndex++]], respawned = false)
                }
                nextSpawnAt = tic + w.spawnDelay
            }
            return
        }

        if (countDemons() > 0 || respawns.isNotEmpty()) {
            nextWaveAt = 0
            return
        }
        // Wave cleared: the pause is the one belonging to the wave just finished. Breathing
        // room between waves matters, otherwise the rhythm turns into continuous noise.
        if (nextWaveAt == 0) {
            nextWaveAt = tic + GameData.waves[wave].rest
            return
        }
        if (tic >= nextWaveAt) {
            nextWaveAt = 0
            wave = (wave + 1) % rules.waveCount
            // The whole sequence then replays one skill level harder, up to Nightmare,
            // which is where it stays.
            cleared++
            // Reset rather than modulo: each skill runs a different number of waves, so a
            // running total taken against a changing divisor would promote at the wrong
            // place the moment the ladder moved.
            if (cleared >= rules.waveCount) {
                cleared = 0
                if (skill < GameData.skills.size - 1) skill++
            }
            startWave()
        }
    }

    /**
     * P_NightmareRespawn (p_mobj.c): on the skills that respawn, a monster comes back twelve
     * seconds after it fell.
     *
     * ponytail: a monster only comes back once. The engine respawns forever, which there is
     * fine because a level ends; here a wave would never clear and the marine would be stuck
     * on the same one until he died.
     */
    private fun updateRespawns() {
        var i = 0
        while (i < respawns.size) {
            val packed = respawns[i]
            if (tic >= packed shr 3) {
                spawnDemon(GameData.creatures[packed and 7], respawned = true)
                respawns.removeAt(i)
            } else i++
        }
    }

    /**
     * After death everything restarts: first wave, lowest skill, nothing carried over.
     *
     * g_game.c G_PlayerReborn memsets the whole player struct and hands back the pistol, so
     * death costs the armour and the arsenal alike. Nothing survives here either, which is
     * why there is no state to carry: the new marine simply gets a fresh Loadout.
     */
    private fun restart() {
        actors.clear()
        respawns.clear()
        player = null
        deadUntil = 0
        nextWaveAt = 0
        wave = 0
        skill = 0
        cleared = 0
        // He takes the same moment to arrive after dying as he did at the start. The red
        // wash has already faded by now, so this is a beat of quiet ground, not a second
        // pause stacked on the first.
        playerDueAt = tic + ARRIVAL_DELAY
    }

    /**
     * Arms the wave: nobody enters immediately, the first arrives after spawnDelay.
     *
     * The queue is the wave's own order stretched or trimmed to the skill's share of it,
     * which is this scene's stand-in for the map thing flags the engine filters on.
     */
    private fun startWave() {
        val w = GameData.waves[wave]
        // Rounded up, so the ratio still bites on the short early waves: rounding to nearest
        // gave the opening wave two enemies at every skill, and the ladder started flat.
        val n = (w.order.size * rules.countEighths + 7) / 8
        queue.clear()
        for (i in 0 until n) {
            // Exactly one step, never more. Promoting further was tried and measured worse:
            // the bestiary is ordered by the original's own toughness, which is health, and
            // that is not lethality here. Two steps turns a Zombie into a Serpentipede,
            // whose fireballs the marine sidesteps, instead of a ShotgunZombie, whose three
            // hitscan shots cannot be dodged at all. The odds went *up* at the hard end.
            var c = w.order[i % w.order.size]
            if (pRandom() < rules.toughen && c + 1 < GameData.creatures.size) c++
            queue.add(substitute(c))
        }
        spawnIndex = 0
        nextSpawnAt = tic + if (rushOpening) 0 else w.spawnDelay
        rushOpening = false
    }

    /**
     * The nearest creature the active WAD can draw.
     *
     * Steps down the bestiary first, so a missing PainLord becomes the next thing below it
     * rather than a Zombie: the wave keeps roughly the weight it was written with. Only if
     * nothing below exists does it look upwards.
     */
    internal fun substitute(index: Int): Int {
        val ok = drawable ?: return index
        if (ok.getOrElse(index) { true }) return index
        for (i in index - 1 downTo 0) if (ok.getOrElse(i) { false }) return i
        for (i in index + 1 until GameData.creatures.size) if (ok.getOrElse(i) { false }) return i
        return index
    }

    private fun BooleanArray.getOrElse(i: Int, fallback: () -> Boolean) =
        if (i in indices) this[i] else fallback()

    // ---------------------------------------------------------------- spawning

    /**
     * The spawn margin, clamped so it can never exceed a quarter of the world.
     *
     * SPAWN_MARGIN is derived from the widest sprite and is right for a phone, where the
     * world is around 720 by 1600 units. It is not right for every surface the wallpaper can
     * be handed: split screen, a foldable cover display or a picker thumbnail can be small
     * enough that twice the margin exceeds the whole world, at which point the spawn range
     * inverts and actors are placed outside it. Measured, not imagined: a 180 by 320 world
     * put them off screen within seconds.
     */
    private val marginX = minOf(SPAWN_MARGIN, worldWidth / 4)

    /**
     * Vertical margins, which are **not** symmetric, because a sprite is anchored at its
     * feet and grows upwards from there.
     *
     * At the bottom edge the anchor can sit almost on the boundary and the whole sprite is
     * still drawn above it. At the top the sprite reaches far past the actor's own position,
     * so an actor standing near y=0 is drawn entirely off the screen — which is exactly what
     * was reported: the marine vanishing off the top. Bounding movement by the actor radius
     * alone allowed it, since the marine's radius is 16 units and his sprite reaches 108.
     *
     * See [TOP_MARGIN] and [BOTTOM_MARGIN] for where the numbers come from.
     */
    private val marginTop = minOf(TOP_MARGIN, worldHeight / 3)
    private val marginBottom = minOf(BOTTOM_MARGIN, worldHeight / 8)

    /**
     * Where an actor may *appear*, which is not the same as where it may walk.
     *
     * Movement only has to keep the sprite on screen, and at the bottom that costs very
     * little. Arriving is different: a creature that materialises right on the bottom edge
     * is technically drawn in full but reads as appearing out of the dock, so an arrival
     * keeps the full spawn margin there and walks out of it afterwards if it wants to.
     */
    private val spawnMarginBottom = minOf(SPAWN_MARGIN, worldHeight / 4)

    // The single set of bounds every actor obeys, in fixed-point. Movement, spawning,
    // wandering targets and the retreat step all used to derive these separately, in four
    // different ways, which is how the top edge came to be wrong in only one of them.
    private val minX = marginX * FRACUNIT
    private val maxX = (worldWidth - marginX) * FRACUNIT
    private val minY = marginTop * FRACUNIT
    private val maxY = (worldHeight - marginBottom) * FRACUNIT

    /**
     * P_Random returns 0-255: in the original engine it serves probabilities and small
     * offsets, never the choice of a point on a map. Using it with `%` over a range larger
     * than 256 would confine everything to one corner, so it gets scaled instead.
     */
    private fun randomIn(min: Int, max: Int): Int =
        if (max <= min) min else min + pRandom() * (max - min) / 256

    private fun spawnPlayer() {
        val a = newCreature(GameData.player)
        a.isPlayer = true
        // He always starts as the original starts him: pistol, no armour, no shells.
        a.loadout = Loadout()
        a.x = randomIn(marginX, worldWidth - marginX) * FRACUNIT
        a.y = randomIn(marginTop, worldHeight - spawnMarginBottom) * FRACUNIT
        // Longer than the creatures' reactiontime of 8 tics, which at under a quarter of a
        // second reads as no pause at all. He arrives alone and the first enemy is seconds
        // away, so he can afford to stand in the fog long enough to be noticed.
        a.reactionTime = PLAYER_REACTION
        spawnFog(a.x, a.y)
        actors.add(a)
        player = a
        newTarget(a)
    }

    /** Brings an actor into the scene at its current position: fog, list, first target. */
    private fun materialise(a: Actor) {
        spawnFog(a.x, a.y)
        actors.add(a)
        newTarget(a)
    }

    private fun newCreature(c: GameData.Creature): Actor {
        val a = Actor(c.spriteIndex)
        a.creature = c
        // Softer than the originals, by an amount that depends on where in the ladder and
        // where in the table this is happening. The marine is never scaled. See healthScale.
        // Exactly what info.c prints, at every level. The difficulty is carried by what the
        // marine does to them, not by what they are; see marineDamage.
        a.health = c.health
        a.spawnTic = tic
        a.reactionTime = GameData.REACTION_TIME
        return a
    }

    /**
     * The marine's damage, scaled by where he is on the ladder and in the table.
     *
     * This is the whole difficulty lever, and it sits on his side of the fight rather than on
     * the monsters'. Health used to be the lever - every creature spawned with a fraction of
     * the figure info.c gives it - and moving it here buys two things. The arithmetic happens
     * once per shot instead of once per creature per spawn, and, more usefully, the bestiary
     * reads as itself: a Cyberdemon has 4000 health on every skill, so the table of what
     * things are is one column rather than nine.
     *
     * The shape is the one that was fitted when this was health, mirrored: none of it at the
     * hardest skill, where the marine deals exactly what p_pspr.c says and nothing is bent in
     * his favour, and most of it already in force at the first wave, because a level that has
     * to be gentle has to be gentle from the start. The wave decides only the last stretch.
     */
    internal fun marineDamage(base: Int): Int = base * damageScale(skill, wave) / 100

    private fun spawnDemon(c: GameData.Creature, respawned: Boolean) {
        val a = newCreature(c)
        a.respawned = respawned
        // Only the FleshWorm: g_game.c touches S_SARG_RUN1..S_SARG_PAIN2 and nothing else.
        a.fast = rules.fast && c === GameData.fleshWorm
        // Anywhere across the width, and in the half of the screen the marine is not in.
        //
        // Arrivals used to come from one of the two side edges, which made every wave enter
        // the same way and left the middle of the screen a place nothing ever appeared. This
        // spreads them: horizontally free, vertically on the far side of the marine, so a
        // creature has ground to cross before it reaches him and does not materialise on top
        // of him.
        //
        // The margins still hold. A sprite is anchored at its feet and drawn upwards, so
        // spawning flush against an edge puts half the creature off screen - and a quick kill
        // could then remove it before it had ever properly been seen.
        val top = marginTop
        val bottom = worldHeight - spawnMarginBottom
        val middle = (top + bottom) / 2
        val marineY = player?.let { it.y / FRACUNIT } ?: middle

        a.x = randomIn(marginX, worldWidth - marginX) * FRACUNIT
        a.y = if (marineY < middle) {
            randomIn(middle, bottom)
        } else {
            randomIn(top, middle)
        } * FRACUNIT
        materialise(a)
    }

    private fun spawnEffect(
        spriteIndex: Int,
        anim: GameData.Anim,
        x: Int,
        y: Int,
        drawHeight: Int = 0,
    ) {
        val a = Actor(spriteIndex)
        begin(a, Mode.EFFECT, anim)
        a.x = x
        a.y = y
        a.drawHeight = drawHeight
        a.spawnTic = tic
        actors.add(a)
    }

    /** Teleport fog is on the ground, because that is where somebody arrives. */
    private fun spawnFog(x: Int, y: Int) = spawnEffect(GameData.fogSpriteIndex, GameData.fogAnim, x, y)

    /** Blood is where the shot landed, which is the height the shot was fired at. */
    private fun spawnBlood(x: Int, y: Int) =
        spawnEffect(GameData.bloodSpriteIndex, GameData.bloodAnim, x, y, MUZZLE_HEIGHT)

    /** Drops a pickup somewhere on the map, or at a chosen spot. */
    private fun spawnItem(
        x: Int = randomIn(marginX, worldWidth - marginX) * FRACUNIT,
        y: Int = randomIn(marginTop, worldHeight - spawnMarginBottom) * FRACUNIT,
    ) {
        // Redrawn until the weighted pick lands on something this WAD can show. Bounded by
        // the table's own size rather than looping forever: with every weapon missing, the
        // health and armour entries still make up most of the table, so a handful of tries
        // always finds one.
        var choice = GameData.dropTable[pRandom() % GameData.dropTable.size]
        val ok = drawableItems
        if (ok != null) {
            var tries = 0
            while (!ok.getOrElse(choice) { true } && tries < GameData.dropTable.size) {
                choice = GameData.dropTable[pRandom() % GameData.dropTable.size]
                tries++
            }
            if (!ok.getOrElse(choice) { true }) return
        }
        val it = GameData.items[choice]
        val a = Actor(it.spriteIndex)
        a.mode = Mode.ITEM
        a.item = it
        a.x = clampX(x)
        a.y = clampY(y)
        a.spawnTic = tic
        actors.add(a)
    }

    private fun clampX(x: Int) = x.coerceIn(minX, maxX)

    private fun clampY(y: Int) = y.coerceIn(minY, maxY)

    // ---------------------------------------------------------------- interaction

    /**
     * The user tapped the home screen: drop a pickup where they touched.
     *
     * Ignored while the marine is dead, so the red wash stays a pause rather than something
     * the user can litter with items nobody will collect.
     */
    fun tapAt(x: Int, y: Int) {
        if (deadUntil > 0) return
        spawnItem(clampX(x), clampY(y))
    }

    /**
     * An icon was dropped on the home screen: send demons to that spot.
     *
     * They arrive outside the wave sequence, so this never disturbs the wave pacing: the
     * current wave still has to be cleared before the next begins, these are simply extra.
     */
    fun dropAt(x: Int, y: Int) {
        if (deadUntil > 0) return
        val count = 1 + pRandom() % 2
        repeat(count) {
            val c = GameData.creatures[pRandom() % 3]        // only the lighter creatures
            val a = newCreature(c)
            a.x = clampX(x + (pRandom() - 128) * FRACUNIT / 2)
            a.y = clampY(y + (pRandom() - 128) * FRACUNIT / 2)
            materialise(a)
        }
    }

    /** false once the pickup has been taken or has sat on the ground for too long. */
    private fun updateItem(a: Actor): Boolean {
        val p = player ?: return true
        if (!p.dead && approxDistance(a, p) < (p.radius + 24) * FRACUNIT) {
            if (pickUp(p, a.item ?: return false)) return false
        }
        return tic - a.spawnTic < ITEM_LIFETIME
    }

    /** Everything below only ever runs for the marine, the one actor with a loadout. */

    /**
     * Pickup, following the p_inter.c rules: health never exceeds 100, armour is refused
     * when the one already worn is better, and a weapon carries two clip loads.
     * Returns false when the item is not needed and should stay on the ground.
     */
    /** Test hook: pickUp is the rule set worth pinning, and it is otherwise unreachable. */
    internal fun pickUpForTest(p: Actor, item: GameData.Item) = pickUp(p, item)

    private fun pickUp(p: Actor, it: GameData.Item): Boolean {
        val kit = p.loadout ?: return false
        return when (it.kind) {
            GameData.ITEM_HEALTH -> {
                if (p.health >= GameData.player.health) false
                else { p.health = minOf(GameData.player.health, p.health + it.amount); true }
            }
            GameData.ITEM_ARMOR -> {
                if (kit.armorPoints >= it.amount) false
                else { kit.armorPoints = it.amount; kit.armorType = it.extra; true }
            }
            else -> {
                // One bit per weapon, so a second shotgun never becomes a second shotgun: it
                // replaces the one carried and reloads it, which is the only source of
                // ammunition left now that none is dropped on its own.
                //
                // Taken only if it actually gives something. It used to be taken always, so
                // a marine already holding the weapon with full ammunition walked over it and
                // it vanished for nothing.
                val reloaded = giveAmmo(kit, GameData.weapons[it.extra].ammo, it.amount)
                val isNew = !kit.has(it.extra)
                if (reloaded || isNew) { kit.take(it.extra); true } else false
            }
        }
    }

    private fun giveAmmo(kit: Loadout, type: Int, clips: Int): Boolean {
        if (type < 0) return false
        if (kit.ammo[type] >= GameData.maxAmmo[type]) return false
        // p_inter.c:95 — double ammo on the easiest skill and on Nightmare, where it is
        // needed for the opposite reason.
        var given = clips * GameData.clipAmmo[type]
        if (rules.doubleAmmo) given = given shl 1
        kit.ammo[type] = minOf(GameData.maxAmmo[type], kit.ammo[type] + given)
        return true
    }

    /**
     * The weapon in hand: the most powerful one carried that still has ammunition,
     * otherwise the pistol he started with.
     */
    private fun currentWeaponIndex(p: Actor): Int {
        val kit = p.loadout ?: return GameData.WEAPON_PISTOL
        // By expected damage per tic, not by position in the list. The original's slot order
        // puts the rocket launcher near the end and it is the weakest thing in this scene,
        // because splash is not modelled; ranking by position had the marine reach for it
        // over a super shotgun. See Weapon.damagePerTic.
        var best = GameData.WEAPON_PISTOL
        var bestRate = GameData.weapons[GameData.WEAPON_PISTOL].damagePerTic
        for (i in GameData.weapons.indices) {
            if (i == GameData.WEAPON_PISTOL || !kit.has(i)) continue
            val w = GameData.weapons[i]
            if (w.ammo >= 0 && kit.ammo[w.ammo] <= 0) continue
            if (w.damagePerTic > bestRate) { bestRate = w.damagePerTic; best = i }
        }
        return best
    }


    // ---------------------------------------------------------------- animation

    /**
     * Starts an animation.
     *
     * One place, so no caller can set three of the four fields and forget the fourth. That
     * is not hypothetical: leaving the pain state without clearing `anim` left the index
     * pointing past the end of a finished sequence, and crashed the renderer.
     */
    private fun begin(a: Actor, mode: Mode, anim: GameData.Anim) {
        a.mode = mode
        a.anim = anim
        a.animStep = 0
        a.animTics = anim.tics[0]
    }

    /**
     * Advances the animation. false once it has finished.
     *
     * Invariant: animStep always stays a valid index, even when the sequence is exhausted.
     * Letting it run past the last frame used to crash the renderer whenever a caller
     * forgot to clear anim.
     */
    private fun advanceAnim(a: Actor): Boolean {
        val anim = a.anim ?: return false
        if (--a.animTics > 0) return true
        if (a.animStep + 1 >= anim.length) return false
        a.animStep++
        a.animTics = anim.tics[a.animStep]
        return true
    }

    /**
     * Death: the last frame has a tic value of -1, meaning it stays forever — in the engine
     * corpses never disappear. In a wallpaper they would pile up, so they fade after a while.
     */
    private fun advanceCorpse(a: Actor): Boolean {
        val anim = a.anim ?: return false
        if (a.animTics == -1) return tic - a.spawnTic < CORPSE_LIFETIME
        if (--a.animTics > 0) return true
        a.animStep++
        if (a.animStep >= anim.length) return false
        a.animTics = anim.tics[a.animStep]
        if (a.animTics == -1) a.spawnTic = tic          // the corpse countdown starts here
        return true
    }

    /** The attack lands on the last frame, as in the engine (the action sits in S_*_ATK3). */
    private fun advanceAttack(a: Actor) {
        val anim = a.anim ?: return
        val last = a.animStep == anim.length - 1
        if (!advanceAnim(a)) {
            a.mode = Mode.WALK
            a.anim = null
            return
        }
        if (!last && a.animStep == anim.length - 1) fireAttack(a)
    }

    // ---------------------------------------------------------------- combat

    private fun enemyOf(a: Actor): Actor? =
        if (a.isPlayer) nearestDemon(a) else player?.takeIf { !it.dead }

    private fun nearestItem(from: Actor): Actor? {
        var best: Actor? = null
        var bestDist = Int.MAX_VALUE
        for (o in actors) {
            if (o.mode != Mode.ITEM) continue
            val d = approxDistance(from, o)
            if (d < bestDist) { bestDist = d; best = o }
        }
        return best
    }

    private fun nearestDemon(from: Actor): Actor? {
        var best: Actor? = null
        var bestDist = Int.MAX_VALUE
        for (o in actors) {
            if (o.creature == null || o.isPlayer || o.dead) continue
            val d = approxDistance(from, o)
            if (d < bestDist) { bestDist = d; best = o }
        }
        return best
    }

    /** P_AproxDistance (m_fixed.c): dx + dy/2 when dx > dy. No square root. */
    private fun approxDistance(a: Actor, b: Actor): Int {
        val dx = abs(a.x - b.x)
        val dy = abs(a.y - b.y)
        return if (dx > dy) dx + (dy shr 1) else dy + (dx shr 1)
    }

    /**
     * P_CheckMissileRange (p_enemy.c): the chance of firing falls with distance.
     * One line produces a varied combat rhythm with no timers or extra state.
     */
    private fun checkMissileRange(a: Actor, target: Actor): Boolean {
        var dist = approxDistance(a, target) - 64 * FRACUNIT
        val c = a.creature ?: return false
        if (c.meleeMod == 0) dist -= 128 * FRACUNIT
        dist = dist shr 16
        if (dist < 0) dist = 0
        if (dist > 200) dist = 200
        return pRandom() >= dist
    }

    private fun startAttack(a: Actor) {
        val c = a.creature ?: return
        // The marine uses the rate of fire of the weapon in hand: the chaingun is far
        // faster than the shotgun.
        begin(a, Mode.ATTACK, if (a.isPlayer) GameData.weapons[currentWeaponIndex(a)].attack else c.attack)
    }

    private fun fireAttack(a: Actor) {
        val c = a.creature ?: return
        val target = enemyOf(a) ?: return

        // Melee when the target is in reach: P_CheckMeleeRange uses MELEERANGE.
        if (c.meleeMod > 0 && approxDistance(a, target) < MELEERANGE + target.radius * FRACUNIT) {
            damageActor(target, (pRandom() % c.meleeMod + 1) * c.meleeMul)
            return
        }
        // The marine fires whatever he is holding, which may be hitscan or a missile; a
        // monster fires whatever its own entry says. Splitting on the actor rather than on
        // the creature is what lets the arsenal grow without touching the bestiary.
        if (a.isPlayer) {
            val i = currentWeaponIndex(a)
            val w = GameData.weapons[i]
            val kit = a.loadout
            if (w.ammo >= 0 && kit != null) {
                // Firing the last round costs him the gun: it drops out of the arsenal and
                // he falls back to whatever he still has that is loaded.
                if (--kit.ammo[w.ammo] <= 0) kit.drop(i)
            }
            if (w.projectile >= 0) {
                spawnMissile(a, target, GameData.projectiles[w.projectile])
            } else {
                // p_pspr.c: every pellet deals 5*(P_Random()%3+1). Only the count changes -
                // one for the pistol and chaingun, seven for the shotgun, twenty for the
                // super shotgun.
                var total = 0
                repeat(w.pellets) { total += GameData.gunShotDamage() }
                damageActor(target, marineDamage(total))
            }
            return
        }

        if (c.hitscanShots > 0) {
            // Instant shot: no projectile to simulate, damage applied directly.
            repeat(c.hitscanShots) { damageActor(target, GameData.hitscanDamage()) }
            return
        }
        if (c.projectile >= 0) spawnMissile(a, target, GameData.projectiles[c.projectile])
    }

    /** P_SpawnMissile: constant speed along the direction of the target. */
    private fun spawnMissile(from: Actor, target: Actor, p: GameData.Projectile) {
        val m = Actor(p.spriteIndex)
        m.mode = Mode.PROJECTILE
        m.anim = GameData.ballAnim
        m.animTics = GameData.ballAnim.tics[0]
        m.x = from.x
        m.y = from.y
        // Leaves the chest and stays there for the whole flight: the height is drawn, not
        // travelled, so the trajectory and everything it collides with are unchanged.
        m.drawHeight = MUZZLE_HEIGHT
        m.spawnTic = tic
        // The engine multiplies at impact; this is the same number, worked out once at
        // launch, because it no longer depends on a roll. See GameData.missileDamage.
        m.damage = GameData.missileDamage(p.damage)
            .let { if (from.isPlayer) marineDamage(it) else it }
        m.firedByPlayer = from.isPlayer

        val dx = target.x - from.x
        val dy = target.y - from.y
        val dist = approxDistance(from, target).coerceAtLeast(1)
        // Momentum is in fixed-point: p.speed is units per tic, as in mobjinfo where the
        // missiles have speed 10*FRACUNIT (monsters instead carry a plain integer).
        // g_game.c:1425 forces every monster missile to speed 20 on the fast skills. The
        // marine's own shots are hitscan, so nothing of his is affected.
        val speed = if (rules.fast && !from.isPlayer) GameData.FAST_MISSILE_SPEED else p.speed
        val v = (speed * FRACUNIT).toLong()
        m.momX = (v * dx / dist).toInt()
        m.momY = (v * dy / dist).toInt()
        actors.add(m)
    }

    /** false once the projectile is done (off the field or exploded). */
    private fun moveProjectile(a: Actor): Boolean {
        if (a.anim != null && !advanceAnim(a)) {
            // The two fireball images are a loop, not a sequence.
            a.animStep = 0
            a.animTics = GameData.ballAnim.tics[0]
        }
        a.x += a.momX
        a.y += a.momY
        if (a.x < 0 || a.y < 0 || a.x > worldWidth * FRACUNIT || a.y > worldHeight * FRACUNIT) return false
        // Safety net: a projectile fired at zero distance would carry no momentum and stay
        // in the scene forever. In the engine a wall would stop it; here there are none.
        if (tic - a.spawnTic > TICRATE * 5) return false

        for (o in actors) {
            if (o.creature == null || o.dead) continue
            if (o.isPlayer == a.firedByPlayer) continue          // no friendly fire
            if (approxDistance(a, o) < o.radius * FRACUNIT) {
                damageActor(o, a.damage)
                return false
            }
        }
        return true
    }

    private fun damageActor(target: Actor, amount: Int) {
        if (target.dead) return
        if (target.isPlayer && invulnerable) return
        val c = target.creature ?: return

        // p_inter.c: armour absorbs a third of the damage when green, half when blue, and
        // is consumed by the same amount. Once it runs out, the type is cleared too.
        // p_inter.c:799 — half damage to the player in trainer mode, before the armour. That
        // is the engine's own rule and belongs to the first level alone; the graded one below
        // is ours and covers the rest of the ladder. They compound on the first rung, which
        // is intended: that level is meant to be the gentlest thing this scene can be.
        var amount = if (target.isPlayer && rules.halfDamage) amount shr 1 else amount
        if (target.isPlayer) amount = amount * damageTakenScale(skill) / 100
        val kit = target.loadout
        if (kit != null && kit.armorType > 0) {
            var saved = GameData.armorSaved(amount, kit.armorType)
            if (kit.armorPoints <= saved) {
                saved = kit.armorPoints
                kit.armorType = 0
            }
            kit.armorPoints -= saved
            amount -= saved
        }

        target.health -= amount
        spawnBlood(target.x, target.y)

        if (target.health <= 0) {
            target.dead = true
            begin(target, Mode.DEATH, c.death)
            target.spawnTic = tic
            if (rules.respawn && !target.isPlayer && !target.respawned) {
                val i = GameData.creatures.indexOf(c)
                if (i >= 0) respawns.add(((tic + GameData.RESPAWN_DELAY) shl 3) or i)
            }
            return
        }
        // painchance: the odds of being interrupted by a hit.
        if (target.mode != Mode.ATTACK && pRandom() < c.painChance) {
            begin(target, Mode.PAIN, c.pain)
        }
    }

    // ---------------------------------------------------------------- movement

    /**
     * A_Chase (p_enemy.c): decide whether to attack, otherwise step forward and recompute
     * the direction whenever movecount runs out or the step fails.
     */
    private fun chase(a: Actor, index: Int) {
        val c = a.creature ?: return

        // A monster that has just appeared stands still: it materialises in the fog and
        // then wakes up, instead of bursting into a run out of nowhere.
        if (a.reactionTime > 0) {
            a.reactionTime--
            return
        }

        val target = enemyOf(a)
        val dist = if (target != null) approxDistance(a, target) else Int.MAX_VALUE

        // Below half health the marine breaks off and goes for supplies rather than trading
        // shots: staying in the fight while hurt is how he dies, and there is usually
        // something on the ground worth reaching.
        val hurt = a.isPlayer && a.health * 2 < GameData.player.health
        val supply = if (a.isPlayer && (hurt || dist > KEEP_AWAY)) nearestItem(a) else null
        val breakingOff = hurt && supply != null

        if (target != null) {
            if (a.isPlayer && dist < KEEP_AWAY) {
                // The marine does not walk into the demons: he backs off and shoots from a
                // distance. Charging them, he died every twenty seconds and never got past
                // the fifth wave.
                a.targetX = clampX(2 * a.x - target.x)
                a.targetY = clampY(2 * a.y - target.y)
            } else {
                a.targetX = target.x
                a.targetY = target.y
            }
            val inMelee = dist < MELEERANGE + target.radius * FRACUNIT
            val attacks = !breakingOff && (
                (c.meleeMod > 0 && inMelee) ||
                    ((c.hitscanShots > 0 || c.projectile >= 0) && checkMissileRange(a, target))
                )
            if (attacks) {
                a.facing = dirTo(a, target)          // A_FaceTarget: turn to shoot
                startAttack(a)
                return
            }
        } else if (actors.size > 0 && tic % actors.size == index && reachedTarget(a)) {
            // Work amortisation taken from P_LookForPlayers, which checks only 2 players per
            // call by cycling on lastlook: the expensive work is spread across several tics.
            newTarget(a)
        }

        // Heading for a pickup overrides whatever movement target was chosen above.
        if (supply != null) {
            a.targetX = supply.x
            a.targetY = supply.y
        }

        // P_TryWalk rearms movecount with P_Random()&15, so the pathfinding runs on average
        // once every 8 tics rather than 35 times a second.
        a.moveCount--
        if (a.moveCount < 0 || !move(a)) newChaseDir(a)
    }

    private fun reachedTarget(a: Actor): Boolean =
        abs(a.targetX - a.x) < 32 * FRACUNIT && abs(a.targetY - a.y) < 32 * FRACUNIT

    private fun newTarget(a: Actor) {
        a.targetX = randomIn(marginX, worldWidth - marginX) * FRACUNIT
        a.targetY = randomIn(marginTop, worldHeight - marginBottom) * FRACUNIT
    }

    /** P_Move: a step in the current direction via the xspeed/yspeed tables, all integer. */
    private fun move(a: Actor): Boolean {
        val d = a.moveDir
        if (d >= 8) return false
        val speed = (a.creature?.speed ?: return false) * (if (a.fast) 2 else 1)
        val tryX = a.x + speed * xspeed[d]
        val tryY = a.y + speed * yspeed[d]
        if (tryX < minX || tryX > maxX) return false
        if (tryY < minY || tryY > maxY) return false
        a.x = tryX
        a.y = tryY
        a.facing = d                 // walking: the sprite looks where it is going
        return true
    }

    /**
     * The direction from one actor to another, used as A_FaceTarget when an attack starts.
     *
     * Cardinal only, like movement. The engine resolves eight octants here via
     * R_PointToAngle, but returning a diagonal would put the diagonal sprite views back on
     * screen through the back door: an actor can be walking north and turn north-east to
     * shoot. Snapping to the dominant axis costs a little aiming precision that nothing in
     * the scene depends on, since damage is applied directly rather than along the facing.
     */
    private fun dirTo(from: Actor, to: Actor): Int {
        val dx = to.x - from.x
        val dy = to.y - from.y
        return if (abs(dx) >= abs(dy)) {
            if (dx > 0) 0 else 4                            // DI_EAST / DI_WEST
        } else {
            if (dy > 0) 2 else 6                            // DI_NORTH / DI_SOUTH
        }
    }

    /** P_TryWalk: when the step succeeds, rearm movecount with a random pause. */
    private fun tryWalk(a: Actor): Boolean {
        if (!move(a)) return false
        a.moveCount = pRandom() and 15
        return true
    }

    /**
     * P_NewChaseDir (p_enemy.c), in the original order: diagonal towards the target, then
     * the dominant axis, then the previous direction, then all 8 in random order.
     * **It never turns around** while an alternative exists: that is why the original monsters feel
     * alive instead of remote-controlled.
     */
    private fun newChaseDir(a: Actor) {
        val oldDir = a.moveDir
        val turnAround = opposite[oldDir]

        val deltaX = a.targetX - a.x
        val deltaY = a.targetY - a.y

        var d1 = when {
            deltaX > 10 * FRACUNIT -> 0                     // DI_EAST
            deltaX < -10 * FRACUNIT -> 4                    // DI_WEST
            else -> DI_NODIR
        }
        var d2 = when {
            deltaY < -10 * FRACUNIT -> 6                    // DI_SOUTH
            deltaY > 10 * FRACUNIT -> 2                     // DI_NORTH
            else -> DI_NODIR
        }

        // The engine tries the diagonal first here. Nobody in this scene moves diagonally at
        // all: everything reads more clearly along the axes, and it halves the sprite angles
        // in play, so only rotations 1, 3, 5 and 7 are ever decoded.
        if (pRandom() > 200 || abs(deltaY) > abs(deltaX)) {
            val t = d1; d1 = d2; d2 = t
        }
        if (d1 == turnAround) d1 = DI_NODIR
        if (d2 == turnAround) d2 = DI_NODIR

        if (d1 != DI_NODIR) { a.moveDir = d1; if (tryWalk(a)) return }
        if (d2 != DI_NODIR) { a.moveDir = d2; if (tryWalk(a)) return }
        if (oldDir != DI_NODIR) { a.moveDir = oldDir; if (tryWalk(a)) return }

        // Exhaustive fallback, still cardinals only. The random direction of travel through
        // the list is the engine's, and keeps a blocked actor from always picking the same
        // way out.
        if (pRandom() and 1 != 0) {
            for (d in CARDINALS) { if (d == turnAround) continue; a.moveDir = d; if (tryWalk(a)) return }
        } else {
            for (i in CARDINALS.indices.reversed()) {
                val d = CARDINALS[i]
                if (d == turnAround) continue
                a.moveDir = d
                if (tryWalk(a)) return
            }
        }

        if (turnAround != DI_NODIR) { a.moveDir = turnAround; if (tryWalk(a)) return }
        a.moveDir = DI_NODIR
    }

    /**
     * Sorts by depth (increasing y = further away). Insertion sort: the list is almost
     * always sorted already and the actors are few, so it costs O(n) and allocates nothing.
     */
    private fun sortByDepth() {
        for (i in 1 until actors.size) {
            val a = actors[i]
            var j = i - 1
            while (j >= 0 && actors[j].y > a.y) {
                actors[j + 1] = actors[j]
                j--
            }
            actors[j + 1] = a
        }
    }

    // internal rather than private so the tests can assert against these tuning values
    // instead of restating them, which would let the two drift apart silently.
    internal companion object {
        /**
         * How long a body stays before it is removed.
         *
         * ponytail: corpses in the original stay forever; in a wallpaper they would pile up.
         * Raised from twelve seconds to thirty, which the thinned wave table pays for: at 61
         * arrivals the bodies were the thing keeping the actor list long, and at 30 they are
         * what keeps the ground from looking empty between waves.
         */
        const val CORPSE_LIFETIME = TICRATE * 30

        /**
         * How tall a resting body has to be, against the standing marine of the same WAD, to
         * be drawn beneath the fight instead of in it.
         *
         * One means "as tall lying down as the marine is standing up", which is a strange
         * thing for a corpse to be and exactly what makes these worth moving. Measured on the
         * two files: it takes the Overlord (1.79) and the Cyberlord (1.43) out of Freedoom,
         * the Cyberdemon (2.39) out of Phase 2, and the Charger from both - its detonation
         * measures 1.14 and 1.61, and it is the widest thing on the screen.
         *
         * Big bodies used to be handled by removing them sooner, ten seconds against thirty.
         * That traded one problem for another: they still covered what they landed on, and
         * they vanished while the fight around them was still going. Everything lasts thirty
         * seconds again.
         */
        const val TALL_CORPSE = 1.0

        /**
         * How long the red screen lasts before restarting from the first wave.
         *
         * This is also the length of the fade, which now runs all the way to opaque, so it
         * is the whole of the descent rather than a pause with a tint over it.
         *
         * Four and a half seconds. Ten was tried early on and was unwatchable, but that was
         * a bright red at constant strength; a slow ramp into a dark one is a different
         * thing to sit through. With ARRIVAL_DELAY after it, six and a half seconds pass
         * between the marine falling and the next one landing.
         */
        const val DEATH_DELAY = TICRATE * 9 / 2

        /** Below this distance the marine backs off instead of closing in. */
        const val KEEP_AWAY = 220 * FRACUNIT

        /**
         * How far inside the edge a creature appears, in map units.
         *
         * It has to come from the sprite, not the collision radius: the widest creature has
         * a radius of 31 but an 83-pixel sprite anchored 50 pixels from its left edge, and
         * sprites are drawn at twice the world scale. Fifty anchor pixels are therefore a
         * hundred map units of screen space, which is what this has to clear.
         */
        const val SPAWN_MARGIN = 100

        /**
         * How far below the top edge an actor may stand, in map units.
         *
         * A sprite hangs above its anchor, so this is what keeps a whole creature on screen
         * at the top; the sideways margin above cannot serve, since the vertical reach is
         * larger and the two edges are not symmetric.
         *
         * Measured on the shipped assets rather than estimated. Reading the patch headers,
         * the tallest reach above the anchor is the PainLord at 74 pixels, against 61 for
         * the ShotgunZombie and 54 for the marine. A sprite pixel is two map units, because
         * the renderer draws sprites at SPRITE_SCALE and the world at PX_PER_UNIT, a ratio
         * of 3 to 1.5 that holds at every display density. Seventy-four pixels are therefore
         * 148 map units.
         *
         * If SPRITE_SCALE or PX_PER_UNIT ever change, this changes with them.
         */
        const val TOP_MARGIN = 148

        /**
         * How far above the bottom edge an actor may stand, in map units.
         *
         * Small on purpose: the sprite is drawn upwards from the anchor, so at the bottom
         * only the few pixels that hang below the feet can be clipped. Measured the same
         * way, the worst is the Trilobite at 8 pixels, so 16 map units.
         */
        const val BOTTOM_MARGIN = 16

        /**
         * Monster health as a percentage of the value in mobjinfo.
         *
         * A deliberate divergence, and the only one applied to a creature's own numbers.
         * Everything else about them — speed, animation timing, pain chance, damage — is the
         * original's. It is applied here, at spawn, rather than by editing GameData, so the
         * licensed values stay in the table with their provenance and this stays one number
         * in one place.
         *
         * It exists to pay for a slower supply of pickups. Difficulty over a table lasting
         * minutes is the balance between how fast the marine is worn down and how fast he
         * can heal; the drops were frequent enough to be visible clutter, so they were thinned
         * and the monsters softened to match. The marine's own 100 is untouched: it is
         * MAXHEALTH, and it is what the readout shows.
         */
        /**
         * How far above an actor's feet a shot leaves it, and where blood appears when one
         * lands. In map units.
         *
         * An actor's position is its anchor, which is where it stands: sprites are drawn
         * upwards from there. Firing from that point put every fireball on the floor. This is
         * roughly the chest of the marine, whose sprite stands about 100 units tall at the
         * scale the scene draws - the creatures vary, but they all fire from the same third
         * of their height, so one number reads correctly for all of them.
         */
        const val MUZZLE_HEIGHT = 34

        /**
         * The share of its original health a creature is given, as a percentage.
         *
         * ## The rule
         *
         * At the hardest skill nothing is scaled at all: a Cyberdemon has the 4000 health
         * `info.c` gives it, and every other creature the number the wiki prints. That is the
         * anchor the rest hangs from, and it makes Nightmare a declared wall rather than a
         * difficulty setting - it is not meant to be finished.
         *
         * Below that, the reduction grows with two things. With the wave, because a life has
         * to survive a whole table and it is the far end that ends it; and with how far down
         * the ladder the skill is, because that is what the ladder is for. Wave one is close
         * to untouched at every level - a Zombie has 20 health and dies to two pistol shots
         * either way - and the far end of the table is where the levels separate.
         *
         * ## Why it replaced two constants
         *
         * There used to be a flat 10% for everything and a further 25% for anything at or
         * above 1000 health. Two blunt numbers, and the second one inverted the bestiary: the
         * Baron, at 1000, came out with 25 health against the Mancubus's 60, so the wave table
         * got easier exactly where it was meant to peak. Scaling by position instead of by
         * size keeps every creature in the order `info.c` puts it in.
         */
        internal fun damageScale(skill: Int, wave: Int): Int {
            val lastSkill = (GameData.skills.size - 1).coerceAtLeast(1)
            val lastWave = (GameData.waves.size - 1).coerceAtLeast(1)
            // The whole bonus this skill is entitled to. Zero at the top of the ladder, which
            // is what anchors the hardest level to the printed numbers.
            val full = (MAX_DAMAGE - 100) * (lastSkill - skill.coerceIn(0, lastSkill)) / lastSkill
            // How much of it is already in force. Anchoring wave one at no bonus at all was
            // tried, when this lever was on the monsters' health, and is the wrong shape: a
            // level that has to be gentle has to be gentle from the beginning. The wave
            // decides the last stretch of it, not the bulk.
            val progress = wave.coerceIn(0, lastWave) * 100 / lastWave
            val applied = EARLY_SHARE + (100 - EARLY_SHARE) * progress / 100
            return 100 + full * applied / 100
        }

        /**
         * What the marine's damage is multiplied by, as a percentage, at the deepest wave of
         * the easiest level.
         *
         * Fitted rather than chosen: the target is that the easiest level reaches the
         * PainLord - the first creature the original prices at a thousand health, opening
         * wave 23 of 26 - as near to every life as this lever can manage.
         */
        const val MAX_DAMAGE = 3300

        /** How much of a skill's bonus is already in force at the first wave, percent. */
        const val EARLY_SHARE = 94

        /**
         * How much of a hit the marine actually takes, as a percentage, by skill.
         *
         * The other half of the ladder, and it exists because the first half ran out. Raising
         * what he deals stops helping once one shot kills anything: 3300, 5000 and 8000
         * percent all measured 94.0% at reaching the first boss, and the flatness says the
         * remaining deaths are not about how fast he kills. They are about what lands on him,
         * which is the thing this scales.
         *
         * A hundred at the hardest skill, so that level takes what the engine throws, and it
         * pairs with p_inter.c's own halfDamage rather than replacing it: that rule is the
         * engine's and belongs to the first level, this one is ours and grades the rest.
         */
        internal fun damageTakenScale(skill: Int): Int {
            val lastSkill = (GameData.skills.size - 1).coerceAtLeast(1)
            return MIN_TAKEN + (100 - MIN_TAKEN) * skill.coerceIn(0, lastSkill) / lastSkill
        }

        /** What the easiest level takes, before p_inter.c halves it again. */
        const val MIN_TAKEN = 80

        /** How long the marine stands still after materialising. */
        const val PLAYER_REACTION = TICRATE / 2

        /**
         * Empty ground before the marine arrives, at the start and after every death.
         *
         * Two seconds. Everything downstream shifts with it rather than being squeezed: the
         * wave is armed when he lands, so the first enemy is still a full spawnDelay behind
         * him and the opening keeps its shape.
         */
        const val ARRIVAL_DELAY = TICRATE * 2

        /** The only directions anything moves in: east, north, west, south. */
        val CARDINALS = intArrayOf(0, 2, 4, 6)

        /** How long an item stays if nobody picks it up. How often one drops is per skill. */
        const val ITEM_LIFETIME = TICRATE * 40
    }
}
