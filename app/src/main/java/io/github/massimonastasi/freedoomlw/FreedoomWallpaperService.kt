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

/** the engine runs at 35 tics per second. All game logic advances at that rate, always. */
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

    /** One SpriteSet per prefix, indexed like the engineData.spritePrefixes. */
    private var sprites: List<SpriteSet> = emptyList()

    /** Red damage flash colour, read from PLAYPAL. */
    private var damageTint = 0xFFAA1400.toInt()

    /** Floor texture from the WAD, tiled behind the scene. Null when unavailable. */
    private var floorTile: Bitmap? = null

    /** Status bar numerals from the WAD. */
    private var digits: Array<Bitmap>? = null

    /** Readout colours, taken from the active WAD's palette. */
    private var healthColor = 0xFF77FF6F.toInt()
    private var armorColor = 0xFF7373FF.toInt()

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
            floorTile = loadFloor(w)
            loadDigits(w)
            sprites = the engineData.spritePrefixes.map { SpriteSet(w, it) }
            val missing = the engineData.spritePrefixes.filterIndexed { i, _ -> sprites[i].frameCount == 0 }
            Log.i(TAG, "WAD loaded: ${w.lumpCount} lumps, ${sprites.size - missing.size}/${sprites.size} sprites" +
                if (missing.isEmpty()) "" else " (missing: $missing)")
        } catch (e: Exception) {
            // Without a WAD the wallpaper stays alive and shows the placeholder rather
            // than disappearing.
            Log.e(TAG, "WAD not loaded", e)
        }
    }

    /**
     * Picks a floor flat that works as a backdrop.
     *
     * A wallpaper sits *behind* the launcher icons, so the texture has to stay quiet. The
     * candidates were chosen by measuring every flat in the WAD on three axes: mean
     * luminance, standard deviation, and chroma.
     *
     * Chroma is the one that is easy to forget. Ranking on luminance alone picked FLOOR6_1,
     * dark at 26/255 and very uniform — and visibly a saturated red, which shouts on screen
     * and collides with the red damage flash. CEIL5_1 is dark, uniform *and* has a chroma of
     * exactly zero: pure greyscale, which is what a backdrop wants to be.
     *
     * The list is a fallback chain because a user-supplied WAD may not have all of them.
     */
    private fun loadFloor(w: WadFile): Bitmap? {
        for (name in listOf("CEIL5_1", "RROCK03", "FLOOR1_6", "FLAT14", "FLOOR0_1")) {
            val i = w.flatIndex(name)
            if (i < 0) continue
            val f = w.decodeFlat(i)
            Log.i(TAG, "floor texture: $name")
            return Bitmap.createBitmap(f.pixels, f.width, f.height, Bitmap.Config.ARGB_8888)
        }
        Log.i(TAG, "no usable floor flat, falling back to a flat colour")
        return null
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
        healthColor = w.paletteColor(the engineData.PALETTE_HEALTH)
        armorColor = w.paletteColor(the engineData.PALETTE_ARMOR)
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
        private val dim = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    SCENE_DIM, 0f, 0f, 0f, 0f,
                    0f, SCENE_DIM, 0f, 0f, 0f,
                    0f, 0f, SCENE_DIM, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )

        private val paint = Paint().apply {
            isFilterBitmap = false
            colorFilter = dim
        }

        /** Tiled floor. Its shader matrix carries the home screen parallax. */
        private val floorPaint = Paint().apply {
            isFilterBitmap = false
            colorFilter = dim
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

        // Both become settings in phase 9. Corner is a bit field: 1 = right, 2 = bottom.
        private var readoutVisible = true
        private var readoutCorner = 2                       // bottom left

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
                val wx = (x / PX_PER_UNIT * the engineData.FRACUNIT).toInt()
                val wy = (y / PX_PER_UNIT * the engineData.FRACUNIT).toInt()
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
            floorPaint.shader = floorTile?.let {
                BitmapShader(it, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            scene = Scene(
                worldWidth = (width / pxPerUnit).toInt(),
                worldHeight = (height / pxPerUnit).toInt(),
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
                val ax = (a.x.toFloat() / the engineData.FRACUNIT) * pxPerUnit
                val ay = (a.y.toFloat() / the engineData.FRACUNIT) * pxPerUnit

                matrix.setScale(if (flip) -spriteScale else spriteScale, spriteScale)
                matrix.postTranslate(
                    if (flip) ax + sprite.xOffset * spriteScale else ax - sprite.xOffset * spriteScale,
                    ay - sprite.yOffset * spriteScale,
                )
                canvas.drawBitmap(sprite.bitmap, matrix, paint)
            }

            drawReadout(canvas, s)

            // Marine death: the screen washes red. The colour is not invented, it is
            // PLAYPAL palette 8, the original game's damage flash — but at full strength it
            // was an unreadable red wall every time he died, so it only ever reaches
            // DEATH_MAX_ALPHA. A wallpaper has to stay usable even at its most dramatic.
            val fade = s.deathFade
            if (fade > 0f) {
                overlay.color = damageTint
                overlay.alpha = (fade * DEATH_MAX_ALPHA).toInt().coerceIn(0, 255)
                canvas.drawRect(0f, 0f, frame.width().toFloat(), frame.height().toFloat(), overlay)
            }
        }

        /**
         * Health above armour, in a corner, using the WAD's own status bar numerals.
         *
         * Deliberately not a home screen widget: a widget runs in another process, so it
         * would need a channel out of the wallpaper and a push on every change, and an
         * update arriving while the wallpaper is not even running would undo the whole
         * battery premise. Drawn inside the scene it is a handful of small bitmaps on a
         * frame we are already drawing.
         *
         * The corner is a field rather than a constant because settings will expose it
         * (phase 9); the default keeps clear of the status bar and the dock.
         */
        private fun drawReadout(canvas: Canvas, s: Scene) {
            if (!readoutVisible) return
            val glyphs = digits ?: return

            val scale = spriteScale * READOUT_SCALE
            val gw = glyphs[0].width * scale
            val gh = glyphs[0].height * scale
            val pad = READOUT_PADDING * scale

            val health = s.playerHealth
            val armor = s.playerArmor
            // Digit counting rather than toString: the draw loop allocates nothing anywhere
            // else, and two throwaway strings forty times a second is not the place to start.
            val widest = maxOf(digitCount(health), digitCount(armor))
            val blockW = widest * gw
            val blockH = gh * 2 + pad

            val left = if (readoutCorner and 1 == 0) pad else frame.width() - blockW - pad
            val top = if (readoutCorner and 2 == 0) pad else frame.height() - blockH - pad

            // Colour is what tells the two apart, which is why the percent sign is gone: it
            // occupied a glyph's width to say nothing.
            hudPaint.colorFilter = healthFilter
            drawNumber(canvas, health, left, top, scale)
            hudPaint.colorFilter = armorFilter
            drawNumber(canvas, armor, left, top + gh + pad, scale)
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
        private fun drawFloor(canvas: Canvas) {
            val shader = floorPaint.shader
            if (shader == null) {
                canvas.drawColor(BACKDROP)
                return
            }
            floorMatrix.setScale(FLOOR_SCALE, FLOOR_SCALE)
            floorMatrix.postTranslate((0.5f - offset) * PARALLAX_PX, 0f)
            shader.setLocalMatrix(floorMatrix)
            canvas.drawRect(0f, 0f, frame.width().toFloat(), frame.height().toFloat(), floorPaint)
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
         * Scene zoom: how many pixels one the engine map unit is worth.
         *
         * Speeds stay the original ones *in map units* — this value only decides how fast
         * they appear, i.e. how wide a slice of the world is framed.
         */
        const val PX_PER_UNIT = 1.5f

        /**
         * Sprite magnification. Deliberately different from PX_PER_UNIT: the engine sprites were
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

        /** Peak opacity of the death wash. Full red was unusable as a wallpaper. */
        const val DEATH_MAX_ALPHA = 110f

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
