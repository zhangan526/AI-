package com.example.desktoppet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.desktoppet.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var assets: PetAssetRepository
    private lateinit var settings: PetSettings

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) startPetService()
        updateStatus()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startPetService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        assets = PetAssetRepository(this)
        settings = PetSettings(this)

        binding.petPreview.show(assets.randomFileFor(PetState.HAPPY))
        binding.startButton.setOnClickListener { requestPermissionsAndStart() }
        binding.stopButton.setOnClickListener { stopPet() }
        binding.petButton.setOnClickListener { changeMood(PetState.TOUCH, 5) }
        binding.sleepButton.setOnClickListener { sendState(PetState.SLEEP) }
        binding.feedButton.setOnClickListener { feedPet() }
        binding.freeWalkSwitch.isChecked = settings.edgeWalkEnabled
        binding.freeWalkSwitch.setOnCheckedChangeListener { _, enabled ->
            settings.edgeWalkEnabled = enabled
            sendServiceAction(PetService.ACTION_REFRESH_SETTINGS)
        }
        binding.petSizeSeek.progress = ((settings.petScale - 0.55f) / 0.9f * 100f).toInt()
        binding.petSizeSeek.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            settings.petScale = 0.55f + progress / 100f * 0.9f
            sendServiceAction(PetService.ACTION_REFRESH_SETTINGS)
        })
        binding.walkSpeedSeek.progress = ((settings.walkSpeed - WALK_SPEED_MIN) * 100 /
            (WALK_SPEED_MAX - WALK_SPEED_MIN)).coerceIn(0, 100)
        binding.walkSpeedSeek.setOnSeekBarChangeListener(simpleSeekBarListener { progress ->
            settings.walkSpeed = WALK_SPEED_MIN + progress * (WALK_SPEED_MAX - WALK_SPEED_MIN) / 100
            sendServiceAction(PetService.ACTION_REFRESH_SETTINGS)
        })
        binding.focusButton.setOnClickListener { FocusLauncher.openTimer(this) }
        binding.focusAttribution.setOnClickListener { FocusLauncher.openAbout(this) }

        updateValues()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::settings.isInitialized) {
            updateValues()
            updateStatus()
        }
    }

    private fun requestPermissionsAndStart() {
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
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, PetService::class.java))
        }.onSuccess {
            updateStatus(true)
            binding.root.postDelayed({
                if (!PetService.isRunning) {
                    updateStatus(false)
                    showLastStartError()
                }
            }, SERVICE_START_CHECK_DELAY_MS)
        }.onFailure {
            updateStatus(false)
            Toast.makeText(
                this,
                getString(R.string.start_failed, it.localizedMessage ?: it.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showLastStartError() {
        val message = getSharedPreferences("pet_runtime", MODE_PRIVATE)
            .getString("last_start_error", null) ?: return
        Toast.makeText(this, getString(R.string.start_failed, message), Toast.LENGTH_LONG).show()
    }

    private fun stopPet() {
        stopService(Intent(this, PetService::class.java))
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
        binding.petPreview.show(assets.randomFileFor(state))
    }

    private fun updateValues() {
        binding.moodText.text = getString(R.string.mood_value, settings.mood)
        binding.moodProgress.progress = settings.mood
        binding.hungerText.text = getString(R.string.hunger_value, settings.hunger)
        binding.hungerProgress.progress = settings.hunger
        binding.foodText.text = getString(R.string.food_value, settings.foodCount)
    }

    private fun updateStatus(running: Boolean = PetService.isRunning) {
        binding.statusText.text = when {
            !Settings.canDrawOverlays(this) -> getString(R.string.status_permission)
            running -> getString(R.string.status_running)
            else -> getString(R.string.status_stopped)
        }
        binding.startButton.isEnabled = !running
        binding.stopButton.isEnabled = running
        binding.feedButton.isEnabled = running
        binding.petButton.isEnabled = running
        binding.sleepButton.isEnabled = running
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
        private const val SERVICE_START_CHECK_DELAY_MS = 1_200L
        private const val WALK_SPEED_MIN = 10
        private const val WALK_SPEED_MAX = 500
    }
}
