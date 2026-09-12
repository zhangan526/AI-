package com.example.desktoppet

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import java.time.LocalTime
import kotlin.random.Random

class PetService : Service() {
    private lateinit var windowManager: WindowManager
    private var petView: PetCanvas? = null
    private var idleAnimator: ObjectAnimator? = null
    private lateinit var assets: PetAssetRepository
    private lateinit var settings: PetSettings
    private var currentState = PetState.IDLE
    private var walkAnimator: ValueAnimator? = null
    private var panelOverlay: PetPanelOverlay? = null
    private val timeHandler = Handler(Looper.getMainLooper())
    private var lastTimeActionKey: String? = null
    private val timeCheck = object : Runnable {
        override fun run() {
            evaluateLocalTime()
            timeHandler.postDelayed(this, TimeBehaviorConfig.CHECK_INTERVAL_MS)
        }
    }
    private val interactionHandler = Handler(Looper.getMainLooper())
    private val behaviorHandler = Handler(Looper.getMainLooper())
    private val behaviorTick = object : Runnable {
        override fun run() {
            playRandomBehavior()
            behaviorHandler.postDelayed(this, 180_000L)
        }
    }
    override fun onCreate() {
        super.onCreate()
        assets = PetAssetRepository(this)
        settings = PetSettings(this)
        isRunning = false
        runCatching {
            createNotificationChannel()
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            check(Settings.canDrawOverlays(this)) { getString(R.string.overlay_permission_required) }
            showPet()
            isRunning = true
            clearLastStartError()
        }.onFailure {
            isRunning = false
            saveLastStartError(it)
            stopSelf()
        }
        evaluateLocalTime()
        timeHandler.postDelayed(timeCheck, TimeBehaviorConfig.CHECK_INTERVAL_MS)
        behaviorHandler.postDelayed(behaviorTick, 180_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REFRESH, ACTION_REFRESH_SETTINGS -> {
                currentState = PetState.IDLE
                petView?.show(assets.randomFileFor(currentState))
                applyVisualState()
                evaluateLocalTime()
            }
            ACTION_SET_STATE -> {
                currentState = PetState.fromKey(intent.getStringExtra(EXTRA_STATE))
                if (isSleepingByTime()) return START_STICKY
                walkAnimator?.cancel()
                petView?.show(assets.randomFileFor(currentState))
                applyVisualState()
                if (currentState == PetState.SLEEP) {
                    idleAnimator?.cancel()
                } else {
                    startIdleAnimation()
                    resumeFreeWalkAfter(INTERACTION_DISPLAY_MS)
                }
            }
        }
        if (petView == null && Settings.canDrawOverlays(this)) showPet()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        resizeCanvasForCurrentScreen()
        updateEdgeWalk()
    }

    override fun onDestroy() {
        idleAnimator?.cancel()
        walkAnimator?.cancel()
        timeHandler.removeCallbacks(timeCheck)
        interactionHandler.removeCallbacksAndMessages(null)
        behaviorHandler.removeCallbacks(behaviorTick)
        panelOverlay?.close()
        panelOverlay = null
        petView?.let { runCatching { windowManager.removeView(it) } }
        petView = null
        isRunning = false
        super.onDestroy()
    }

    private fun showPet() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val canvasSize = PetCanvasConfig.resolve(this)
        val preferences = getSharedPreferences("pet_position", MODE_PRIVATE)
        val params = WindowManager.LayoutParams(
            canvasSize.widthPx,
            canvasSize.heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.getInt("x", 40)
            y = preferences.getInt("y", 250)
        }

