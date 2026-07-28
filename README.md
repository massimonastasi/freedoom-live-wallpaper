# FreedoomLiveWallpaper

An Android live wallpaper: an endless battle on your home screen, with movement speeds and
animation timings taken from the original engine source. Free, no ads, no permissions, no
network access.

## Where it comes from

The idea comes from **a live wallpaper**, made by **James Gittins** in 2011, which can no
longer be installed on a modern Android: it targets an API level the system now refuses. No
code and no asset is shared with it - this was written from nothing - but the thing it did is
the thing this does.

Particular thanks to **John Carmack**, for releasing the engine source. The movement, the
timings and the creature values here are read from it, and are the reason the scene feels
like the original rather than like an imitation of it.

And to the **Freedoom team**, whose artwork this draws. Without their twenty years of work
there would be nothing to show, because the alternative was shipping game data that is not
ours to give. Neither project endorses or is involved in this one.

**Status**: the scene, the settings and the licences screen are done; the wallpaper runs from
a signed release build. Details and next steps in [docs/STATUS.md](docs/STATUS.md).

## Setup

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
   reads — 639 of 3610, 1.9 MB instead of 27.5 MB. Neither file is in the repo (see
   `.gitignore`), and the input stays outside `assets` so it is never packaged.

   **Phase 2, not Phase 1.** Freedoom Phase 1 is Phase 1 compatible and carries nine of the
   fourteen creatures this bestiary names; the five it lacks — the ChaingunZombie,
   BoneStalker, LesserLord, Spiderling and Bloater — were being substituted away at runtime,
   so the shipped wallpaper never showed a third of its own table. The balance measurements
   were taken with every creature present, which meant they described a file nobody had.
   Phase 2 has all fourteen. It costs 496 KB more in the APK.

4. Open the folder in Android Studio and run it on a physical phone, then go to
   *Settings → Wallpaper → Live wallpapers* and pick Freedoom Live Wallpaper.

The emulator works for rendering but is not representative of power consumption.

## Licences

- Code: **GPL-2.0**. The gameplay constants (speeds, state tables, physics) derive from
  `id-Software/the engine` (`linuxdoom-1.10`), GPL-2.0. Every value carries a comment naming
  its origin in the id source.
- Assets: **Freedoom**, 3-clause BSD licence. No commercial asset is redistributed.
- Users may optionally point the app at an IWAD they legally own. No commercial WAD is
  bundled with the app or downloadable from it.

the engine is a trademark of ZeniMax Media Inc. This project is neither affiliated with nor
endorsed by ZeniMax or the Freedoom project.
