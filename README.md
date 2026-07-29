# Freedoom Live Wallpaper

An Android live wallpaper: an endless battle on your home screen, with movement speeds and
animation timings read from the original engine source. Free, no ads, no permissions, no
network access, nothing collected.

| The wallpaper | The settings |
|---|---|
| <img src="docs/images/scene.png" width="320" alt="A marine fighting among corpses, blood and dropped weapons"> | <img src="docs/images/settings.png" width="320" alt="The settings screen"> |

## What it does

- **Fourteen creatures** with the speeds, states, animation lengths and health `info.c` gives
  them, and `P_NewChaseDir` reproduced from the source: table-based movement, no trigonometry.
  This is why they move like the engine monsters rather than like something chasing a point.
- **Twenty-six waves and nine skill levels** — the five from `g_game.c` with four of ours
  between them, each fitted by measurement rather than by feel. The easiest reaches the first
  boss 99 times in 100; Nightmare is a wall by construction.
- **Combat**: melee, hitscan, missiles, pain, death, corpses. Missile damage goes through
  `PIT_CheckThing`'s multiplier, so a rocket is a rocket.
- **Your own sprites**: point it at any IWAD you already own and its sprites, palette, floors
  and numerals replace the bundled ones. The file is copied into the app and never leaves the
  device. No WAD is downloaded by this app, and none is linked from it.
- **Backgrounds** chosen by measuring the WAD's own flats — colour family, then saturation —
  or a flat palette colour, or a photograph of yours.
- **Tap** the home screen to drop a supply crate; **drop an icon** to spawn attackers there.

The scene always thinks at the original 35 tics a second. The frame rate is only how often it
is drawn, and it is a setting.

## Battery

The claim a live wallpaper has to answer. Thirteen hours unplugged, screen on for 100% of it,
this wallpaper active throughout, read from `batterystats` per uid:

| | mAh | share of the drain |
|---|---:|---:|
| Whole device | 2360 | |
| The screen | 486 | 20.6% |
| **This wallpaper** | **116** | **4.9%** |

**9.0 mA — 0.23% of a 4006 mAh battery per hour**, against a 2% target. An independent check
from `/proc/<pid>/stat` over the same period gives 12.7% of one core, matching the sixty-second
samples taken months earlier. A second method that bounds the same number from above puts the
ceiling at 0.93%/hour; the honest claim is the range. Hidden — any full-screen app, the screen
off — it costs **zero measured CPU ticks**.

Full working in [docs/STATUS.md](docs/STATUS.md).

## Where it comes from

The idea comes from **a live wallpaper**, made by **James Gittins** in 2011, which can no
longer be installed on a modern Android: it targets an API level the system now refuses. No
code and no asset is shared with it — this was written from nothing — but the thing it did is
the thing this does.

Particular thanks to **John Carmack**, for releasing the engine source. The movement, the
timings and the creature values here are read from it, and are the reason the scene feels
like the original rather than like an imitation of it.

And to the **Freedoom team**, whose artwork this draws. Without their twenty years of work
there would be nothing to show, because the alternative was shipping game data that is not
ours to give. Neither project endorses or is involved in this one.

## Install

A signed APK is attached to each [release](../../releases). Android will ask you to allow
installing from outside the store; that is expected for an app distributed this way.

After installing, open it and use **Set as wallpaper**, or go to
*Settings → Wallpaper → Live wallpapers*.

Requires Android 12 or newer.

## Build from source

1. Install Android Studio (it bundles the JDK, SDK, Gradle and adb):
   ```
   winget install --id Google.AndroidStudio -e
   ```
   On first launch: **Standard** setup wizard, then *SDK Manager → SDK Platforms* → tick
   **Android 16 (API 36)**.

2. Put `adb` on the PATH; it is needed for the battery measurements:
   ```
   winget install --id Google.PlatformTools -e
   ```

3. Download Freedoom from <https://freedoom.github.io>, put its `freedoom2.wad` at
   `app/wad/freedoom-full.wad`, and run:
   ```
   gradlew reduceWad
   ```
   That writes `app/src/main/assets/freedoom2.wad` containing only the lumps the wallpaper
   reads — 639 of 3610, 1.9 MB instead of 27.5 MB. Neither file is in the repository (see
   `.gitignore`), and the input stays outside `assets` so it is never packaged.

   **Phase 2, not Phase 1.** Freedoom Phase 1 is Phase 1 compatible and carries nine of the
   fourteen creatures this bestiary names; the five it lacks — the ChaingunZombie,
   BoneStalker, LesserLord, Spiderling and Bloater — were being substituted away at runtime,
   so the shipped wallpaper never showed a third of its own table. The balance measurements
   were taken with every creature present, which meant they described a file nobody had.
   Phase 2 has all fourteen. It costs 496 KB more in the APK.

4. Open the folder in Android Studio and run it on a physical phone, then go to
   *Settings → Wallpaper → Live wallpapers* and pick it.

The emulator works for rendering but is not representative of power consumption. `gradlew test`
runs 53 JVM tests, including an hour-long simulation that pins the top of the skill ladder;
none of them launches the app.

## Licences

- Code: **GPL-2.0**. The gameplay constants (speeds, state tables, physics) derive from
  `id-Software/the engine` (`linuxdoom-1.10`), GPL-2.0. Every value carries a comment naming
  its origin in the id source.
- Assets: **Freedoom**, 3-clause BSD licence. No commercial asset is redistributed.
- Users may optionally point the app at an IWAD they legally own. No commercial WAD is
  bundled with the app or downloadable from it.

Privacy policy: [PRIVACY.md](PRIVACY.md). It is short.

the engine is a trademark of ZeniMax Media Inc. This project is neither affiliated with nor
endorsed by ZeniMax or the Freedoom project.
