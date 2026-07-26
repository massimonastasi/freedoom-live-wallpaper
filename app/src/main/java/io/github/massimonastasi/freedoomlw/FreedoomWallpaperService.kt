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

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import java.nio.channels.FileChannel

/** The original engine runs at 35 tics per second. All game logic advances at that rate, always. */
const val TICRATE = 35

/** Frames drawn per second. Independent of TICRATE: we draw less often than we simulate. */
// Measured on a Pixel 6a, scene with 8 actors, wallpaper visible:
//   20 fps -> 12.0% of one core   (gfxinfo: 5 ms/frame, 0 janky frames)
//   10 fps -> 9.2% of one core
// Not linear: there is a fixed ~6.4% cost independent of the frame rate, still
// unidentified. Perfetto profiling belongs to phase 7, where the plan puts it.
private const val DRAW_FPS = 20

private const val TAG = "FreedoomLW"

class FreedoomWallpaperService : WallpaperService() {

    /** One SpriteSet per prefix, indexed like GameData.spritePrefixes. */
    private var sprites: List<SpriteSet> = emptyList()

    /** Red damage flash colour, read from PLAYPAL. */
    private var damageTint = 0xFFAA1400.toInt()

    /** Colour of the death wash, straight from the active WAD's palette. */
    private var deathTint = 0xFFB30000.toInt()

    /** Floor texture per skill level, tiled behind the scene. Null entries when absent. */
    private var floorTiles: Array<Bitmap?> = arrayOfNulls(GameData.skills.size)

    /** Which flat each skill actually resolved to, for the debug overlay. */
    private var floorNames: Array<String> = Array(GameData.skills.size) { "?" }

    /** Status bar numerals from the WAD. */
    private var digits: Array<Bitmap>? = null

    /**
     * Readout colours, replaced by the active WAD's palette once it loads.
     *
     * Health is the blue and armour the green, matching PALETTE_HEALTH and PALETTE_ARMOR.
     * These two defaults used to hold the opposite pair, which no drawn frame could reveal
     * because the readout is skipped entirely when the numerals fail to load — wrong in a
     * place that never shows is still wrong, and it would have surfaced the moment anything
     * else started using them.
     */
    private var healthColor = 0xFF7373FF.toInt()
    private var armorColor = 0xFF77FF6F.toInt()

