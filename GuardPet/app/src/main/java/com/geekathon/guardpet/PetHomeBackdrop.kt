package com.geekathon.guardpet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/** Soft animated backdrop used by 宠物之家 and the mini-game menu. */
class PetHomeBackdrop @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbles = List(14) {
        Bubble(Random.nextFloat(), Random.nextFloat(), 8f + Random.nextFloat() * 22f, Random.nextFloat() * 6.28f)
    }
    private var phase = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        paint.shader = RadialGradient(
            w * .2f, h * .05f, h * .95f,
            Color.rgb(255, 231, 240), Color.rgb(244, 235, 255),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        phase += .018f
        bubbles.forEachIndexed { i, b ->
            val x = (b.x * w + sin(phase + b.seed) * 18f).coerceIn(0f, w)
            val y = ((b.y * h - phase * (12f + i) * 5f) % (h + 80f) + h + 80f) % (h + 80f) - 40f
            paint.color = Color.argb(38 + (i % 3) * 10, 255, 255, 255)
            canvas.drawCircle(x, y, b.radius, paint)
        }
        postInvalidateOnAnimation()
    }

    private data class Bubble(val x: Float, val y: Float, val radius: Float, val seed: Float)
}
