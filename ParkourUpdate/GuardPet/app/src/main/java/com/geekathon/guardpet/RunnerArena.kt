package com.geekathon.guardpet

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Fresh parkour arena: colorful parallax world, time-based difficulty, mid-air coins. */
class RunnerArena(private val pet: WalkPetSprite) {
    var onScore: ((Int) -> Unit)? = null
    var onJump: (() -> Unit)? = null
    var onGameOver: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()

    private var score = 0
    private var elapsed = 0f
    private var distance = 0f
    private var playerLift = 0f
    private var velocity = 0f
    private var jumpsLeft = 2
    private var crouching = false
    private var runPhase = 0f
    private var spawnTimer = 0.7f
    private var coinTimer = 0.45f
    private var scoreTick = 0f
    private val obstacles = ArrayList<Hazard>()
    private val coins = ArrayList<Loot>()
    private val pops = ArrayList<Pop>()
    private var ended = false

    /* Runner coordinates use a small logical canvas, then scale to the device.
       This keeps the sprite and hazards readable on high-density screens. */
    private val logicalScaleWidth = 420f
    private val logicalScaleHeight = 280f

    fun score(): Int = score
    fun elapsedSeconds(): Float = elapsed
    fun level(): Int = 1 + (elapsed / 12f).toInt().coerceAtMost(7)

    fun reset() {
        score = 0
        elapsed = 0f
        distance = 0f
        playerLift = 0f
        velocity = 0f
        jumpsLeft = 2
        crouching = false
        runPhase = 0f
        spawnTimer = 0.85f
        coinTimer = 0.4f
        scoreTick = 0f
        obstacles.clear()
        coins.clear()
        pops.clear()
        ended = false
        onScore?.invoke(0)
    }

    fun jump() {
        if (ended || jumpsLeft <= 0) return
        velocity = if (playerLift >= -2f) -760f else -640f
        jumpsLeft -= 1
        onJump?.invoke()
    }

    fun setCrouching(value: Boolean) { crouching = value && !ended }
    fun crouch() { if (!ended) { crouching = true; if (playerLift < -2f) velocity = 980f } }

