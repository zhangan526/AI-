package com.geekathon.guardpet

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.ActivityOptions
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
import dev.pranav.reef.accessibility.BlockerService
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
    private var menuOverlay: PetMenuOverlay? = null
    private var sleepLockOverlay: SleepLockOverlay? = null
    private var focusTimerOverlay: FocusTimerOverlay? = null
    private val tapHandler = Handler(Looper.getMainLooper())
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
            ACTION_SLEEP_LOCK -> showSleepLock()
            ACTION_SLEEP_UNLOCK -> hideSleepLock()
            ACTION_SHOW_PET -> {
                petView?.visibility = View.VISIBLE
                resumeFreeWalkAfter(INTERACTION_DISPLAY_MS)
            }
            ACTION_OPEN_TIMER -> openFocusTimer()
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
        tapHandler.removeCallbacksAndMessages(null)
        panelOverlay?.close()
        panelOverlay = null
        menuOverlay?.close()
        menuOverlay = null
        sleepLockOverlay?.close()
        sleepLockOverlay = null
        focusTimerOverlay?.close()
        focusTimerOverlay = null
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

    fun openControlPanel() {
        showControlPanel()
    }

    fun extractText() {
        menuOverlay?.close()
        petView?.visibility = View.INVISIBLE
        tapHandler.postDelayed({
            val raw = BlockerService.captureVisibleText()
            if (raw.isEmpty() && !BlockerService.isConnected) {
                petView?.visibility = View.VISIBLE
                Toast.makeText(this, R.string.a11y_required, Toast.LENGTH_LONG).show()
                return@postDelayed
            }
            val tokens = TextTokenizer.splitAll(raw)
            if (tokens.isEmpty()) {
                petView?.visibility = View.VISIBLE
                Toast.makeText(this, R.string.bigbang_empty, Toast.LENGTH_LONG).show()
                return@postDelayed
            }
            TextCaptureHolder.tokens = tokens
            runCatching {
                startActivity(
                    Intent(this, BigBangActivity::class.java)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        ),
                    ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
                )
            }.onFailure {
                petView?.visibility = View.VISIBLE
                Toast.makeText(
                    this,
                    getString(R.string.panel_open_failed, it.localizedMessage ?: it.javaClass.simpleName),
                    Toast.LENGTH_LONG
                ).show()
            }
        }, CAPTURE_DELAY_MS)
    }

    fun openFocusTimer() {
        menuOverlay?.close()
        runCatching {
            if (focusTimerOverlay == null) {
                focusTimerOverlay = FocusTimerOverlay(this, windowManager)
            }
            focusTimerOverlay?.show()
        }.onFailure {
            focusTimerOverlay = null
            Toast.makeText(
                this,
                getString(R.string.panel_open_failed, it.localizedMessage ?: it.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun openFlashNote(prefill: String? = null, source: String = "typed") {
        menuOverlay?.close()
        startActivity(
            Intent(this, FlashNoteActivity::class.java)
                .putExtra(FlashNoteActivity.EXTRA_PREFILL, prefill)
                .putExtra(FlashNoteActivity.EXTRA_SOURCE, source)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun showFeatureMenu() {
        runCatching {
            check(Settings.canDrawOverlays(this)) { getString(R.string.overlay_permission_required) }
            val view = petView ?: return@runCatching
            val petParams = view.layoutParams as? WindowManager.LayoutParams ?: return@runCatching
            menuOverlay?.close()
            panelOverlay?.close()
            menuOverlay = PetMenuOverlay(
                this,
                windowManager,
                petParams.x,
                petParams.y,
                petParams.width,
                petParams.height
            )
            menuOverlay?.show()
        }.onFailure {
            Toast.makeText(
                this,
                getString(R.string.panel_open_failed, it.localizedMessage ?: it.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showSleepLock() {
        if (!Settings.canDrawOverlays(this)) return
        currentState = PetState.SLEEP
        petView?.show(assets.randomFileFor(PetState.SLEEP))
        applyVisualState()
        idleAnimator?.cancel()
        walkAnimator?.cancel()
        if (sleepLockOverlay == null) {
            sleepLockOverlay = SleepLockOverlay(this, windowManager)
        }
        sleepLockOverlay?.show()
    }

    private fun hideSleepLock() {
        HabitGuardian.clearSleepLock()
        sleepLockOverlay?.close()
        sleepLockOverlay = null
    }

    private fun showControlPanel() {
        runCatching {
            check(Settings.canDrawOverlays(this)) { getString(R.string.overlay_permission_required) }
            val view = petView ?: return@runCatching
            val petParams = view.layoutParams as? WindowManager.LayoutParams
                ?: return@runCatching
            menuOverlay?.close()
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
        hideSleepLock()
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
        petView?.animate()?.scaleX(1.08f)?.scaleY(1.08f)?.rotationBy(8f)?.setDuration(120)?.withEndAction {
            petView?.animate()?.scaleX(1f)?.scaleY(1f)?.rotation(0f)?.setDuration(180)?.start()
        }?.start()
        resumeFreeWalkAfter(INTERACTION_DISPLAY_MS)
    }

    private fun performAssigned(gesture: PetGesture) {
        performAction(settings.actionFor(gesture))
    }

    fun performAction(action: PetAction) {
        when (action) {
            PetAction.CHANGE_STYLE -> nextExpression()
            PetAction.FEATURE_MENU -> showFeatureMenu()
            PetAction.CONTROL_PANEL -> showControlPanel()
            PetAction.EXTRACT_TEXT -> extractText()
            PetAction.FLASH_NOTE -> openFlashNote()
            PetAction.FOCUS_TIMER -> openFocusTimer()
            PetAction.PET -> touchInteraction()
        }
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
        private var lastMoveX = 0f
        private var lastMoveY = 0f
        private var lastDirX = 0
        private var lastDirY = 0
        private var reverseCount = 0
        private var moved = false
        private var lastTapAt = 0L
        private var secondTap = false
        private var holdFired = false
        private var shakeFired = false

        private val singleTapRunnable = Runnable { performAssigned(PetGesture.SINGLE_TAP) }
        private val holdRunnable = Runnable {
            holdFired = true
            performAssigned(PetGesture.DOUBLE_TAP_HOLD)
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    downX = event.rawX
                    downY = event.rawY
                    lastMoveX = event.rawX
                    lastMoveY = event.rawY
                    lastDirX = 0
                    lastDirY = 0
                    reverseCount = 0
                    downAt = android.os.SystemClock.uptimeMillis()
                    moved = false
                    holdFired = false
                    shakeFired = false
                    secondTap = downAt - lastTapAt <= DOUBLE_TAP_MS
                    if (secondTap) {
                        tapHandler.removeCallbacks(singleTapRunnable)
                        tapHandler.postDelayed(holdRunnable, LONG_PRESS_MS)
                    }
                    idleAnimator?.pause()
                    walkAnimator?.cancel()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > viewConfigurationTouchSlop || abs(dy) > viewConfigurationTouchSlop) {
                        moved = true
                        tapHandler.removeCallbacks(holdRunnable)
                    }
                    trackShake(event.rawX, event.rawY)
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
                    if (!shakeFired && reverseCount >= SHAKE_REVERSES) {
                        shakeFired = true
                        tapHandler.removeCallbacks(holdRunnable)
                        performAssigned(PetGesture.SHAKE)
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    idleAnimator?.resume()
                    tapHandler.removeCallbacks(holdRunnable)
                    val cancelled = event.actionMasked == MotionEvent.ACTION_CANCEL
                    if (!cancelled && !moved && !holdFired && !shakeFired) {
                        val now = android.os.SystemClock.uptimeMillis()
                        if (secondTap) {
                            tapHandler.removeCallbacks(singleTapRunnable)
                            performAssigned(PetGesture.DOUBLE_TAP)
                            lastTapAt = 0L
                        } else {
                            lastTapAt = now
                            tapHandler.removeCallbacks(singleTapRunnable)
                            tapHandler.postDelayed(singleTapRunnable, DOUBLE_TAP_MS)
                        }
                    } else {
                        lastTapAt = 0L
                    }
                    if (moved || cancelled) updateEdgeWalk()
                    getSharedPreferences("pet_position", MODE_PRIVATE).edit()
                        .putInt("x", params.x)
                        .putInt("y", params.y)
                        .apply()
                    return true
                }
            }
            return false
        }

        private fun trackShake(rawX: Float, rawY: Float) {
            val segment = SHAKE_SEGMENT_PX * resources.displayMetrics.density
            val vx = rawX - lastMoveX
            val vy = rawY - lastMoveY
            if (abs(vx) >= segment) {
                val dir = if (vx > 0) 1 else -1
                if (lastDirX != 0 && dir != lastDirX) reverseCount++
                lastDirX = dir
                lastMoveX = rawX
            }
            if (abs(vy) >= segment) {
                val dir = if (vy > 0) 1 else -1
                if (lastDirY != 0 && dir != lastDirY) reverseCount++
                lastDirY = dir
                lastMoveY = rawY
            }
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
        const val ACTION_REFRESH = "com.geekathon.guardpet.action.REFRESH"
        const val ACTION_REFRESH_SETTINGS = "com.geekathon.guardpet.action.REFRESH_SETTINGS"
        const val ACTION_SET_STATE = "com.geekathon.guardpet.action.SET_STATE"
        const val ACTION_SLEEP_LOCK = "com.geekathon.guardpet.action.SLEEP_LOCK"
        const val ACTION_SLEEP_UNLOCK = "com.geekathon.guardpet.action.SLEEP_UNLOCK"
        const val ACTION_SHOW_PET = "com.geekathon.guardpet.action.SHOW_PET"
        const val ACTION_OPEN_TIMER = "com.geekathon.guardpet.action.OPEN_TIMER"
        const val EXTRA_STATE = "state"
        private const val LONG_PRESS_MS = 650L
        private const val DOUBLE_TAP_MS = 350L
        private const val SHAKE_REVERSES = 4
        private const val SHAKE_SEGMENT_PX = 36f
        private const val CAPTURE_DELAY_MS = 280L
        private const val INTERACTION_DISPLAY_MS = 1_800L
        private const val CHANNEL_ID = "desktop_pet_channel"
        private const val NOTIFICATION_ID = 1001
        private const val RUNTIME_PREFERENCES = "pet_runtime"
        private const val KEY_LAST_ERROR = "last_start_error"
        @Volatile var isRunning = false
            private set
    }
}
