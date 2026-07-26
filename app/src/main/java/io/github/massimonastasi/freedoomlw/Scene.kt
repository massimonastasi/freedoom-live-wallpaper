package io.github.massimonastasi.freedoomlw

import io.github.massimonastasi.freedoomlw.the engineData.DI_NODIR
import io.github.massimonastasi.freedoomlw.the engineData.FRACUNIT
import io.github.massimonastasi.freedoomlw.the engineData.MELEERANGE
import io.github.massimonastasi.freedoomlw.the engineData.opposite
import io.github.massimonastasi.freedoomlw.the engineData.pRandom
import io.github.massimonastasi.freedoomlw.the engineData.xspeed
import io.github.massimonastasi.freedoomlw.the engineData.yspeed
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
    var weapon = the engineData.WEAPON_PISTOL
    val ammo = IntArray(the engineData.maxAmmo.size)

    fun copyFrom(other: Loadout) {
        armorPoints = other.armorPoints
        armorType = other.armorType
        weapon = other.weapon
        other.ammo.copyInto(ammo)
    }
}

/**
 * An actor in the scene: creature, projectile, effect or pickup.
 *
 * Positions are in the engine map units, 16.16 fixed-point like the original: no drift, and the
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
    var creature: the engineData.Creature? = null
    var isPlayer = false

    /** Animation in progress for every mode other than WALK. */
    var anim: the engineData.Anim? = null
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

    /** Tics of stillness after appearing: info.c mobjinfo.reactiontime. */
    var reactionTime = 0

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
    var item: the engineData.Item? = null

    val radius get() = creature?.radius ?: 6

    /** The frame to draw right now. */
    fun frame(tic: Int): Int {
        val a = anim
        if (a != null) return a.frames[animStep]
        item?.let { return if (it.frames > 1) ((tic - spawnTic) / 6) % it.frames else 0 }
        val c = creature ?: return 0
        // Walking: the engine repeats each frame twice (A,A,B,B,...), so it lasts walkTics*2.
        val per = c.walkTics * 2
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
     * The engine formula in r_things.c looks like a reflection instead, because the engine
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
 * The world is the engine's x/y plane in map units. The projection onto the screen is oblique:
 * x horizontal, y into the depth. There are no walls, so the P_CheckSight line-of-sight
 * test is unnecessary — here the firing line really is always clear, it is not a shortcut.
 */
class Scene(
    private val worldWidth: Int,
    private val worldHeight: Int,
) {

    val actors = ArrayList<Actor>()

    /** Setting: the marine never dies (phase 9). */
    var invulnerable = false

    /** Current wave, zero-based. */
    var wave = 0
        private set

    /** What the corner readout shows. Zero while the marine is down. */
    val playerHealth: Int get() = player?.takeIf { !it.dead }?.health?.coerceAtLeast(0) ?: 0
    val playerArmor: Int get() = player?.takeIf { !it.dead }?.loadout?.armorPoints ?: 0

    private var tic = 0
    private var player: Actor? = null
    private var nextWaveAt = 0
    private var deadUntil = 0

    /** Equipment that survives death. */
    private val kept = Loadout()

    /** Arrival queue for the current wave, and how far along it we are. */
    private var spawned = IntArray(0)
    private var spawnIndex = 0
    private var nextSpawnAt = 0

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
        if (deadUntil == 0 && tic % ITEM_INTERVAL == 0) spawnItem()

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
            // The marine appears first, on his own: the first enemy arrives after the
            // wave delay, not alongside him.
            spawnPlayer()
            startWave()
            return
        }
        if (p.dead) {
            deadUntil = tic + DEATH_DELAY
            return
        }

        // Staggered arrivals: until the whole wave is in, we do not judge it finished.
        if (spawnIndex < spawned.size) {
            if (tic >= nextSpawnAt) {
                val w = the engineData.waves[wave]
                var n = w.burst
                while (n-- > 0 && spawnIndex < spawned.size) {
                    spawnDemon(the engineData.creatures[spawned[spawnIndex++]])
                }
                nextSpawnAt = tic + w.spawnDelay
            }
            return
        }

        if (countDemons() > 0) {
            nextWaveAt = 0
            return
        }
        // Wave cleared: the pause is the one belonging to the wave just finished. Breathing
        // room between waves matters, otherwise the rhythm turns into continuous noise.
        if (nextWaveAt == 0) {
            nextWaveAt = tic + the engineData.waves[wave].rest
            return
        }
        if (tic >= nextWaveAt) {
            nextWaveAt = 0
            wave = (wave + 1) % the engineData.waves.size
            startWave()
        }
    }

    /** After death everything restarts from the first wave: dying wipes the progress. */
    private fun restart() {
        player?.loadout?.let { kept.copyFrom(it) }
        actors.clear()
        player = null
        deadUntil = 0
        nextWaveAt = 0
        wave = 0
        spawnPlayer()
        startWave()
    }

    /** Arms the wave: nobody enters immediately, the first arrives after spawnDelay. */
    private fun startWave() {
        val w = the engineData.waves[wave]
        spawned = w.order
        spawnIndex = 0
        nextSpawnAt = tic + w.spawnDelay
    }

    // ---------------------------------------------------------------- spawning

    /**
     * P_Random returns 0-255: in the original engine it serves probabilities and small
     * offsets, never the choice of a point on a map. Using it with `%` over a range larger
     * than 256 would confine everything to one corner, so it gets scaled instead.
     */
    private fun randomIn(min: Int, max: Int): Int = min + pRandom() * (max - min) / 256

    private fun spawnPlayer() {
        val a = newCreature(the engineData.player)
        a.isPlayer = true
        // The waves restart from scratch, the arsenal does not: without that continuity the
        // marine stayed on the pistol forever in the early waves and half the bestiary was
        // never seen.
        a.loadout = Loadout().apply { copyFrom(kept) }
        a.x = randomIn(SPAWN_MARGIN, worldWidth - SPAWN_MARGIN) * FRACUNIT
        a.y = randomIn(SPAWN_MARGIN, worldHeight - SPAWN_MARGIN) * FRACUNIT
        // Longer than the creatures' reactiontime of 8 tics, which at under a quarter of a
        // second reads as no pause at all. He arrives alone and the first enemy is seconds
        // away, so he can afford to stand in the fog long enough to be noticed.
        a.reactionTime = PLAYER_REACTION
        spawnFog(a.x, a.y)
        actors.add(a)
        player = a
        newTarget(a)
    }

    private fun newCreature(c: the engineData.Creature): Actor {
        val a = Actor(c.spriteIndex)
        a.creature = c
        a.health = c.health
        a.spawnTic = tic
        a.reactionTime = the engineData.REACTION_TIME
        return a
    }

    private fun spawnDemon(c: the engineData.Creature) {
        val a = newCreature(c)
        // Enters from a side edge, but a whole sprite width inside it: arriving exactly on
        // the boundary left half the creature off screen, and a quick kill could then remove
        // it before it had ever been properly seen.
        a.x = (if (pRandom() and 1 == 0) SPAWN_MARGIN else worldWidth - SPAWN_MARGIN) * FRACUNIT
        a.y = randomIn(SPAWN_MARGIN, worldHeight - SPAWN_MARGIN) * FRACUNIT
        spawnFog(a.x, a.y)
        actors.add(a)
        newTarget(a)
    }

    private fun spawnEffect(spriteIndex: Int, anim: the engineData.Anim, x: Int, y: Int) {
        val a = Actor(spriteIndex)
        a.mode = Mode.EFFECT
        a.anim = anim
        a.animTics = anim.tics[0]
        a.x = x
        a.y = y
        a.spawnTic = tic
        actors.add(a)
    }

    private fun spawnFog(x: Int, y: Int) = spawnEffect(the engineData.fogSpriteIndex, the engineData.fogAnim, x, y)

    private fun spawnBlood(x: Int, y: Int) = spawnEffect(the engineData.bloodSpriteIndex, the engineData.bloodAnim, x, y)

    /** Drops a pickup somewhere on the map, or at a chosen spot. */
    private fun spawnItem(
        x: Int = randomIn(SPAWN_MARGIN, worldWidth - SPAWN_MARGIN) * FRACUNIT,
        y: Int = randomIn(SPAWN_MARGIN, worldHeight - SPAWN_MARGIN) * FRACUNIT,
    ) {
        val it = the engineData.items[pRandom() % the engineData.items.size]
        val a = Actor(it.spriteIndex)
        a.mode = Mode.ITEM
        a.item = it
        a.x = clampX(x)
        a.y = clampY(y)
        a.spawnTic = tic
        actors.add(a)
    }

    private fun clampX(x: Int) =
        x.coerceIn(SPAWN_MARGIN * FRACUNIT, (worldWidth - SPAWN_MARGIN) * FRACUNIT)

    private fun clampY(y: Int) =
        y.coerceIn(SPAWN_MARGIN * FRACUNIT, (worldHeight - SPAWN_MARGIN) * FRACUNIT)

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
            val c = the engineData.creatures[pRandom() % 3]        // only the lighter creatures
            val a = newCreature(c)
            a.x = clampX(x + (pRandom() - 128) * FRACUNIT / 2)
            a.y = clampY(y + (pRandom() - 128) * FRACUNIT / 2)
            spawnFog(a.x, a.y)
            actors.add(a)
            newTarget(a)
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
    private fun pickUp(p: Actor, it: the engineData.Item): Boolean {
        val kit = p.loadout ?: return false
        return when (it.kind) {
            the engineData.ITEM_HEALTH -> {
                if (p.health >= the engineData.player.health) false
                else { p.health = minOf(the engineData.player.health, p.health + it.amount); true }
            }
            the engineData.ITEM_ARMOR -> {
                if (kit.armorPoints >= it.amount) false
                else { kit.armorPoints = it.amount; kit.armorType = it.extra; true }
            }
            the engineData.ITEM_WEAPON -> {
                giveAmmo(kit, the engineData.weapons[it.extra].ammo, it.amount)
                if (it.extra > kit.weapon) kit.weapon = it.extra
                true
            }
            else -> giveAmmo(kit, it.extra, it.amount)
        }
    }

    private fun giveAmmo(kit: Loadout, type: Int, clips: Int): Boolean {
        if (type < 0) return false
        if (kit.ammo[type] >= the engineData.maxAmmo[type]) return false
        kit.ammo[type] = minOf(the engineData.maxAmmo[type], kit.ammo[type] + clips * the engineData.clipAmmo[type])
        return true
    }

    /** The best owned weapon that still has ammunition. */
    private fun currentWeapon(p: Actor): the engineData.Weapon {
        val kit = p.loadout ?: return the engineData.weapons[the engineData.WEAPON_PISTOL]
        for (i in kit.weapon downTo 0) {
            val w = the engineData.weapons[i]
            if (w.ammo < 0 || kit.ammo[w.ammo] > 0) return w
        }
        return the engineData.weapons[the engineData.WEAPON_PISTOL]
    }

    // ---------------------------------------------------------------- animation

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
        val anim = if (a.isPlayer) currentWeapon(a).attack else c.attack
        a.mode = Mode.ATTACK
        a.anim = anim
        a.animStep = 0
        a.animTics = anim.tics[0]
    }

    private fun fireAttack(a: Actor) {
        val c = a.creature ?: return
        val target = enemyOf(a) ?: return

        // Melee when the target is in reach: P_CheckMeleeRange uses MELEERANGE.
        if (c.meleeMod > 0 && approxDistance(a, target) < MELEERANGE + target.radius * FRACUNIT) {
            damageActor(target, (pRandom() % c.meleeMod + 1) * c.meleeMul, a.isPlayer)
            return
        }
        if (c.hitscanShots > 0) {
            // Instant shot: no projectile to simulate, damage applied directly.
            if (a.isPlayer) {
                // p_pspr.c: every pellet deals 5*(P_Random()%3+1). Only the pellet count
                // changes: one for pistol and chaingun, seven for the shotgun.
                val w = currentWeapon(a)
                if (w.ammo >= 0) a.loadout?.ammo?.let { it[w.ammo]-- }
                var total = 0
                repeat(w.pellets) { total += the engineData.gunShotDamage() }
                damageActor(target, total, true)
            } else {
                repeat(c.hitscanShots) { damageActor(target, the engineData.hitscanDamage(), false) }
            }
            return
        }
        if (c.projectile >= 0) spawnMissile(a, target, the engineData.projectiles[c.projectile])
    }

    /** P_SpawnMissile: constant speed along the direction of the target. */
    private fun spawnMissile(from: Actor, target: Actor, p: the engineData.Projectile) {
        val m = Actor(p.spriteIndex)
        m.mode = Mode.PROJECTILE
        m.anim = the engineData.ballAnim
        m.animTics = the engineData.ballAnim.tics[0]
        m.x = from.x
        m.y = from.y
        m.spawnTic = tic
        m.damage = p.damage
        m.firedByPlayer = from.isPlayer

        val dx = target.x - from.x
        val dy = target.y - from.y
        val dist = approxDistance(from, target).coerceAtLeast(1)
        // Momentum is in fixed-point: p.speed is units per tic, as in mobjinfo where the
        // missiles have speed 10*FRACUNIT (monsters instead carry a plain integer).
        val v = (p.speed * FRACUNIT).toLong()
        m.momX = (v * dx / dist).toInt()
        m.momY = (v * dy / dist).toInt()
        actors.add(m)
    }

    /** false once the projectile is done (off the field or exploded). */
    private fun moveProjectile(a: Actor): Boolean {
        if (a.anim != null && !advanceAnim(a)) {
            // The two fireball images are a loop, not a sequence.
            a.animStep = 0
            a.animTics = the engineData.ballAnim.tics[0]
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
                damageActor(o, a.damage, a.firedByPlayer)
                return false
            }
        }
        return true
    }

    private fun damageActor(target: Actor, amount: Int, byPlayer: Boolean) {
        if (target.dead) return
        if (target.isPlayer && invulnerable) return
        val c = target.creature ?: return

        // p_inter.c: armour absorbs a third of the damage when green, half when blue, and
        // is consumed by the same amount. Once it runs out, the type is cleared too.
        var amount = amount
        val kit = target.loadout
        if (kit != null && kit.armorType > 0) {
            var saved = the engineData.armorSaved(amount, kit.armorType)
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
            target.mode = Mode.DEATH
            target.anim = c.death
            target.animStep = 0
            target.animTics = c.death.tics[0]
            target.spawnTic = tic
            return
        }
        // painchance: the odds of being interrupted by a hit.
        if (target.mode != Mode.ATTACK && pRandom() < c.painChance) {
            target.mode = Mode.PAIN
            target.anim = c.pain
            target.animStep = 0
            target.animTics = c.pain.tics[0]
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
        val hurt = a.isPlayer && a.health * 2 < the engineData.player.health
        val supply = if (a.isPlayer && (hurt || dist > KEEP_AWAY)) nearestItem(a) else null
        val breakingOff = hurt && supply != null

        if (target != null) {
            if (a.isPlayer && dist < KEEP_AWAY) {
                // The marine does not walk into the demons: he backs off and shoots from a
                // distance. Charging them, he died every twenty seconds and never got past
                // the fifth wave.
                a.targetX = (2 * a.x - target.x).coerceIn(a.radius * FRACUNIT, (worldWidth - a.radius) * FRACUNIT)
                a.targetY = (2 * a.y - target.y).coerceIn(a.radius * FRACUNIT, (worldHeight - a.radius) * FRACUNIT)
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
        a.targetX = randomIn(a.radius, worldWidth - a.radius) * FRACUNIT
        a.targetY = randomIn(a.radius, worldHeight - a.radius) * FRACUNIT
    }

    /** P_Move: a step in the current direction via the xspeed/yspeed tables, all integer. */
    private fun move(a: Actor): Boolean {
        val d = a.moveDir
        if (d >= 8) return false
        val speed = a.creature?.speed ?: return false
        val tryX = a.x + speed * xspeed[d]
        val tryY = a.y + speed * yspeed[d]
        val r = a.radius * FRACUNIT
        if (tryX < r || tryX > (worldWidth * FRACUNIT) - r) return false
        if (tryY < r || tryY > (worldHeight * FRACUNIT) - r) return false
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
     * **It never turns around** while an alternative exists: that is why the engine monsters feel
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

    private companion object {
        /** ponytail: the engine corpses stay forever; in a wallpaper they would pile up. */
        const val CORPSE_LIFETIME = TICRATE * 12

        /**
         * How long the red screen lasts before restarting from the first wave.
         * Kept short: at ten seconds the marine died often enough to leave the screen red
         * half the time, which is unwatchable as a wallpaper.
         */
        const val DEATH_DELAY = TICRATE * 3

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

        /** How long the marine stands still after materialising. */
        const val PLAYER_REACTION = TICRATE / 2

        /** The only directions anything moves in: east, north, west, south. */
        val CARDINALS = intArrayOf(0, 2, 4, 6)

        /** How often an item drops, and how long it stays if nobody picks it up. */
        const val ITEM_INTERVAL = TICRATE * 12
        const val ITEM_LIFETIME = TICRATE * 40
    }
}
