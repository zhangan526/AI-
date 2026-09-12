package com.geekathon.guardpet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Draws the app's walk GIF after the character was cut out of the canvas
 * and scaled down so it does not cover the playfield.
 */
class WalkPetSprite(context: Context) {
    private val frames: List<Bitmap> = loadFrames(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dest = RectF()
    private val src = Rect()
    private var time = 0f

    fun step(dt: Float) {
        if (frames.isEmpty()) return
        time += dt
    }

    fun resetAnim() {
        time = 0f
    }

    fun draw(
        canvas: Canvas,
        cx: Float,
        groundY: Float,
        lift: Float,
        heightPx: Float,
        faceRight: Boolean = true
    ) {
        val frame = frames.getOrNull(frameIndex()) ?: return
        val widthPx = heightPx * frame.width / frame.height
        dest.set(cx - widthPx / 2f, groundY + lift - heightPx, cx + widthPx / 2f, groundY + lift)
        paint.color = Color.argb(50, 40, 28, 60)
        canvas.drawOval(cx - widthPx * 0.28f, groundY - 5f, cx + widthPx * 0.28f, groundY + 6f, paint)
        canvas.save()
        if (faceRight) canvas.scale(-1f, 1f, cx, groundY + lift - heightPx / 2f)
        src.set(0, 0, frame.width, frame.height)
        paint.color = Color.WHITE
        canvas.drawBitmap(frame, src, dest, paint)
        canvas.restore()
    }

    private fun frameIndex(): Int {
        if (frames.isEmpty()) return 0
        val idx = (time / FRAME_SEC).toInt()
        return idx.mod(frames.size)
    }

    companion object {
        const val RUNNER_HEIGHT = 62f
        const val TETRIS_HEIGHT = 40f
        private const val FRAME_SEC = 0.1f
        private const val DECODE_HEIGHT = 148
        private const val ASSET_DIR = "game/walk_cutout"

        @Volatile
        private var cached: List<Bitmap>? = null

        private fun loadFrames(context: Context): List<Bitmap> {
            cached?.let { return it }
            synchronized(this) {
                cached?.let { return it }
                val names = context.assets.list(ASSET_DIR)?.filter { it.endsWith(".png") }?.sorted().orEmpty()
                val loaded = names.mapNotNull { name ->
                    context.assets.open("$ASSET_DIR/$name").use { stream ->
                        val raw = BitmapFactory.decodeStream(stream) ?: return@use null
                        val h = DECODE_HEIGHT
                        val w = (h.toFloat() * raw.width / raw.height).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
                        if (scaled != raw) raw.recycle()
                        scaled
                    }
                }
                cached = loaded
                return loaded
            }
        }
    }
}
