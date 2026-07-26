package io.github.massimonastasi.freedoomlw

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.util.Log
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

    override fun onCreateEngine(): Engine = FreedoomEngine()

    private inner class FreedoomEngine : WallpaperService.Engine() {

        // ponytail: a Handler on the wallpaper process main looper, not a dedicated
        // thread. The process is ours alone, so no synchronisation with surfaceDestroyed
        // is needed.
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint().apply { isFilterBitmap = false }
        private val matrix = Matrix()
        private val frame = Rect()

        private var visible = false
        private var tic = 0
        private var lastNanos = 0L
        private var ticAccumulator = 0L
        private var scene: Scene? = null
        private var shrinkPending = true

        // The constants are expressed at full resolution; here they become the values for
        // the reduced surface, so scene and sprites look identical on screen.
        private val pxPerUnit = PX_PER_UNIT * RENDER_SCALE
        private val spriteScale = SPRITE_SCALE * RENDER_SCALE

        private val drawRunnable = Runnable { step() }

        // isPowerSaveMode is an IPC call into PowerManagerService: querying it every frame
        // meant 20 transactions per second for a value that changes maybe once a day.
        // Sampled once per second instead.
        private val powerManager by lazy { getSystemService(POWER_SERVICE) as PowerManager }
        private var powerSave = false
        private var powerSaveCheckedAt = 0L

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            // Enabled only once the features that use them exist (phases 5 and 6).
            setOffsetNotificationsEnabled(false)
            setTouchEventsEnabled(false)
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
            shrinkPending = true
            handler.removeCallbacks(drawRunnable)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            // We draw onto a reduced surface and let the hardware compositor scale it up.
            // This saves no CPU (measured: no difference, the cost is the RenderThread per
            // delivered frame, not the fill) but it **cuts graphics memory by two thirds**:
            // each buffer goes from 10.4 MB to 2.6 MB, and Android keeps three of them.
            // setFixedSize triggers a new onSurfaceChanged with the reduced size.
            if (shrinkPending) {
                shrinkPending = false
                holder.setFixedSize((width * RENDER_SCALE).toInt(), (height * RENDER_SCALE).toInt())
                return
            }

            Log.i(TAG, "drawing surface: ${width}x$height")
            frame.set(0, 0, width, height)
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
            handler.postDelayed(drawRunnable, 1000L / fps)
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
            canvas.drawColor(Color.rgb(48, 34, 30))

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

            // Marine death: the screen sinks into red. The colour is not invented, it is
            // PLAYPAL palette 8, the original game's damage flash.
            val fade = s.deathFade
            if (fade > 0f) {
                paint.color = damageTint
                paint.alpha = (fade * 255f).toInt().coerceIn(0, 255)
                canvas.drawRect(0f, 0f, frame.width().toFloat(), frame.height().toFloat(), paint)
                paint.alpha = 255
            }
        }

        /** Visible placeholder when the WAD is missing: better than a silent black screen. */
        private fun drawPlaceholder(canvas: Canvas) {
            paint.color = Color.rgb(220, 60, 30)
            val w = frame.width() * 0.2f
            val progress = (tic % (TICRATE * 4)).toFloat() / (TICRATE * 4)
            val x = progress * (frame.width() + w) - w
            canvas.drawRect(x, 0f, x + w, frame.height().toFloat(), paint)
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
         * Fraction of the screen resolution we actually draw at.
         * 0.5 = a quarter of the pixels, hence a quarter of the memory per buffer.
         */
        const val RENDER_SCALE = 0.5f
    }
}