    override fun onCreate() {
        super.onCreate()
        try {
            // The WAD is never copied: it is memory-mapped straight from the assets
            // (build.gradle.kts excludes it from compression, so it is readable in place).
            val afd = assets.openFd("freedoom1.wad")
            val buf = afd.createInputStream().use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.length)
            }
            val w = WadFile(buf)
            // Only the creatures whose sprites actually exist in this WAD: the file
            // declares itself, no per-IWAD compatibility table needed.
            damageTint = w.damageTint
            deathTint = w.paletteColor(GameData.PALETTE_DEATH)
            floorTiles = loadFloors(w)
            loadDigits(w)
            sprites = GameData.spritePrefixes.map { SpriteSet(w, it) }
            val missing = GameData.spritePrefixes.filterIndexed { i, _ -> sprites[i].frameCount == 0 }
            Log.i(TAG, "WAD loaded: ${w.lumpCount} lumps, ${sprites.size - missing.size}/${sprites.size} sprites" +
                if (missing.isEmpty()) "" else " (missing: $missing)")
        } catch (e: Exception) {
            // Without a WAD the wallpaper stays alive and shows the placeholder rather
            // than disappearing.
            Log.e(TAG, "WAD not loaded", e)
        }
    }

    /**
     * One floor flat per skill level, so the ground reports the difficulty.
     *
     * A wallpaper sits *behind* the launcher icons, so a backdrop has to stay quiet. Every
     * flat in the IWAD was measured on mean luminance, spread and chroma, and then the
     * shortlist was decoded and **looked at**, which is the step that mattered: FLOOR1_7
     * measures as an ordinary dark red and is really two glaring panels, and GATE1 is a
     * circular emblem that tiles into a repeating logo.
     *
     * The five chosen all sit between 28 and 38 luminance, so the ladder climbs by hue while
     * the contrast behind the icons stays put. See GameData.Skill.flat.
     *
     * Each falls back down a shared chain, because a user-supplied WAD need not carry them
     * all — a WAD with only one usable flat simply shows the same ground at every level.
     */
    private fun loadFloors(w: WadFile): Array<Bitmap?> = Array(GameData.skills.size) { skill ->
        val wanted = listOf(GameData.skills[skill].flat) + FLOOR_FALLBACKS
        wanted.firstNotNullOfOrNull { name ->
            val i = w.flatIndex(name)
            if (i < 0) return@firstNotNullOfOrNull null
            val f = w.decodeFlat(i)
            Log.i(TAG, "floor for ${GameData.skills[skill].name}: $name")
            floorNames[skill] = name
            Bitmap.createBitmap(f.pixels, f.width, f.height, Bitmap.Config.ARGB_8888)
        }
    }

    /**
     * Loads the status bar numerals, so the corner readout is drawn with the WAD's own
     * glyphs rather than a bundled font. They decode with the ordinary patch reader, and a
     * user-supplied IWAD brings its own digits along with its own sprites.
     */
    private fun loadDigits(w: WadFile) {
        fun lump(name: String): Bitmap? {
            val i = w.indexOf(name)
            if (i < 0) return null
            val p = w.decodePatch(i)
            return Bitmap.createBitmap(p.pixels, p.width, p.height, Bitmap.Config.ARGB_8888)
        }
        val loaded = Array(10) { lump("STTNUM$it") ?: return }
        digits = loaded
        healthColor = w.paletteColor(GameData.PALETTE_HEALTH)
        armorColor = w.paletteColor(GameData.PALETTE_ARMOR)
    }

    override fun onCreateEngine(): Engine = FreedoomEngine()

    private inner class FreedoomEngine : WallpaperService.Engine() {

        // ponytail: a Handler on the wallpaper process main looper, not a dedicated
        // thread. The process is ours alone, so no synchronisation with surfaceDestroyed
        // is needed.
        private val handler = Handler(Looper.getMainLooper())

        /**
         * Everything is drawn dimmed. A wallpaper competing with the launcher icons is a
         * bad wallpaper: sprites and floor texture together were bright enough to make
         * icons hard to read, so the whole scene is scaled down in brightness. Applied as
         * a colour filter rather than a translucent overlay, so it costs nothing per frame.
         */
        private fun dimBy(amount: Float) = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    amount, 0f, 0f, 0f, 0f,
                    0f, amount, 0f, 0f, 0f,
                    0f, 0f, amount, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )

        private val dim = dimBy(SCENE_DIM)

        /**
         * The floor is dimmed harder than the sprites.
         *
         * One shared value could not serve both. The backdrop covers the whole screen and
         * only has to hint that there is ground; the sprites are the thing being watched and
         * have to stay legible. Tuned to the same brightness the previous, textureless
         * backdrop happened to land on, so the ground reads as barely there while keeping
         * the structure that stops it looking like a black screen.
         */
        private val floorDim = dimBy(FLOOR_DIM)

        private val paint = Paint().apply {
            isFilterBitmap = false
            colorFilter = dim
        }

        /** Tiled floor. Its shader matrix carries the home screen parallax. */
        private val floorPaint = Paint().apply {
            isFilterBitmap = false
            colorFilter = floorDim
        }
        private val floorMatrix = Matrix()
        private var offset = 0.5f

        private val matrix = Matrix()
        private val frame = Rect()

        private var visible = false
        private var tic = 0
        private var lastNanos = 0L
        private var ticAccumulator = 0L
        private var scene: Scene? = null

        /**
         * Both scales follow the display density, so a map unit and a sprite pixel keep a
         * constant *physical* size on any screen.
         *
         * Without this the constants are raw pixels: on a 560 dpi phone the marine would be
         * two thirds the size he is here, and on a 240 dpi tablet half again as large. The
         * world, measured in map units, is then free to vary with the physical size of the
         * screen, which is what should happen — a bigger display shows more of the scene
         * rather than the same scene magnified.
         *
         * The reference is the density these values were tuned on.
         */
        private val densityScale = resources.displayMetrics.density / REFERENCE_DENSITY
        private val pxPerUnit = PX_PER_UNIT * densityScale
        private val spriteScale = SPRITE_SCALE * densityScale

        private val drawRunnable = Runnable { step() }

        // isPowerSaveMode is an IPC call into PowerManagerService: querying it every frame
        // meant 20 transactions per second for a value that changes maybe once a day.
        // Sampled once per second instead.
        private val powerManager by lazy { getSystemService(POWER_SERVICE) as PowerManager }
        private var powerSave = false
        private var powerSaveCheckedAt = 0L

        /** Frame rate last declared to the compositor, so we only declare it on change. */
        private var declaredFps = 0f

        // Becomes a setting in phase 9. The corner is no longer a choice: the two readings
        // take one bottom corner each, so there is nothing left to place.
        private var readoutVisible = true

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setOffsetNotificationsEnabled(true)
            // Deliberately left off even though the wallpaper now reacts to taps. Raw touch
            // delivery would wake this process for every finger movement anywhere on the
            // home screen; the launcher already sends a single WALLPAPER_TAP command for
            // the gesture we care about, through onCommand, at no cost.
            setTouchEventsEnabled(false)
        }

        /**
         * The launcher's own channel for wallpaper interaction: a tap on empty space, and an
         * icon being dropped. Coordinates arrive in display pixels, and the render scale
         * cancels out when converting to map units, because the world was sized from the
         * reduced surface using the same factor.
         */
        override fun onCommand(
            action: String?,
            x: Int,
            y: Int,
            z: Int,
            extras: android.os.Bundle?,
            resultRequested: Boolean,
        ): android.os.Bundle? {
            val s = scene
            if (s != null) {
                val wx = (x / PX_PER_UNIT * GameData.FRACUNIT).toInt()
                val wy = (y / PX_PER_UNIT * GameData.FRACUNIT).toInt()
                when (action) {
                    WALLPAPER_TAP -> s.tapAt(wx, wy)
                    HOME_DROP -> s.dropAt(wx, wy)
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xStep: Float,
            yStep: Float,
            xPixels: Int,
            yPixels: Int,
        ) {
            offset = xOffset
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                // Cheap wake-up: resume from the frozen state, rebuild nothing. This
                // matters on the lock screen, where it fires on every notification.
                lastNanos = System.nanoTime()
                step()
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunnable)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            // Do not reach for setFixedSize here. Drawing onto a half-resolution surface and
            // letting the compositor scale it up saved two thirds of the graphics memory,
            // and it worked perfectly in the picker preview — but a real wallpaper engine
            // throws UnsupportedOperationException, "Wallpapers currently only support
            // sizing from layout". The preview and the live engine are different surface
            // paths, and only the live one enforces this.
            Log.i(TAG, "drawing surface: ${width}x$height")
            frame.set(0, 0, width, height)
            shaderSkill = -1                       // force the floor shader to be rebuilt
            scene = Scene(
                worldWidth = (width / pxPerUnit).toInt(),
                worldHeight = (height / pxPerUnit).toInt(),
                // The picker preview is the shop window, and it is watched for seconds, not
                // minutes: the opening wave arrives at once rather than after the usual pause.
                instantStart = isPreview,
            )
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            draw()
        }

        /** One turn: advance the elapsed tics, draw once, reschedule. */
        private fun step() {
            if (!visible) return

            val now = System.nanoTime()
            val elapsed = now - lastNanos
            lastNanos = now

            // Fixed-step accumulator: game speed does not depend on the draw rate.
            // Clamped to half a second so a long pause does not replay 1000 tics at once.
            ticAccumulator += elapsed.coerceAtMost(500_000_000L)
            val nanosPerTic = 1_000_000_000L / TICRATE
            while (ticAccumulator >= nanosPerTic) {
                ticAccumulator -= nanosPerTic
                tic++
                scene?.tick(tic)
            }

            draw()

            if (now - powerSaveCheckedAt > 1_000_000_000L) {
                powerSaveCheckedAt = now
                powerSave = powerManager.isPowerSaveMode
            }
            val fps = if (powerSave) DRAW_FPS / 2 else DRAW_FPS
            declareFrameRate(fps.toFloat())
            handler.postDelayed(drawRunnable, 1000L / fps)
        }

        /**
         * Tells the compositor how often this surface actually produces content.
         *
         * Measured A/B on a Pixel 6a: without it the pipeline runs at
         * `mActiveRenderFrameRate = 60` for a wallpaper that changes 20 times a second;
         * with it the figure drops to 20, and this process goes from 13.0% to 11.2% of one
         * core. SurfaceFlinger itself was unchanged at ~12.1%, and the panel never moved:
         * this device has a single fixed 60 Hz mode, so the "supported refresh rates
         * 60/30/20" it advertises are pipeline throttling rather than panel modes. The
         * display power saving one might hope for therefore does not appear here, though it
         * should on hardware with genuine multi-rate panels.
         *
         * FIXED_SOURCE because the rate really is fixed, and the seamless strategy so the
         * system only switches when it can do so without a visible glitch.
         */
        private fun declareFrameRate(fps: Float) {
            if (fps == declaredFps) return
            val surface = surfaceHolder.surface
            if (!surface.isValid) return
            surface.setFrameRate(
                fps,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS,
            )
            declaredFps = fps
        }

        private fun draw() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockHardwareCanvas()
                if (canvas != null) drawScene(canvas)
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun drawScene(canvas: Canvas) {
            drawFloor(canvas)

            val s = scene
            if (s == null || sprites.isEmpty()) {
                drawPlaceholder(canvas)
                return
            }

            // Already depth-sorted by Scene.tick: whoever is in front covers those behind.
            for (i in s.actors.indices) {
                val a = s.actors[i]
                val set = sprites[a.spriteIndex]
                val packed = set.resolve(a.frame(tic), a.spriteRotation())
                if (packed < 0) continue
                val sprite = set.sprite(packed shr 1)
                val flip = packed and 1 == 1

                // Oblique projection: x horizontal, y into the depth. The sprite anchor
                // point (the feet) lands on the actor position.
                val ax = (a.x.toFloat() / GameData.FRACUNIT) * pxPerUnit
                val ay = (a.y.toFloat() / GameData.FRACUNIT) * pxPerUnit

                matrix.setScale(if (flip) -spriteScale else spriteScale, spriteScale)
                matrix.postTranslate(
                    if (flip) ax + sprite.xOffset * spriteScale else ax - sprite.xOffset * spriteScale,
                    ay - sprite.yOffset * spriteScale,
                )
                canvas.drawBitmap(sprite.bitmap, matrix, paint)
            }

            drawReadout(canvas, s)
            drawDebug(canvas, s)

            // Marine death: the screen washes red. The colour is not invented, it is
            // PLAYPAL palette 8, the original game's damage flash — but at full strength it
            // was an unreadable red wall every time he died, so it only ever reaches
            // DEATH_MAX_ALPHA. A wallpaper has to stay usable even at its most dramatic.
            val fade = s.deathFade
            if (fade > 0f) {
                overlay.color = deathTint
                overlay.alpha = (fade * DEATH_MAX_ALPHA).toInt().coerceIn(0, 255)
                canvas.drawRect(0f, 0f, frame.width().toFloat(), frame.height().toFloat(), overlay)
            }
        }

        /**
         * The two readings, split across the bottom corners: armour on the left, health on
         * the right, drawn with the WAD's own status bar numerals.
         *
         * Splitting them beats stacking them. Stacked, the two numbers sat one above the
         * other in a single corner and could be read as one figure; apart, there is nothing
         * to confuse and each has the whole width of its own corner. Both stay along the
         * bottom, clear of the status bar and of the top row of icons.
         *
         * Deliberately not a home screen widget: a widget runs in another process, so it
         * would need a channel out of the wallpaper and a push on every change, and an
         * update arriving while the wallpaper is not even running would undo the whole
         * battery premise. Drawn inside the scene it is a handful of small bitmaps on a
         * frame we are already drawing.
         */
        private fun drawReadout(canvas: Canvas, s: Scene) {
            if (!readoutVisible) return
            val glyphs = digits ?: return

            val scale = spriteScale * READOUT_SCALE
            val gw = glyphs[0].width * scale
            val gh = glyphs[0].height * scale
            val pad = READOUT_PADDING * scale
            val baseline = frame.height() - gh - pad

            val health = s.playerHealth
            val armor = s.playerArmor

            // Colour is what tells the two apart, which is why the percent sign is gone: it
            // occupied a glyph's width to say nothing. Position now says it too.
            hudPaint.colorFilter = armorFilter
            drawNumber(canvas, armor, pad, baseline, scale)

            // Digit counting rather than toString: the draw loop allocates nothing anywhere
            // else, and a throwaway string forty times a second is not the place to start.
            // The right-hand block is measured so it ends at the margin however wide it is.
            hudPaint.colorFilter = healthFilter
            drawNumber(canvas, health, frame.width() - digitCount(health) * gw - pad, baseline, scale)
        }

        /**
         * Debug overlay: the flat in use top left, the skill and wave top right.
         *
         * Present only in debug builds, gated on BuildConfig.DEBUG rather than on a constant
         * someone has to remember to flip, so it cannot reach a release APK. It draws with
         * the platform font instead of the WAD numerals: those cover the ten digits and
         * nothing else, and this needs letters.
         */
        private val debugPaint = Paint().apply {
            color = 0x99FFFFFF.toInt()
            textSize = 13f * resources.displayMetrics.density
            isAntiAlias = true
        }

        /** Rebuilt only when it changes, so the draw loop keeps allocating nothing. */
        private var debugRight = ""
        private var debugSkill = -1
        private var debugWave = -1

        private fun drawDebug(canvas: Canvas, s: Scene) {
            if (!BuildConfig.DEBUG) return

            if (s.skill != debugSkill || s.wave != debugWave) {
                debugSkill = s.skill
                debugWave = s.wave
                debugRight = "skill ${s.skill + 1}/${GameData.skills.size}  wave ${s.wave + 1}/${GameData.waves.size}"
            }

            // Clear of the status bar, which a wallpaper is drawn behind.
            val pad = READOUT_PADDING * spriteScale * READOUT_SCALE
            val y = pad + debugPaint.textSize + STATUS_BAR_CLEARANCE * resources.displayMetrics.density

            canvas.drawText(floorNames.getOrElse(s.skill) { "?" }, pad, y, debugPaint)
            canvas.drawText(debugRight, frame.width() - debugPaint.measureText(debugRight) - pad, y, debugPaint)
        }

        private fun digitCount(value: Int) = when {
            value >= 100 -> 3
            value >= 10 -> 2
            else -> 1
        }

        private fun drawNumber(canvas: Canvas, value: Int, x: Float, y: Float, scale: Float) {
            val glyphs = digits ?: return
            var cursor = x
            var divisor = 1
            repeat(digitCount(value) - 1) { divisor *= 10 }
            while (divisor > 0) {
                val g = glyphs[(value / divisor) % 10]
                matrix.setScale(scale, scale)
                matrix.postTranslate(cursor, y)
                canvas.drawBitmap(g, matrix, hudPaint)
                cursor += g.width * scale
                divisor /= 10
            }
        }

        /**
         * Tiled floor texture, shifted by the home screen paging. The shader repeats the
         * 64x64 flat, so the whole background is one draw call whatever the screen size.
         */
        /** Which skill's tile the shader currently holds, so it is rebuilt only on change. */
        private var shaderSkill = -1

        /**
         * The ground the scene is leaving, kept alive for the length of the crossfade.
         *
         * Swapping the tile outright made the whole screen change texture between one frame
         * and the next, which is the most abrupt thing a wallpaper can do and reads as a
         * glitch rather than as progress. The old ground is held underneath and the new one
         * brought up over it.
         */
        private val fadePaint = Paint().apply {
            isFilterBitmap = false
            colorFilter = floorDim
        }
        private var fadeUntil = 0

        private fun drawFloor(canvas: Canvas) {
            // The ground changes with the difficulty. Rebuilding a BitmapShader is cheap but
            // not free, and the skill changes a handful of times an hour, so it is keyed on
            // the value rather than done per frame.
            val skill = scene?.skill ?: 0
            if (skill != shaderSkill) {
                // Only fade between two real grounds. The first tile of all appears with the
                // scene itself, and fading that one in would just be a slow start.
                fadePaint.shader = if (shaderSkill >= 0) floorPaint.shader else null
                fadeUntil = if (fadePaint.shader != null) tic + FLOOR_FADE_TICS else 0
                shaderSkill = skill
                floorPaint.shader = floorTiles.getOrNull(skill)?.let {
                    BitmapShader(it, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                }
            }

            val shader = floorPaint.shader
            if (shader == null && fadePaint.shader == null) {
                canvas.drawColor(BACKDROP)
                return
            }

            floorMatrix.setScale(FLOOR_SCALE, FLOOR_SCALE)
            floorMatrix.postTranslate((0.5f - offset) * PARALLAX_PX, 0f)
            val w = frame.width().toFloat()
            val h = frame.height().toFloat()

            val left = fadeUntil - tic
            if (left > 0) {
                fadePaint.shader?.let {
                    it.setLocalMatrix(floorMatrix)
                    canvas.drawRect(0f, 0f, w, h, fadePaint)
                }
            } else if (fadePaint.shader != null) {
                fadePaint.shader = null            // released once it has finished showing
            }

            shader ?: return
            shader.setLocalMatrix(floorMatrix)
            // Opaque unless a fade is running, in which case it rises over the old ground.
            floorPaint.alpha =
                if (left > 0) (255 - left * 255 / FLOOR_FADE_TICS).coerceIn(0, 255) else 255
            canvas.drawRect(0f, 0f, w, h, floorPaint)
        }

        /** Solid colour overlays: the death wash and the no-WAD placeholder. */
        private val overlay = Paint()

        /**
         * The readout is drawn without the scene dimming. That filter exists so the
         * wallpaper does not fight the launcher icons, but these digits are information
         * rather than decoration, and Freedoom's status bar numerals are already a dark red:
         * dimming them a further 38% left them barely legible against the floor.
         */
        private val hudPaint = Paint().apply { isFilterBitmap = false }

        /**
         * Recolours the red numerals into a palette colour.
         *
         * The glyphs are essentially a red ramp, so moving the red channel into the target
         * colour's proportions keeps every bit of the shading inside each digit. Flattening
         * with a SRC_IN filter would replace it with a flat silhouette. It is set on the
         * paint once, so recolouring costs nothing per frame.
         */
        // Built once, on first draw, by which time the WAD has been read and the colours
        // are known. Rebuilding them per frame would be two allocations forty times a
        // second for a value that never changes.
        private val healthFilter by lazy { tint(healthColor) }
        private val armorFilter by lazy { tint(armorColor) }

        private fun tint(color: Int) = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    Color.red(color) / 255f, 0f, 0f, 0f, 0f,
                    Color.green(color) / 255f, 0f, 0f, 0f, 0f,
                    Color.blue(color) / 255f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )

        /** Visible placeholder when the WAD is missing: better than a silent black screen. */
        private fun drawPlaceholder(canvas: Canvas) {
            overlay.color = Color.rgb(220, 60, 30)
            overlay.alpha = 255
            val w = frame.width() * 0.2f
            val progress = (tic % (TICRATE * 4)).toFloat() / (TICRATE * 4)
            val x = progress * (frame.width() + w) - w
            canvas.drawRect(x, 0f, x + w, frame.height().toFloat(), overlay)
        }
    }

    private companion object {
        /**
         * Scene zoom: how many pixels one map unit is worth.
         *
         * Speeds stay the original ones *in map units* — this value only decides how fast
         * they appear, i.e. how wide a slice of the world is framed.
         */
        /**
         * Tried in order when a skill's own flat is absent, so a user-supplied WAD that
         * carries only some of them still gets a floor rather than a flat colour.
         */
        val FLOOR_FALLBACKS = listOf("CEIL5_1", "RROCK03", "FLOOR1_6", "FLAT14", "FLOOR0_1")

        const val PX_PER_UNIT = 1.5f

        /**
         * Sprite magnification. Deliberately different from PX_PER_UNIT: the original sprites were
         * drawn for a 320x200 screen and would be unreadable at 1.5x on a modern phone.
         * The proportions between monsters stay correct relative to each other.
         */
        const val SPRITE_SCALE = 3f

        /**
         * Brightness multiplier for the whole scene. The wallpaper has to stay behind the
         * launcher icons, and floor texture plus lit sprites together were bright enough to
         * fight them for attention.
         */
        const val SCENE_DIM = 0.62f

        /**
         * Brightness multiplier for the floor alone, well below SCENE_DIM.
         *
         * FLAT4 measures 37.7 mean luminance; at 0.35 it lands near 13, against the 23 the
         * old textureless CEIL5_1 reached at the shared value. That older backdrop was
         * reported as the better one to sit behind icons, and this is what matches it — with
         * a visible grid rather than featureless noise, which is why it can afford to be
         * darker still and remain a backdrop rather than a black screen.
         */
        const val FLOOR_DIM = 0.35f

        /**
         * How long the ground takes to change, in tics.
         *
         * Quick on purpose: this is scenery, not an event. Long enough that the eye reads a
         * dissolve rather than a cut, short enough that nobody watches it happen.
         */
        const val FLOOR_FADE_TICS = TICRATE / 2

        /** Room left above the debug overlay for the status bar, in dp. */
        const val STATUS_BAR_CLEARANCE = 34f

        /**
         * Peak opacity of the death wash, reached at the end of the fade.
         *
         * The colour it washes with is now a deep 179,0,0 rather than the glaring 255,25,25
         * of the damage ramp, so the same opacity reads far calmer than it used to. This is
         * the one number to change if the wash should be fainter still.
         */
        const val DEATH_MAX_ALPHA = 130f

        /** Magnification of the 64x64 floor tile. */
        const val FLOOR_SCALE = 1.5f

        /** How far the floor slides across the full home screen paging range, in surface pixels. */
        const val PARALLAX_PX = 240f

        /** Display density the pixel scales above were tuned at: a Pixel 6a, 420 dpi. */
        const val REFERENCE_DENSITY = 2.625f

        /** Used when the WAD has no usable flat. */
        const val BACKDROP = 0xFF201814.toInt()

        /** Size of the corner readout, relative to the sprite scale. */
        const val READOUT_SCALE = 0.7f

        /**
         * Padding around the readout, in glyph-scale units. Generous at the bottom, where
         * the navigation bar and the dock both encroach.
         */
        const val READOUT_PADDING = 14f

        /** Commands the launcher sends to the wallpaper. */
        const val WALLPAPER_TAP = "android.wallpaper.tap"
        const val HOME_DROP = "android.home.drop"
    }
}
