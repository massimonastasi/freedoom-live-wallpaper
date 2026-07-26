# FreedoomLiveWallpaper

An Android live wallpaper: an endless battle on your home screen, with movement speeds and
animation timings taken from the original engine source. Free, no ads, no permissions, no
network access.

**Status**: phases 1-4 of 11 complete, plus waves, weapons, armour and pickups.
Details and next steps in [docs/STATUS.md](docs/STATUS.md).

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

3. Download Freedoom from <https://freedoom.github.io> and extract `freedoom1.wad` into
   `app/src/main/assets/`. The file is not in the repo (see `.gitignore`).

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
