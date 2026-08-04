# Balance reference — monsters, weapons, and the marine

The values applied in the code after the flat-balance rebalance. Attack types, speeds, timings
and the marine's own mechanics are still `linuxdoom-1.10` / `info.c`; the health and weapon-damage
numbers are rebalanced for the wallpaper and are ours. When the code changes, this file changes
with it.

## Monsters — health and attack

Health and damage are both rebalanced (the `info.c` health originals are in the last column, for
provenance). The roster climbs monotonically to the Overlord, which closes it, and damage climbs
with it: **damage is the row number**, 1 through 14.

Damage is one **fixed** whole number per attack, exactly as the marine's weapons are fixed — no
roll, no multiplier, no shot count. A creature with both melee and a missile deals the same
figure either way; which column it has says only how the damage is delivered.

| # | Monster | Sprite | HP | Damage | Attack | info.c HP |
|---:|---|---|---:|---:|---|---:|
| 0 | Zombie | POSS | 1 | 1 | hitscan | 20 |
| 1 | ShotgunZombie | SPOS | 2 | 2 | hitscan | 30 |
| 2 | Serpentipede | TROO | 3 | 3 | melee + fireball (BAL1) | 60 |
| 3 | ChaingunZombie | CPOS | 4 | 4 | hitscan | 70 |
| 4 | Charger | SKUL | 5 | 5 | melee (flying) | 100 |
| 5 | FleshWorm | SARG | 8 | 6 | melee | 150 |
| 6 | BoneStalker | SKEL | 10 | 7 | melee + tracer (FATB) | 300 |
| 7 | Trilobite | HEAD | 15 | 8 | melee + fireball (BAL1) | 400 |
| 8 | LesserLord | BOS2 | 20 | 9 | melee + bruiser shot (BAL7) | 500 |
| 9 | Spiderling | BSPI | 25 | 10 | plasma (APLS) | 500 |
| 10 | Bloater | FATT | 30 | 11 | fat spread (MANF) | 600 |
| 11 | PainLord | BOSS | 45 | 12 | melee + bruiser shot (BAL7) | 1000 |
| 12 | Cyberlord | CYBR | 60 | 13 | rockets (MISL) | 4000 |
| 13 | Overlord | SPID | 70 | 14 | hitscan | 3000 |

What this replaced: `(P_Random()%8+1)*3` claws, `((P_Random()%5)+1)*3` per instant shot with a
shot count on top, and `((P_Random()%8)+1) * mobjinfo.damage` for every missile. The rolls are
gone for the same reason the skill levers went — nobody is playing this, so the spread buys
nothing, and a monster's damage can now be read against the health printed beside it. Missiles
carry no damage of their own any more: the shooter says what its missile deals.

`BOSS_FROM = 45`: PainLord and up are bosses (never a stand-in for a missing WAD creature).

## The marine

| | |
|---|---:|
| Health | 100 (MAXHEALTH) |
| Green armour | 100 points, absorbs 1/3 of each hit |
| Blue armour | 200 points, absorbs 1/2 of each hit |

He takes monster damage **in full** — no skill scaling sits between the monster and him. At 100
health, the worst single hit in the table (the Overlord's 14) is a seventh of him.

## Weapons — damage and ammunition

Damage is a **fixed** value per trigger pull (no roll, no skill multiplier). Picked-up ammo is
5 clip loads of `clipAmmo = {bullets 10, shells 4, cells 20, rockets 1}`.

| Weapon | Sprite | Ammo | Max | Picked up | Damage | Per second |
|---|---|---|---:|---:|---:|---:|
| Pistol | PIST | — (never empties) | ∞ | — (starting) | 1 | 2 |
| Chaingun | MGUN | bullets | 200 | 50 | 1 | 5 |
| Shotgun | SHOT | shells | 50 | 20 | 7 | 10 |
| SuperShotgun | SGN2 | shells | 50 | 20 | 20 | 25 |
| PlasmaRifle | PLAS | cells | 300 | 100 | 2 | 11 |
| RocketLauncher | LAUN | rockets | 50 | 5 | 10 | 11 |

Shotgun and SuperShotgun share the 50 shells. The arsenal always fires the highest
`damagePerSecond` it holds (`damage * 35 / firing-animation tics`) — a whole number, not the
fraction per tic it used to be, because the ranking is the same either way and nothing else
reads it. A missile weapon's missile carries the same fixed figure it does; the `*4.5` that
used to be applied to every missile in flight is gone with the rolls.

## Difficulty — flat

The skill ladder's combat levers are **removed**: no marine-damage scaling (`damageScale`,
`marineDamage`), no damage-taken scaling (`damageTakenScale`, trainer half-damage), no `toughen`
promotion, no `fast`, no `respawn`, no double-ammo. Monster health is fixed per the table.

