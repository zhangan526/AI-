package com.geekathon.guardpet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** Hosts the rewritten pet-home mini-games. */
class GameSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    enum class Mode { RUNNER, TETRIS }

    var mode: Mode = Mode.RUNNER
    var onScore: ((Int) -> Unit)? = null
    var onPetJump: (() -> Unit)? = null
    var onGameOver: ((Int) -> Unit)? = null

    private val pet = WalkPetSprite(context)
    private val runner = RunnerArena(pet)
    private val tetris = TetrisArena(pet)
    private val overlay = Paint(Paint.ANTI_ALIAS_FLAG)
    private var running = false
    private var ended = false
    private var lastFrame = 0L
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    /** Releases a keyboard/accessibility-triggered crouch after its one-shot action. */
    private val releaseRunnerCrouch = Runnable {
        if (mode == Mode.RUNNER) {
            runner.setCrouching(false)
            invalidate()
        }
    }

    private val loop = object : Runnable {
        override fun run() {
            tick()
            if (running) postOnAnimation(this)
        }
    }

    init {
        bindCallbacks()
    }

    fun start(game: Mode) {
        mode = game
        reset()
        running = true
        lastFrame = 0L
        removeCallbacks(loop)
        postOnAnimation(loop)
    }

    fun stopGame() {
        running = false
        removeCallbacks(loop)
        removeCallbacks(releaseRunnerCrouch)
        runner.setCrouching(false)
    }

    fun currentScore(): Int = if (mode == Mode.RUNNER) runner.score() else tetris.score()
    fun isGameOver(): Boolean = ended

    fun moveTetris(dx: Int) {
        if (mode != Mode.TETRIS || ended) return
        tetris.move(dx, 0)
        invalidate()
    }

    fun rotateTetris() {
        if (mode != Mode.TETRIS || ended) return
        tetris.rotate()
        invalidate()
    }

    fun hardDropTetris() {
        if (mode != Mode.TETRIS || ended) return
        tetris.hardDrop()
        invalidate()
    }

    fun setRunnerCrouching(value: Boolean) {
        if (mode != Mode.RUNNER) return
        removeCallbacks(releaseRunnerCrouch)
        if (value && !ended) runner.crouch() else runner.setCrouching(false)
        invalidate()
    }

    /** Handles a momentary click from keyboard/accessibility activation. */
    fun runnerCrouch() {
        if (mode != Mode.RUNNER || ended) return
        setRunnerCrouching(true)
        postDelayed(releaseRunnerCrouch, 180L)
    }

    fun reset() {
        removeCallbacks(releaseRunnerCrouch)
        ended = false
        pet.resetAnim()
        runner.reset()
        tetris.reset()
        onScore?.invoke(0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        when (mode) {
            Mode.RUNNER -> runner.draw(canvas, w, h)
            Mode.TETRIS -> tetris.draw(canvas, w, h)
        }
        if (ended) drawEnded(canvas, w, h)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.TETRIS && !ended && abs(event.x - downX) > 40f) {
                    tetris.move(if (event.x < downX) -1 else 1, 0)
                    downX = event.x
                    moved = true
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (ended) return true
                when (mode) {
                    Mode.RUNNER -> runner.jump()
                    Mode.TETRIS -> {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if (dy > 88f && abs(dy) > abs(dx)) tetris.hardDrop()
                        else if (!moved && abs(dx) <= 40f) tetris.rotate()
                    }
                }
                invalidate()
                return true
            }
        }
        return true
    }

    private fun tick() {
        if (!running || ended) return
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1_000_000_000f).coerceAtMost(0.05f)
        lastFrame = now
        pet.step(dt)
        when (mode) {
            Mode.RUNNER -> runner.step(dt, width.toFloat(), height.toFloat())
            Mode.TETRIS -> tetris.step(dt)
        }
        invalidate()
    }

    private fun bindCallbacks() {
        runner.onScore = { onScore?.invoke(it) }
        tetris.onScore = { onScore?.invoke(it) }
        runner.onJump = { onPetJump?.invoke() }
        runner.onGameOver = { finish(it) }
        tetris.onGameOver = { finish(it) }
    }

    private fun finish(score: Int) {
        if (ended) return
        ended = true
        running = false
        onGameOver?.invoke(score)
        invalidate()
    }

    private fun drawEnded(canvas: Canvas, w: Float, h: Float) {
        overlay.color = Color.argb(188, 24, 16, 46)
        canvas.drawRoundRect(22f, h * 0.36f, w - 22f, h * 0.64f, 26f, 26f, overlay)
        overlay.color = Color.WHITE
        overlay.textAlign = Paint.Align.CENTER
        overlay.textSize = 28f
        overlay.isFakeBoldText = true
        canvas.drawText("本局结束", w / 2f, h * 0.47f, overlay)
        overlay.isFakeBoldText = false
        overlay.textSize = 16f
        canvas.drawText("点击“重新开始”再来一局", w / 2f, h * 0.55f, overlay)
        overlay.textAlign = Paint.Align.LEFT
    }
}
