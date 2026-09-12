package com.example.desktoppet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.desktoppet.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var assets: PetAssetRepository
    private lateinit var settings: PetSettings
    private var starting = false
    private var settingsExpanded = false
    private var startupCheck: Runnable? = null

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) requestPermissionsAndStart()
        updateStatus()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startPetService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        // Keep the single column comfortably readable on tablets and wide emulator windows.
        binding.contentColumn.layoutParams = binding.contentColumn.layoutParams.apply {
            width = minOf(resources.displayMetrics.widthPixels, (600 * resources.displayMetrics.density).toInt())
        }
        assets = PetAssetRepository(this)
        settings = PetSettings(this)

        binding.petPreview.fitPreviewToCanvas()
        binding.petPreview.show(assets.fileFor(PetState.IDLE))
        binding.startButton.setOnClickListener { requestPermissionsAndStart() }
        binding.stopButton.setOnClickListener { stopPet() }
        binding.petButton.setOnClickListener { changeMood(PetState.TOUCH, 5) }
        binding.sleepButton.setOnClickListener { sendState(PetState.SLEEP) }
        binding.feedButton.setOnClickListener { feedPet() }
        binding.todoButton.setOnClickListener {
            startActivity(Intent(this, PetPanelActivity::class.java))
        }
        settingsExpanded = savedInstanceState?.getBoolean(KEY_SETTINGS_EXPANDED) ?: false
        updateSettingsExpansion()
        binding.settingsToggle.setOnClickListener {
            settingsExpanded = !settingsExpanded
            updateSettingsExpansion()
        }
        binding.freeWalkSwitch.isChecked = settings.edgeWalkEnabled
        binding.freeWalkSwitch.setOnCheckedChangeListener { _, enabled ->
            settings.edgeWalkEnabled = enabled
            sendServiceAction(PetService.ACTION_REFRESH_SETTINGS)
        }
        binding.petSizeSeek.progress = ((settings.petScale - 0.55f) / 0.9f * 100f).toInt()
        binding.petSizeSeek.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            settings.petScale = 0.55f + progress / 100f * 0.9f
            updateSettingLabels()
            sendServiceAction(PetService.ACTION_REFRESH_SETTINGS)
        })
        binding.walkSpeedSeek.progress = ((settings.walkSpeed - WALK_SPEED_MIN) * 100 /
            (WALK_SPEED_MAX - WALK_SPEED_MIN)).coerceIn(0, 100)
        binding.walkSpeedSeek.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            settings.walkSpeed = WALK_SPEED_MIN + progress * (WALK_SPEED_MAX - WALK_SPEED_MIN) / 100
            updateSettingLabels()
            sendServiceAction(PetService.ACTION_REFRESH_SETTINGS)
        })

        updateValues()
        updateSettingLabels()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::settings.isInitialized) {
            updateValues()
            binding.freeWalkSwitch.isChecked = settings.edgeWalkEnabled
            updateStatus()
        }
    }

    private fun requestPermissionsAndStart() {
        if (starting || PetService.isRunning) return
        if (!Settings.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startPetService()
        }
    }

    private fun startPetService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show()
            updateStatus(false)
            return
        }
        starting = true
        updateStatus(false)
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, PetService::class.java))
        }.onSuccess {
            updateStatus(false)
            waitForServiceStartup(0)
        }.onFailure {
            starting = false
            updateStatus(false)
            Toast.makeText(
                this,
                getString(R.string.start_failed, it.localizedMessage ?: it.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun waitForServiceStartup(attempt: Int) {
        startupCheck = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            if (PetService.isRunning) {
                starting = false
                updateStatus(true)
                return@Runnable
            }
            val lastError = getSharedPreferences("pet_runtime", MODE_PRIVATE)
                .getString("last_start_error", null)
            if (lastError != null || attempt >= MAX_SERVICE_START_ATTEMPTS) {
                starting = false
                updateStatus(false)
                showLastStartError()
                return@Runnable
            }
            waitForServiceStartup(attempt + 1)
        }.also { binding.root.postDelayed(it, SERVICE_START_POLL_INTERVAL_MS) }
    }

    private fun showLastStartError() {
        val message = getSharedPreferences("pet_runtime", MODE_PRIVATE)
            .getString("last_start_error", null) ?: return
        Toast.makeText(this, getString(R.string.start_failed, message), Toast.LENGTH_LONG).show()
    }

    private fun stopPet() {
        stopService(Intent(this, PetService::class.java))
        binding.petPreview.show(assets.fileFor(PetState.IDLE))
        updateStatus(false)
    }

    private fun feedPet() {
        if (settings.foodCount <= 0) {
            Toast.makeText(this, R.string.no_food, Toast.LENGTH_SHORT).show()
            return
        }
        settings.foodCount -= 1
        settings.hunger += 20
        settings.mood += 3
        updateValues()
        sendState(PetState.FEED)
    }

    private fun changeMood(state: PetState, delta: Int) {
        settings.mood += delta
        updateValues()
        sendState(state)
    }

    private fun sendState(state: PetState) {
        if (!PetService.isRunning) {
            Toast.makeText(this, R.string.start_pet_first, Toast.LENGTH_SHORT).show()
            return
        }
        startService(
            Intent(this, PetService::class.java)
                .setAction(PetService.ACTION_SET_STATE)
                .putExtra(PetService.EXTRA_STATE, state.key)
        )
        binding.petPreview.show(assets.fileFor(state))
        binding.companionMessage.setText(when (state) {
            PetState.FEED -> R.string.hero_message_feed
            PetState.TOUCH -> R.string.hero_message_pet
            PetState.SLEEP -> R.string.hero_message_sleep
            else -> R.string.hero_message_running
        })
    }

    private fun updateValues() {
        val mood = settings.mood
        val hunger = settings.hunger
        val food = settings.foodCount
        binding.moodText.text = mood.toString()
        binding.moodText.contentDescription = getString(R.string.mood_accessibility, mood)
        binding.moodProgress.setProgress(mood, true)
        binding.hungerText.text = hunger.toString()
        binding.hungerText.contentDescription = getString(R.string.hunger_accessibility, hunger)
        binding.hungerProgress.setProgress(hunger, true)
        binding.foodText.text = food.toString()
        binding.foodText.contentDescription = getString(R.string.food_accessibility, food)
    }

    private fun updateStatus(running: Boolean = PetService.isRunning) {
        binding.statusText.text = when {
            starting -> getString(R.string.status_starting)
            !Settings.canDrawOverlays(this) -> getString(R.string.status_permission)
            running -> getString(R.string.status_running)
            else -> getString(R.string.status_stopped)
        }
        binding.statusBadge.setText(when {
            starting -> R.string.badge_starting
            running -> R.string.badge_running
            else -> R.string.badge_stopped
        })
        binding.companionMessage.setText(if (running) R.string.hero_message_running else R.string.hero_message_idle)
        binding.startButton.visibility = if (running) View.GONE else View.VISIBLE
        binding.stopButton.visibility = if (running) View.VISIBLE else View.GONE
        binding.startButton.isEnabled = !running && !starting
        binding.stopButton.isEnabled = running
        binding.feedButton.isEnabled = running
        binding.petButton.isEnabled = running
        binding.sleepButton.isEnabled = running
    }

    private fun updateSettingLabels() {
        binding.petSizeValue.text = getString(R.string.percent_value, (settings.petScale * 100).toInt())
        binding.walkSpeedValue.text = getString(R.string.percent_value, binding.walkSpeedSeek.progress)
    }

    private fun updateSettingsExpansion() {
        binding.settingsDetails.visibility = if (settingsExpanded) View.VISIBLE else View.GONE
        binding.settingsSummary.setText(if (settingsExpanded) R.string.settings_expanded else R.string.settings_collapsed)
        binding.settingsChevron.rotation = if (settingsExpanded) 180f else 0f
        binding.settingsToggle.contentDescription = getString(if (settingsExpanded) R.string.settings_collapse else R.string.settings_expand)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_SETTINGS_EXPANDED, settingsExpanded)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        startupCheck?.let { binding.root.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun simpleSeekBarListener(onChanged: (Int) -> Unit) =
        object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChanged(progress)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        }

    private fun sendServiceAction(action: String) {
        if (PetService.isRunning) startService(Intent(this, PetService::class.java).setAction(action))
    }

    companion object {
        private const val KEY_SETTINGS_EXPANDED = "settings_expanded"
        private const val SERVICE_START_POLL_INTERVAL_MS = 500L
        private const val MAX_SERVICE_START_ATTEMPTS = 16
        private const val WALK_SPEED_MIN = 10
        private const val WALK_SPEED_MAX = 500
    }
}