    fun step(dt: Float, width: Float, height: Float) {
        if (ended) return
        val scale = scaleFor(width, height)
        val worldWidth = width / scale
        val worldHeight = height / scale
        elapsed += dt
        val speed = currentSpeed()
        distance += speed * dt
        runPhase += dt * (8.5f + level())
        velocity += 1760f * dt
        playerLift += velocity * dt
        if (playerLift > 0f) {
            playerLift = 0f
            velocity = 0f
            jumpsLeft = 2
        }
        scoreTick += dt
        if (scoreTick >= 1f) {
            scoreTick -= 1f
            addScore(1)
        }
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnHazard(worldWidth, worldHeight)
            spawnTimer = currentGap()
        }
        coinTimer -= dt
        if (coinTimer <= 0f) {
            spawnCoinTrail(worldWidth, worldHeight)
            coinTimer = (1.05f - elapsed * 0.012f).coerceIn(0.55f, 1.05f)
        }
        val ground = groundY(worldHeight)
        val petX = worldWidth * 0.22f
        val petY = ground + playerLift - WalkPetSprite.RUNNER_HEIGHT * 0.38f
        val it = obstacles.iterator()
        while (it.hasNext()) {
            val hazard = it.next()
            hazard.x -= speed * dt
            if (hazard.x + hazard.w < -20f) {
                it.remove()
                addScore(2)
            }
        }
        val coinIt = coins.iterator()
        while (coinIt.hasNext()) {
            val coin = coinIt.next()
            coin.x -= speed * dt
            coin.spin += dt * 7f
            if (abs(coin.x - petX) < 22f && abs(coin.y - petY) < 28f) {
                coinIt.remove()
                addScore(10)
                pops += Pop(coin.x, coin.y, "+10")
            } else if (coin.x < -24f) {
                coinIt.remove()
            }
        }
        val popIt = pops.iterator()
        while (popIt.hasNext()) {
            val pop = popIt.next()
            pop.life -= dt
            pop.y -= 36f * dt
            if (pop.life <= 0f) popIt.remove()
        }
        val hitLeft = petX - 16f
        val hitRight = petX + 16f
        val hitBottom = ground + playerLift
        val hitTop = hitBottom - if (crouching) 20f else 34f
        if (obstacles.any { it.hits(hitLeft, hitRight, hitTop, hitBottom, ground) }) {
            ended = true
            onGameOver?.invoke(score)
        }
    }

    fun draw(canvas: Canvas, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        val scale = scaleFor(width, height)
        val worldWidth = width / scale
        val worldHeight = height / scale
        val ground = groundY(worldHeight)
        canvas.save()
        canvas.scale(scale, scale)
        drawWorld(canvas, worldWidth, worldHeight, ground)
        coins.forEach { drawCoin(canvas, it) }
        obstacles.forEach { drawHazard(canvas, it, ground) }
        pet.draw(canvas, worldWidth * 0.22f, ground, playerLift, if (crouching) WalkPetSprite.RUNNER_HEIGHT * 0.58f else WalkPetSprite.RUNNER_HEIGHT, faceRight = true)
        pops.forEach { pop ->
            paint.shader = null
            paint.color = Color.argb((pop.life * 255).toInt().coerceIn(0, 255), 255, 220, 90)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 22f
            paint.isFakeBoldText = true
            canvas.drawText(pop.text, pop.x, pop.y, paint)
            paint.isFakeBoldText = false
            paint.textAlign = Paint.Align.LEFT
        }
        drawHud(canvas, worldWidth)
        canvas.restore()
    }

    private fun currentSpeed(): Float = 260f + min(420f, elapsed * 16f) + level() * 12f

    private fun currentGap(): Float {
        val base = (1.28f - elapsed * 0.028f).coerceAtLeast(0.52f)
        return if (level() >= 5) base * 0.86f else base
    }

    private fun spawnHazard(width: Float, height: Float) {
        val start = width + 36f
        val roll = Random.nextFloat()
        val kind = when {
            elapsed > 28f && roll < 0.22f -> Kind.ARCH
            elapsed > 16f && roll < 0.38f -> Kind.CRYSTAL
            roll < 0.55f -> Kind.CRATE
            else -> Kind.BUSH
        }
        obstacles += when (kind) {
            Kind.BUSH -> Hazard(start, 46f + Random.nextInt(16), 34f + Random.nextInt(14), kind)
            Kind.CRATE -> Hazard(start, 38f + Random.nextInt(10), 50f + Random.nextInt(16), kind)
            Kind.CRYSTAL -> Hazard(start, 30f + Random.nextInt(8), 78f + Random.nextInt(22), kind)
            // The bar hangs just above the crouch hitbox: standing collides,
            // while crouching leaves a clear path underneath it.
            Kind.ARCH -> Hazard(start, 92f, 40f, kind, floatLift = 27f + Random.nextInt(6))
        }
        // A ground crate immediately after an overhead bar would require a
        // crouch and a jump at the same time. Keep that pair solvable.
        if (kind != Kind.ARCH && elapsed > 20f && Random.nextFloat() < 0.34f) {
            obstacles += Hazard(start + 56f + Random.nextInt(24), 36f, 42f + Random.nextInt(18), Kind.CRATE)
        }
    }

    private fun spawnCoinTrail(width: Float, height: Float) {
        val ground = groundY(height)
        val start = width + 70f
        val mid = ground - 92f - Random.nextInt(36)
        val count = 3 + if (level() >= 3) Random.nextInt(3) else 0
        for (i in 0 until count) {
            val arc = sin(i / (count - 1f).coerceAtLeast(1f) * Math.PI.toFloat()) * 28f
            coins += Loot(start + i * 28f, mid - arc, Random.nextFloat() * 3f)
        }
    }

    private fun drawWorld(canvas: Canvas, width: Float, height: Float, ground: Float) {
        paint.shader = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(
                Color.rgb(255, 132, 176),
                Color.rgb(255, 186, 132),
                Color.rgb(255, 226, 150),
                Color.rgb(126, 214, 214)
            ),
            floatArrayOf(0f, 0.32f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = RadialGradient(
            width * 0.78f, height * 0.16f, 70f,
            Color.rgb(255, 244, 170), Color.argb(0, 255, 180, 80),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(width * 0.78f, height * 0.16f, 70f, paint)
        paint.shader = null
        paint.color = Color.rgb(255, 214, 96)
        canvas.drawCircle(width * 0.78f, height * 0.16f, 28f, paint)

        drawHills(canvas, width, ground, Color.rgb(196, 118, 214), 0.16f, 54f, 0.18f)
        drawHills(canvas, width, ground, Color.rgb(255, 122, 150), 0.34f, 42f, 0.28f)
        drawHills(canvas, width, ground, Color.rgb(80, 196, 176), 0.62f, 28f, 0.42f)
        drawClouds(canvas, width)
        drawBalloons(canvas, width, height)

        paint.shader = LinearGradient(
            0f, ground, 0f, height,
            Color.rgb(86, 196, 132), Color.rgb(46, 140, 110),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, ground, width, height, paint)
        paint.shader = null
        paint.color = Color.rgb(255, 236, 150)
        canvas.drawRect(0f, ground, width, ground + 8f, paint)
        val tile = 36f
        val shift = distance % tile
        for (i in -1..((width / tile).toInt() + 2)) {
            val x = i * tile - shift
            paint.color = if (i % 2 == 0) Color.rgb(255, 168, 196) else Color.rgb(126, 214, 230)
            canvas.drawRoundRect(x + 4f, ground + 14f, x + 28f, ground + 22f, 4f, 4f, paint)
        }
        drawFlowers(canvas, width, ground)
    }

    private fun drawHills(
        canvas: Canvas,
        width: Float,
        ground: Float,
        color: Int,
        parallax: Float,
        amplitude: Float,
        baseline: Float
    ) {
        val offset = (distance * parallax) % width
        path.reset()
        path.moveTo(-40f, ground)
        var x = -40f
        while (x <= width + 40f) {
            val world = x + offset
            val y = ground * baseline - sin(world / 70f) * amplitude - 20f
            path.lineTo(x, y)
            x += 18f
        }
        path.lineTo(width + 40f, ground)
        path.close()
        paint.shader = null
        paint.color = color
        canvas.drawPath(path, paint)
    }

    private fun drawClouds(canvas: Canvas, width: Float) {
        paint.color = Color.argb(150, 255, 255, 255)
        for (i in 0 until 5) {
            val x = ((i * width / 3.2f) - distance * 0.22f).mod(width + 90f) - 40f
            val y = 28f + i * 16f
            canvas.drawCircle(x, y, 16f + i, paint)
            canvas.drawCircle(x + 18f, y + 4f, 13f, paint)
            canvas.drawCircle(x + 32f, y, 12f, paint)
        }
    }

    private fun drawBalloons(canvas: Canvas, width: Float, height: Float) {
        val colors = intArrayOf(
            Color.rgb(255, 102, 148),
            Color.rgb(92, 186, 255),
            Color.rgb(255, 196, 64),
            Color.rgb(156, 122, 255)
        )
        for (i in colors.indices) {
            val x = ((i * width / 3.5f) - distance * 0.12f).mod(width + 70f) - 20f
            val y = 70f + sin(elapsed * 1.3f + i) * 10f + i * 18f
            paint.color = colors[i]
            oval.set(x - 10f, y - 16f, x + 10f, y + 10f)
            canvas.drawOval(oval, paint)
            paint.color = Color.argb(120, 40, 30, 60)
            paint.strokeWidth = 1.6f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(x, y + 10f, x, y + 28f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawFlowers(canvas: Canvas, width: Float, ground: Float) {
        val palette = intArrayOf(
            Color.rgb(255, 110, 160),
            Color.rgb(255, 196, 64),
            Color.rgb(126, 168, 255),
            Color.rgb(255, 132, 96)
        )
        for (i in 0 until 9) {
            val x = ((i * 52f) - distance * 0.9f).mod(width + 40f) - 12f
            paint.color = Color.rgb(62, 150, 92)
            canvas.drawRect(x - 1.2f, ground - 14f, x + 1.2f, ground, paint)
            paint.color = palette[i % palette.size]
            canvas.drawCircle(x, ground - 16f, 5f, paint)
            paint.color = Color.rgb(255, 236, 140)
            canvas.drawCircle(x, ground - 16f, 2f, paint)
        }
    }

    private fun drawHazard(canvas: Canvas, hazard: Hazard, ground: Float) {
        val top = when (hazard.kind) {
            Kind.ARCH -> ground - hazard.floatLift - hazard.h
            else -> ground - hazard.h
        }
        val bottom = when (hazard.kind) {
            Kind.ARCH -> ground - hazard.floatLift
            else -> ground
        }
        when (hazard.kind) {
            Kind.BUSH -> {
                paint.color = Color.rgb(56, 176, 118)
                canvas.drawCircle(hazard.x + hazard.w * 0.3f, bottom - 10f, 16f, paint)
                canvas.drawCircle(hazard.x + hazard.w * 0.7f, bottom - 14f, 18f, paint)
                paint.color = Color.rgb(255, 110, 150)
                canvas.drawCircle(hazard.x + 12f, bottom - 22f, 4f, paint)
            }
            Kind.CRATE -> {
                paint.shader = LinearGradient(
                    hazard.x, top, hazard.x, bottom,
                    Color.rgb(255, 176, 92), Color.rgb(214, 96, 86),
                    Shader.TileMode.CLAMP
                )
                oval.set(hazard.x, top, hazard.x + hazard.w, bottom)
                canvas.drawRoundRect(oval, 8f, 8f, paint)
                paint.shader = null
                paint.color = Color.argb(80, 255, 255, 255)
                canvas.drawRoundRect(hazard.x + 6f, top + 6f, hazard.x + hazard.w - 6f, top + 16f, 4f, 4f, paint)
            }
            Kind.CRYSTAL -> {
                path.reset()
                path.moveTo(hazard.x + hazard.w / 2f, top)
                path.lineTo(hazard.x + hazard.w, bottom)
                path.lineTo(hazard.x, bottom)
                path.close()
                paint.shader = LinearGradient(
                    hazard.x, top, hazard.x + hazard.w, bottom,
                    Color.rgb(186, 132, 255), Color.rgb(92, 118, 230),
                    Shader.TileMode.CLAMP
                )
                canvas.drawPath(path, paint)
                paint.shader = null
            }
            Kind.ARCH -> {
                paint.color = Color.rgb(255, 96, 140)
                oval.set(hazard.x, top, hazard.x + hazard.w, bottom)
                canvas.drawRoundRect(oval, 14f, 14f, paint)
                paint.color = Color.argb(90, 255, 255, 255)
                canvas.drawCircle(hazard.x + hazard.w / 2f, (top + bottom) / 2f, 8f, paint)
            }
        }
    }

    private fun drawCoin(canvas: Canvas, coin: Loot) {
        val squash = 0.35f + abs(cos(coin.spin)) * 0.65f
        canvas.save()
        canvas.translate(coin.x, coin.y + sin(coin.spin * 2f) * 3f)
        canvas.scale(squash, 1f)
        paint.shader = RadialGradient(
            -3f, -4f, 14f,
            Color.rgb(255, 244, 150), Color.rgb(255, 168, 40),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(0f, 0f, 12f, paint)
        paint.shader = null
        paint.color = Color.rgb(255, 214, 80)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        canvas.drawCircle(0f, 0f, 7.5f, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    private fun drawHud(canvas: Canvas, width: Float) {
        paint.shader = null
        paint.color = Color.argb(120, 40, 24, 56)
        canvas.drawRoundRect(16f, 14f, 168f, 46f, 16f, 16f, paint)
        paint.color = Color.WHITE
        paint.textSize = 15f
        paint.isFakeBoldText = true
        canvas.drawText("难度 ${level()}", 28f, 35f, paint)
        paint.isFakeBoldText = false
        paint.textSize = 12f
        paint.color = Color.argb(210, 255, 236, 180)
        canvas.drawText("坚持 ${elapsed.toInt()}s", width - 108f, 34f, paint)
    }

    private fun addScore(delta: Int) {
        score += delta
        onScore?.invoke(score)
    }

    private fun groundY(height: Float) = height * 0.78f

    private fun scaleFor(width: Float, height: Float): Float {
        if (width <= 0f || height <= 0f) return 1f
        return min(width / logicalScaleWidth, height / logicalScaleHeight)
            .coerceAtLeast(0.75f)
    }

    private fun Float.mod(m: Float): Float = ((this % m) + m) % m

    private enum class Kind { BUSH, CRATE, CRYSTAL, ARCH }

    private data class Hazard(
        var x: Float,
        val w: Float,
        val h: Float,
        val kind: Kind,
        val floatLift: Float = 0f
    ) {
        fun hits(left: Float, right: Float, top: Float, bottom: Float, ground: Float): Boolean {
            val boxTop = if (kind == Kind.ARCH) ground - floatLift - h else ground - h
            val boxBottom = if (kind == Kind.ARCH) ground - floatLift else ground
            return x < right && x + w > left && bottom > boxTop && top < boxBottom
        }
    }

    private data class Loot(var x: Float, var y: Float, var spin: Float)
    private data class Pop(var x: Float, var y: Float, val text: String, var life: Float = 0.7f)
}
