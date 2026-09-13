package com.geekathon.guardpet

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.min
import kotlin.random.Random

/** Fresh Tetris board with glossy blocks, ghost piece, and a colorful night garden. */
class TetrisArena(private val pet: WalkPetSprite) {
    var onScore: ((Int) -> Unit)? = null
    var onGameOver: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val board = Array(H) { IntArray(W) }
    private val bag = ArrayList<Int>()
    private var pieceX = 3
    private var pieceY = 0
    private var pieceType = 0
    private var rotation = 0
    private var nextType = 0
    private var dropAcc = 0f
    private var lines = 0
    private var score = 0
    private var pulse = 0f
    private var ended = false

    fun score(): Int = score

    fun reset() {
        ended = false
        board.forEach { it.fill(0) }
        bag.clear()
        refillBag()
        nextType = takeBag()
        spawn()
        dropAcc = 0f
        lines = 0
        score = 0
        pulse = 0f
        onScore?.invoke(0)
    }

    fun step(dt: Float) {
        if (ended) return
        pulse += dt
        dropAcc += dt
        val interval = (0.78f - lines / 26f).coerceAtLeast(0.13f)
        if (dropAcc >= interval) {
            dropAcc = 0f
            move(0, 1)
        }
    }

    fun move(dx: Int, dy: Int): Boolean {
        if (ended) return false
        if (valid(pieceX + dx, pieceY + dy, rotation)) {
            pieceX += dx
            pieceY += dy
            return true
        }
        if (dy > 0) lock()
        return false
    }

    fun rotate() {
        if (ended) return
        val next = (rotation + 1) % 4
        for (kick in intArrayOf(0, -1, 1, -2, 2)) {
            if (valid(pieceX + kick, pieceY, next)) {
                pieceX += kick
                rotation = next
                return
            }
        }
    }

    fun hardDrop() {
        if (ended) return
        while (move(0, 1)) addScore(1)
    }

