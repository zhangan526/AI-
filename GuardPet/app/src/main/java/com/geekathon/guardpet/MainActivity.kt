package com.geekathon.guardpet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.geekathon.guardpet.databinding.ActivityMainBinding
import dev.pranav.reef.PermissionsCheckActivity
import dev.pranav.reef.util.checkAndRequestMissingPermissions
import dev.pranav.reef.util.hasUsageStatsPermission
import dev.pranav.reef.util.isAccessibilityServiceEnabledForBlocker

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var assets: PetAssetRepository
    private lateinit var settings: PetSettings
    private var hasPromptedPermissions = false

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
        bindGestureShortcut(binding.gestureSingleTap, PetGesture.SINGLE_TAP)
        bindGestureShortcut(binding.gestureDoubleTap, PetGesture.DOUBLE_TAP)
        bindGestureShortcut(binding.gestureShake, PetGesture.SHAKE)
        bindGestureShortcut(binding.gestureDoubleTapHold, PetGesture.DOUBLE_TAP_HOLD)
        binding.focusButton.setOnClickListener {
            if (!PetService.isRunning) {
                Toast.makeText(this, R.string.start_pet_first, Toast.LENGTH_SHORT).show()
            } else {
                sendServiceAction(PetService.ACTION_OPEN_TIMER)
            }
        }
        binding.reefSettingsButton.setOnClickListener { FocusLauncher.openSettings(this) }
        binding.flashNoteButton.setOnClickListener {
            startActivity(Intent(this, FlashNoteActivity::class.java))
        }
        binding.aiAgentButton.setOnClickListener {
            startActivity(Intent(this, AiAgentActivity::class.java))
        }
        binding.focusAttribution.setOnClickListener { FocusLauncher.openAbout(this) }

        updateValues()
        updateStatus()
        renderFlashNotes()
    }

    override fun onResume() {
        super.onResume()
        if (::settings.isInitialized) {
            updateValues()
            updateStatus()
            renderFlashNotes()
        }
        if (!hasPromptedPermissions) {
            hasPromptedPermissions = true
            checkAndRequestMissingPermissions()
        }
    }

    private fun requestPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this) ||
            !hasUsageStatsPermission() ||
            !isAccessibilityServiceEnabledForBlocker()
        ) {
            startActivity(Intent(this, PermissionsCheckActivity::class.java))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            startActivity(Intent(this, PermissionsCheckActivity::class.java))
            return
        }
        startPetService()
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
            verifyServiceStart(0)
        }.onFailure {
            updateStatus(false)
            Toast.makeText(
                this,
                getString(R.string.start_failed, it.localizedMessage ?: it.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun verifyServiceStart(attempt: Int) {
        if (PetService.isRunning) {
            updateStatus(true)
            return
        }
        if (attempt < SERVICE_START_MAX_ATTEMPTS) {
            binding.root.postDelayed(
                { verifyServiceStart(attempt + 1) },
                SERVICE_START_CHECK_INTERVAL_MS
            )
            return
        }
        updateStatus(false)
        showLastStartError()
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
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        fun mark(granted: Boolean) =
            getString(if (granted) R.string.permission_ok else R.string.permission_missing)
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        binding.permissionStatus.text = buildString {
            appendLine(getString(R.string.permission_overlay, mark(Settings.canDrawOverlays(this@MainActivity))))
            appendLine(getString(R.string.permission_usage, mark(hasUsageStatsPermission())))
            appendLine(getString(R.string.permission_a11y, mark(isAccessibilityServiceEnabledForBlocker())))
            append(getString(R.string.permission_mic, mark(micGranted)))
        }
    }

    private fun renderFlashNotes() {
        binding.flashNoteContainer.removeAllViews()
        val notes = runCatching { FlashNoteStore.all() }.getOrDefault(emptyList())
        if (notes.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.flash_note_empty_list)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                textSize = 14f
            }
            binding.flashNoteContainer.addView(empty)
            return
        }
        val inflater = LayoutInflater.from(this)
        notes.forEach { note ->
            val row = inflater.inflate(R.layout.item_flash_note, binding.flashNoteContainer, false)
            val category = row.findViewById<TextView>(R.id.noteCategory)
            val text = row.findViewById<TextView>(R.id.noteText)
            val delete = row.findViewById<TextView>(R.id.noteDelete)
            val categoryLabel = getString(note.category.labelRes)
            category.text = if (note.scheduleDate.isNullOrBlank()) {
                categoryLabel
            } else {
                "$categoryLabel · ${note.scheduleDate}"
            }
            text.text = note.text
            delete.setOnClickListener {
                FlashNoteStore.delete(note.id)
                renderFlashNotes()
            }
            binding.flashNoteContainer.addView(row)
        }
    }

    private fun bindGestureShortcut(spinner: Spinner, gesture: PetGesture) {
        val actions = PetAction.entries
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            actions.map { getString(it.labelRes) }
        )
        spinner.setSelection(actions.indexOf(settings.actionFor(gesture)).coerceAtLeast(0), false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settings.setAction(gesture, actions[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
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
        private const val SERVICE_START_CHECK_INTERVAL_MS = 500L
        private const val SERVICE_START_MAX_ATTEMPTS = 8
        private const val WALK_SPEED_MIN = 10
        private const val WALK_SPEED_MAX = 500
    }
}