Every skill now plays **identically**: same monsters, same health, same damage, and a single
drop interval of **30 seconds** (`TICRATE * 30`) for all, all running 26 waves. The nine names
are all that is left to choose between. Open: whether to collapse them into one and drop the
picker.

## Wave table — the rising-weight curve

Built. The 26 waves still alternate solo / escorted arrivals, but the only thing that escalates
is **total wave health**, and it never falls:

| Wave | Creatures | HP | | Wave | Creatures | HP |
|---:|---|---:|---|---:|---|---:|
| 1 | Zombie | 1 | | 14 | Trilobite | 15 |
| 2 | ShotgunZombie | 2 | | 15 | Charger + Trilobite | 20 |
| 3 | Zombie + ShotgunZombie | 3 | | 16 | LesserLord | 20 |
| 4 | Serpentipede | 3 | | 17 | Charger + LesserLord | 25 |
| 5 | Zombie + Serpentipede | 4 | | 18 | Spiderling | 25 |
| 6 | ChaingunZombie | 4 | | 19 | Charger + Spiderling | 30 |
| 7 | Zombie + ChaingunZombie | 5 | | 20 | Bloater | 30 |
| 8 | Charger | 5 | | 21 | Trilobite + Bloater | 45 |
| 9 | Serpentipede + Charger | 8 | | 22 | PainLord | 45 |
| 10 | FleshWorm | 8 | | 23 | Trilobite + PainLord | 60 |
| 11 | ShotgunZombie + FleshWorm | 10 | | 24 | Cyberlord | 60 |
| 12 | BoneStalker | 10 | | 25 | BoneStalker + Cyberlord | 70 |
| 13 | Charger + BoneStalker | 15 | | 26 | Overlord | 70 |

The escort is chosen for **weight, not rank**: whatever keeps the pair's total from overshooting
the health of the next creature to enter alone. That is what removes the old dip — under the
previous table wave 6 put 5 HP on the field and wave 7 put 4.

- `SPAWN_DELAY = TICRATE` — one second between arrivals, every wave. The per-wave `spawnDelay`
  and the dead time before a wave's first arrival are both gone; the first creature arrives on
  the tic the wave is armed. Matches `SPAWN_MIN_DISTANCE = 280` (~1 s of walking).
- `Wave.burst` is gone. Arrivals a second apart put a pair on the field together anyway. The one
  case that must still land on a single tic — the doubled arrival compensating a deep WAD
  substitution — does so because `Scene` spawns a consecutive repeat in the queue together.
- `rest` is `TICRATE * 5 / 4` throughout, except 2 s after the Cyberlord and 3 s after the Overlord.
- **No live cap.** With no wave larger than two, a cap would never bind. Add one when a wave does.

Table floor: 46.5 s of pure waiting, 38 arrivals over 26 waves.

## Survival — open

`WaveReachTest` over 400 runs: **mean life 64.3 s, and 0% reach the last wave.** Lives now end
somewhere in waves 14–23, most often 21.

The 23.5 s that stood here before was measured with monster damage still at the `info.c` rolls
while monster health had already been cut some fifty-fold — the marine was fighting 1-HP zombies
that hit for 3 to 15. Fixing the damage table fixed most of that: 23.5 s → 64.3 s, with no change
to the wave table, the drop interval or the marine.

What is still open is the last third of the table. Nobody reaches wave 26, and the table floor is
46.5 s of pure waiting, so a life that ends at 64 s is dying with most of the roster unseen.