    fun draw(canvas: Canvas, width: Float, height: Float) {
        paint.shader = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(Color.rgb(46, 28, 78), Color.rgb(86, 42, 110), Color.rgb(32, 86, 118)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
        paint.color = Color.argb(90, 255, 214, 126)
        for (i in 0 until 12) {
            val x = (i * 47 + pulse * 18f) % (width + 20f)
            val y = 18f + (i * 37) % (height * 0.55f)
            canvas.drawCircle(x, y, 1.8f + (i % 3), paint)
        }

        val cell = min(width / 14.6f, height / 23.4f)
        val boardW = cell * W
        val left = (width - boardW - cell * 3.6f) / 2f
        val top = height * 0.055f
        paint.shader = LinearGradient(
            left, top, left, top + cell * H,
            Color.argb(210, 24, 18, 48), Color.argb(230, 18, 36, 56),
            Shader.TileMode.CLAMP
        )
        oval.set(left - 14f, top - 14f, left + boardW + 14f, top + cell * H + 14f)
        canvas.drawRoundRect(oval, 22f, 22f, paint)
        paint.shader = null
        paint.color = Color.argb(28, 255, 255, 255)
        for (y in 0 until H) for (x in 0 until W) {
            canvas.drawRoundRect(
                left + x * cell + 2f,
                top + y * cell + 2f,
                left + (x + 1) * cell - 2f,
                top + (y + 1) * cell - 2f,
                5f,
                5f,
                paint
            )
        }
        var ghost = pieceY
        while (valid(pieceX, ghost + 1, rotation)) ghost++
        if (ghost != pieceY) {
            cells(pieceType, rotation).forEach { (x, y) ->
                drawCell(canvas, left, top, cell, pieceX + x, ghost + y, pieceType + 1, ghost = true)
            }
        }
        for (y in 0 until H) for (x in 0 until W) {
            if (board[y][x] != 0) drawCell(canvas, left, top, cell, x, y, board[y][x])
        }
        cells(pieceType, rotation).forEach { (x, y) ->
            drawCell(canvas, left, top, cell, pieceX + x, pieceY + y, pieceType + 1)
        }

        val side = left + boardW + 20f
        paint.color = Color.argb(150, 255, 255, 255)
        oval.set(side, top, side + cell * 3.4f, top + cell * 5.4f)
        canvas.drawRoundRect(oval, 16f, 16f, paint)
        paint.color = Color.WHITE
        paint.textSize = 13f
        canvas.drawText("下一个", side + 10f, top + 22f, paint)
        cells(nextType, 0).forEach { (x, y) ->
            drawCell(canvas, side + 14f, top + cell * 1.5f, cell * 0.74f, x, y, nextType + 1, clip = false)
        }
        paint.color = Color.argb(200, 255, 226, 170)
        canvas.drawText("消除 $lines", side + 10f, top + cell * 6.4f, paint)
        pet.draw(
            canvas,
            side + cell * 1.6f,
            top + cell * 8.8f,
            0f,
            WalkPetSprite.TETRIS_HEIGHT,
            faceRight = false
        )
    }

    private fun spawn() {
        pieceType = nextType
        nextType = takeBag()
        pieceX = 3
        pieceY = 0
        rotation = 0
        if (!valid(pieceX, pieceY, rotation)) {
            ended = true
            onGameOver?.invoke(score)
        }
    }

    private fun lock() {
        cells(pieceType, rotation).forEach { (x, y) ->
            val bx = pieceX + x
            val by = pieceY + y
            if (bx in 0 until W && by in 0 until H) board[by][bx] = pieceType + 1
        }
        var cleared = 0
        var y = H - 1
        while (y >= 0) {
            if (board[y].all { it != 0 }) {
                for (row in y downTo 1) board[row] = board[row - 1].clone()
                board[0].fill(0)
                cleared++
            } else {
                y--
            }
        }
        if (cleared > 0) {
            lines += cleared
            addScore(when (cleared) {
                1 -> 12
                2 -> 30
                3 -> 55
                else -> 90
            })
        }
        spawn()
    }

    private fun valid(px: Int, py: Int, rot: Int): Boolean =
        cells(pieceType, rot).all { (x, y) ->
            val bx = px + x
            val by = py + y
            bx in 0 until W && by in 0 until H && board[by][bx] == 0
        }

    private fun cells(type: Int, rot: Int): List<Pair<Int, Int>> {
        var list = SHAPES[type]
        repeat(rot % 4) { list = normalize(list.map { (x, y) -> -y to x }) }
        return list
    }

    private fun normalize(list: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val minX = list.minOf { it.first }
        val minY = list.minOf { it.second }
        return list.map { (x, y) -> (x - minX) to (y - minY) }
    }

    private fun refillBag() {
        bag += (0 until SHAPES.size).shuffled(Random)
    }

    private fun takeBag(): Int {
        if (bag.isEmpty()) refillBag()
        return bag.removeAt(0)
    }

    private fun addScore(delta: Int) {
        score += delta
        onScore?.invoke(score)
    }

    private fun drawCell(
        canvas: Canvas,
        left: Float,
        top: Float,
        cell: Float,
        x: Int,
        y: Int,
        color: Int,
        ghost: Boolean = false,
        clip: Boolean = true
    ) {
        if (clip && (x !in 0 until W || y !in 0 until H)) return
        val base = COLORS[(color - 1).coerceIn(0, COLORS.lastIndex)]
        val l = left + x * cell + 1.6f
        val t = top + y * cell + 1.6f
        val r = left + (x + 1) * cell - 1.6f
        val b = top + (y + 1) * cell - 1.6f
        if (ghost) {
            paint.shader = null
            paint.color = Color.argb(70, Color.red(base), Color.green(base), Color.blue(base))
            canvas.drawRoundRect(l, t, r, b, 6f, 6f, paint)
            return
        }
        paint.shader = LinearGradient(
            l, t, r, b,
            Color.rgb(
                (Color.red(base) + 40).coerceAtMost(255),
                (Color.green(base) + 28).coerceAtMost(255),
                (Color.blue(base) + 20).coerceAtMost(255)
            ),
            base,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(l, t, r, b, 7f, 7f, paint)
        paint.shader = RadialGradient(
            l + 5f, t + 5f, 10f,
            Color.argb(140, 255, 255, 255), Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(l + 2f, t + 2f, r - 6f, b - 8f, 5f, 5f, paint)
        paint.shader = null
    }

    companion object {
        private const val W = 10
        private const val H = 20
        private val COLORS = intArrayOf(
            Color.rgb(255, 118, 168),
            Color.rgb(126, 214, 230),
            Color.rgb(255, 206, 92),
            Color.rgb(150, 132, 255),
            Color.rgb(92, 214, 168),
            Color.rgb(255, 132, 96),
            Color.rgb(118, 176, 255)
        )
        private val SHAPES = listOf(
            listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
            listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0),
            listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1),
            listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1),
            listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1),
            listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1),
            listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1)
        )
    }
}