        petView = PetCanvas(this).apply {
            show(assets.randomFileFor(currentState))
            contentDescription = getString(R.string.pet_description)
            setOnTouchListener(PetTouchListener(params))
        }
        windowManager.addView(petView, params)
        applyVisualState()
        startIdleAnimation()
        updateEdgeWalk()
    }

    private fun applyVisualState() {
        val hidden = settings.semiHidden || currentState == PetState.HIDDEN
        val scale = settings.petScale * if (hidden) 0.72f else 1f
        petView?.setVisualScale(scale, if (hidden) 0.45f else 1f)
    }

    private fun updateEdgeWalk() {
        updateFreeWalkRandom()
    }

    fun refreshSettings() {
        applyVisualState()
        updateEdgeWalk()
    }

    private fun showControlPanel() {
        runCatching {
            check(Settings.canDrawOverlays(this)) { getString(R.string.overlay_permission_required) }
            val view = petView ?: return@runCatching
            val petParams = view.layoutParams as? WindowManager.LayoutParams
                ?: return@runCatching
            panelOverlay?.close()
            panelOverlay = PetPanelOverlay(this, windowManager, petParams.x, petParams.y, petParams.height)
            panelOverlay?.show()
        }.onFailure {
            panelOverlay?.close()
            panelOverlay = null
            Toast.makeText(
                this,
                getString(R.string.panel_open_failed, it.localizedMessage ?: it.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveLastStartError(error: Throwable) {
        getSharedPreferences(RUNTIME_PREFERENCES, MODE_PRIVATE).edit()
            .putString(KEY_LAST_ERROR, error.localizedMessage ?: error.javaClass.simpleName)
            .apply()
    }

    private fun clearLastStartError() {
        getSharedPreferences(RUNTIME_PREFERENCES, MODE_PRIVATE).edit()
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    private fun updateFreeWalkRandom() {
        interactionHandler.removeCallbacksAndMessages(null)
        walkAnimator?.cancel()
        if (!settings.edgeWalkEnabled || petView == null || isSleepingByTime() || currentState == PetState.SLEEP) return
        val view = petView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - params.width).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - params.height).coerceAtLeast(0)
        val startX = params.x.coerceIn(0, maxX)
        val startY = params.y.coerceIn(0, maxY)
        val targetX = Random.nextInt(0, maxX + 1)
        val targetY = Random.nextInt(0, maxY + 1)
        val distance = kotlin.math.hypot((targetX - startX).toDouble(), (targetY - startY).toDouble())
        val duration = (distance * 18 - settings.walkSpeed * 28).toLong().coerceIn(450L, 6500L)

        currentState = PetState.WALK
        view.show(assets.randomFileFor(PetState.WALK))
        applyVisualState()
        // The directional walk GIF faces left in its source file. Mirror once per leg only.
        val movingRight = targetX >= startX
        view.setFacingRight(!movingRight)
        var wasCancelled = false
        walkAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener { animation ->
                val p = animation.animatedFraction
                params.x = (startX + (targetX - startX) * p).toInt().coerceIn(0, maxX)
                params.y = (startY + (targetY - startY) * p).toInt().coerceIn(0, maxY)
                windowManager.updateViewLayout(view, params)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    wasCancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!wasCancelled && settings.edgeWalkEnabled) {
                        interactionHandler.postDelayed(
                            { updateFreeWalkRandom() },
                            Random.nextLong(700L, 2600L)
                        )
                    }
                }
            })
            start()
        }
    }

    private fun playRandomBehavior(allowWhileWalking: Boolean = false) {
        if (isSleepingByTime() || petView == null) return
        if (settings.edgeWalkEnabled && !allowWhileWalking) return
        val roll = Random.nextInt(5)
        currentState = when (roll) {
            0 -> PetState.PLAY
            1 -> PetState.RANDOM
            2 -> PetState.BORED
            else -> if (settings.mood >= 50) PetState.HAPPY else PetState.SAD
        }
        petView?.show(assets.randomFileFor(currentState))
        applyVisualState()
    }

    private fun evaluateLocalTime() {
        val now = LocalTime.now()
        if (TimeBehaviorConfig.isSleepTime(now)) {
            if (currentState != PetState.SLEEP) setTimedState(PetState.SLEEP, "sleep")
            walkAnimator?.cancel()
            return
        }
        if (currentState == PetState.SLEEP) setTimedState(PetState.IDLE, "wake")
        val key = "${now.hour}:${now.minute}"
        if (now.hour == TimeBehaviorConfig.lunchTime.hour && now.minute == 0 && lastTimeActionKey != key) {
            lastTimeActionKey = key
            setTimedState(PetState.FEED, "lunch")
        } else if (currentState == PetState.FEED && now.minute != 0) {
            setTimedState(PetState.IDLE, "day")
        }
        val canStartWalking = currentState == PetState.IDLE || currentState == PetState.WALK
        if (canStartWalking && walkAnimator?.isRunning != true) updateEdgeWalk()
    }

    private fun setTimedState(state: PetState, key: String) {
        if (lastTimeActionKey == key && currentState == state) return
        lastTimeActionKey = key
        currentState = state
        petView?.show(assets.randomFileFor(state))
        applyVisualState()
        if (state == PetState.SLEEP) {
            idleAnimator?.cancel()
            walkAnimator?.cancel()
        } else {
            startIdleAnimation()
        }
    }

    private fun isSleepingByTime() = TimeBehaviorConfig.isSleepTime(LocalTime.now())

    private fun resizeCanvasForCurrentScreen() {
        val view = petView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val canvasSize = PetCanvasConfig.resolve(this)
        params.width = canvasSize.widthPx
        params.height = canvasSize.heightPx
        val screen = resources.displayMetrics
        params.x = params.x.coerceIn(0, (screen.widthPixels - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screen.heightPixels - params.height).coerceAtLeast(0))
        windowManager.updateViewLayout(view, params)
    }

    private fun startIdleAnimation() {
        idleAnimator?.cancel()
        val distance = 8 * resources.displayMetrics.density
        idleAnimator = ObjectAnimator.ofFloat(petView, View.TRANSLATION_Y, 0f, -distance, 0f).apply {
            duration = 1800
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun nextExpression() {
        walkAnimator?.cancel()
        playRandomBehavior(allowWhileWalking = true)
        petView?.animate()?.rotationBy(8f)?.setDuration(100)?.withEndAction {
            petView?.animate()?.rotation(0f)?.setDuration(120)?.start()
        }?.start()
        resumeFreeWalkAfter(INTERACTION_DISPLAY_MS)
    }

    private fun resumeFreeWalkAfter(delayMs: Long) {
        interactionHandler.removeCallbacksAndMessages(null)
        if (settings.edgeWalkEnabled) {
            interactionHandler.postDelayed({ updateFreeWalkRandom() }, delayMs)
        }
    }

    private inner class PetTouchListener(
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var downX = 0f
        private var downY = 0f
        private var moved = false
        private var lastTapAt = 0L

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    downX = event.rawX
                    downY = event.rawY
                    downAt = android.os.SystemClock.uptimeMillis()
                    moved = false
                    idleAnimator?.pause()
                    walkAnimator?.cancel()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > viewConfigurationTouchSlop || abs(dy) > viewConfigurationTouchSlop) {
                        moved = true
                    }
                    val screen = resources.displayMetrics
                    params.x = (initialX + dx).coerceIn(
                        0,
                        (screen.widthPixels - view.width).coerceAtLeast(0)
                    )
                    params.y = (initialY + dy).coerceIn(
                        0,
                        (screen.heightPixels - view.height).coerceAtLeast(0)
                    )
                    windowManager.updateViewLayout(view, params)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    idleAnimator?.resume()
                    val heldFor = android.os.SystemClock.uptimeMillis() - downAt
                    if (!moved && event.actionMasked == MotionEvent.ACTION_UP) {
                        if (heldFor >= LONG_PRESS_MS) {
                            showControlPanel()
                        } else {
                            val now = android.os.SystemClock.uptimeMillis()
                            if (now - lastTapAt <= DOUBLE_TAP_MS) {
                                touchInteraction()
                                lastTapAt = 0L
                            } else {
                                lastTapAt = now
                                nextExpression()
                            }
                        }
                    }
                    if (moved || event.actionMasked == MotionEvent.ACTION_CANCEL) updateEdgeWalk()
                    getSharedPreferences("pet_position", MODE_PRIVATE).edit()
                        .putInt("x", params.x)
                        .putInt("y", params.y)
                        .apply()
                    return true
                }
            }
            return false
        }

        private var downAt = 0L

        private val viewConfigurationTouchSlop: Int
            get() = (8 * resources.displayMetrics.density).toInt()
    }

    private fun touchInteraction() {
        if (isSleepingByTime()) return
        walkAnimator?.cancel()
        settings.mood = settings.mood + 5
        currentState = PetState.TOUCH
        petView?.show(assets.randomFileFor(PetState.TOUCH))
        applyVisualState()
        petView?.animate()?.scaleX(1.08f)?.scaleY(1.08f)?.setDuration(120)?.withEndAction {
            petView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(180)?.start()
        }?.start()
        resumeFreeWalkAfter(INTERACTION_DISPLAY_MS)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_pet_notification)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(getString(R.string.notification_text))
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    companion object {
        const val ACTION_REFRESH = "com.example.desktoppet.action.REFRESH"
        const val ACTION_REFRESH_SETTINGS = "com.example.desktoppet.action.REFRESH_SETTINGS"
        const val ACTION_SET_STATE = "com.example.desktoppet.action.SET_STATE"
        const val EXTRA_STATE = "state"
        private const val LONG_PRESS_MS = 650L
        private const val DOUBLE_TAP_MS = 350L
        private const val INTERACTION_DISPLAY_MS = 1_800L
        private const val CHANNEL_ID = "desktop_pet_channel"
        private const val NOTIFICATION_ID = 1001
        private const val RUNTIME_PREFERENCES = "pet_runtime"
        private const val KEY_LAST_ERROR = "last_start_error"
        @Volatile var isRunning = false
            private set
    }
}
