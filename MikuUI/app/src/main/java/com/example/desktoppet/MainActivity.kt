package com.example.desktoppet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import androidx.core.widget.doAfterTextChanged
import com.example.desktoppet.databinding.ActivityMainBinding
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var assets: PetAssetRepository
    private lateinit var settings: PetSettings
    private var starting = false
    private var settingsExpanded = false
    private var generatingAppearance = false
    private var startupCheck: Runnable? = null
    private val appearanceExecutor = Executors.newSingleThreadExecutor()
    private var pendingUploadBytes: ByteArray? = null
    private var pendingUploadMime: String = "image/jpeg"

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) requestPermissionsAndStart()
        updateStatus()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startPetService() }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onImagePicked(uri)
    }

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
        binding.settingsToggle.setOnLongClickListener {
            binding.developerApiSection.visibility = View.VISIBLE
            settingsExpanded = true
            updateSettingsExpansion()
            Toast.makeText(this, R.string.developer_api_unlocked, Toast.LENGTH_SHORT).show()
            true
        }
        setupAppearanceCustomFields()
        setupAppearanceMode()
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
        binding.generateAppearanceButton.setOnClickListener { generateAppearanceFromIdea() }
        binding.restoreAppearanceButton.setOnClickListener { restoreDefaultAppearance() }
        binding.pickAppearanceImageButton.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.generateFromUploadButton.setOnClickListener { generateAppearanceFromUpload() }
        binding.applyUploadButton.setOnClickListener { applyUploadedAppearance() }
        assets.customIdea()?.let { binding.appearanceIdeaInput.setText(it) }
        if (settings.dashScopeApiKey.isNotBlank() &&
            settings.dashScopeApiKey != BuildConfig.DASHSCOPE_API_KEY
        ) {
            binding.dashScopeApiKeyInput.setText(settings.dashScopeApiKey)
        }
        if (settings.deepSeekApiKey.isNotBlank() &&
            settings.deepSeekApiKey != BuildConfig.DEEPSEEK_API_KEY
        ) {
            binding.deepSeekApiKeyInput.setText(settings.deepSeekApiKey)
        }
        if (settings.openAiApiKey.isNotBlank() &&
            settings.openAiApiKey != BuildConfig.OPENAI_API_KEY
        ) {
            binding.openAiApiKeyInput.setText(settings.openAiApiKey)
        }
        binding.openAiBaseUrlInput.setText(settings.openAiBaseUrl)
        binding.dashScopeApiKeyInput.doAfterTextChanged { text ->
            settings.dashScopeApiKey = text?.toString().orEmpty()
        }
        binding.deepSeekApiKeyInput.doAfterTextChanged { text ->
            settings.deepSeekApiKey = text?.toString().orEmpty()
        }
        binding.openAiApiKeyInput.doAfterTextChanged { text ->
            settings.openAiApiKey = text?.toString().orEmpty()
        }
        binding.openAiBaseUrlInput.doAfterTextChanged { text ->
            settings.openAiBaseUrl = text?.toString().orEmpty()
        }

        updateValues()
        updateSettingLabels()
        updateAppearanceStatus()
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

    private fun generateAppearanceFromIdea() {
        if (generatingAppearance) return
        val guide = currentPromptGuide()
        val validation = runCatching { guide.composeIdea() }
        if (validation.isFailure) {
            Toast.makeText(this, R.string.appearance_empty, Toast.LENGTH_SHORT).show()
            return
        }
        persistApiKeysFromInput()
        persistAppearanceMode()
        val mode = settings.appearanceMode
        val credentials = settings.appearanceCredentials()
        if (mode == PetAppearanceGenerator.GenerationMode.QUALITY && !credentials.hasQwen()) {
            Toast.makeText(this, R.string.dashscope_key_missing, Toast.LENGTH_LONG).show()
            binding.developerApiSection.visibility = View.VISIBLE
            settingsExpanded = true
            updateSettingsExpansion()
            return
        }
        val statusRes = if (mode == PetAppearanceGenerator.GenerationMode.QUALITY) {
            R.string.appearance_generating_quality
        } else {
            R.string.appearance_generating_fast
        }
        setAppearanceGenerating(true, statusRes)
        appearanceExecutor.execute {
            val outcome = runCatching {
                PetAppearanceGenerator.generateFromIdea(guide, credentials, mode) { stage ->
                    runOnUiThread { updateAgentProgress(stage) }
                }
            }
            runOnUiThread { handleAppearanceResult(outcome, requestedMode = mode) }
        }
    }

    private fun generateAppearanceFromUpload() {
        if (generatingAppearance) return
        val bytes = pendingUploadBytes
        if (bytes == null) {
            Toast.makeText(this, R.string.appearance_need_upload, Toast.LENGTH_SHORT).show()
            return
        }
        persistApiKeysFromInput()
        persistAppearanceMode()
        // 「按图 AI 生成」始终走千问图生图，不受快速/精细开关限制
        val credentials = settings.appearanceCredentials()
        if (!credentials.hasQwen()) {
            Toast.makeText(this, R.string.dashscope_key_missing, Toast.LENGTH_LONG).show()
            binding.developerApiSection.visibility = View.VISIBLE
            settingsExpanded = true
            updateSettingsExpansion()
            return
        }
        val guide = currentPromptGuide()
        val mode = PetAppearanceGenerator.GenerationMode.QUALITY
        val statusRes = R.string.appearance_generating_upload
        setAppearanceGenerating(true, statusRes)
        appearanceExecutor.execute {
            val outcome = runCatching {
                PetAppearanceGenerator.generateFromUpload(
                    bytes,
                    pendingUploadMime,
                    guide,
                    credentials,
                    mode
                ) { stage ->
                    runOnUiThread { updateAgentProgress(stage) }
                }
            }
            runOnUiThread { handleAppearanceResult(outcome, requestedMode = mode) }
        }
    }

    private fun applyUploadedAppearance() {
        if (generatingAppearance) return
        val bytes = pendingUploadBytes
        if (bytes == null) {
            Toast.makeText(this, R.string.appearance_need_upload, Toast.LENGTH_SHORT).show()
            return
        }
        setAppearanceGenerating(true, R.string.appearance_generating)
        appearanceExecutor.execute {
            val outcome = runCatching { PetAppearanceGenerator.applyUploadDirect(bytes) }
            runOnUiThread { handleAppearanceResult(outcome) }
        }
    }

    private fun handleAppearanceResult(
        outcome: Result<PetAppearanceGenerator.Result>,
        requestedMode: PetAppearanceGenerator.GenerationMode = PetAppearanceGenerator.GenerationMode.FAST
    ) {
        if (isFinishing || isDestroyed) return
        setAppearanceGenerating(false)
        outcome.onSuccess { result ->
            val label = when {
                result.refinedPrompt.isNotBlank() && result.refinedPrompt != result.prompt ->
                    result.refinedPrompt.take(80)
                else -> result.prompt
            }
            assets.installGeneratedAppearance(result.pngBytes, label)
            binding.petPreview.show(assets.fileFor(PetState.IDLE))
            sendServiceAction(PetService.ACTION_REFRESH)
            binding.companionMessage.setText(R.string.hero_message_appearance)
            updateAppearanceStatus()
            if (result.refinedPrompt.isNotBlank()) {
                binding.appearanceStatus.text = getString(
                    R.string.appearance_status_traits,
                    result.refinedPrompt.take(120)
                )
            }
            Toast.makeText(this, successMessageFor(result, requestedMode), Toast.LENGTH_SHORT).show()
        }.onFailure {
            val detail = it.localizedMessage ?: it.javaClass.simpleName
            binding.appearanceStatus.text = getString(R.string.appearance_failed, detail)
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.appearance_failed_title)
                .setMessage(detail)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun updateAgentProgress(stage: String) {
        if (isFinishing || isDestroyed || !generatingAppearance) return
        val text = when {
            stage == "understanding" -> getString(R.string.appearance_progress_understanding)
            stage == "looking" -> getString(R.string.appearance_progress_looking)
            stage == "fallback" -> getString(R.string.appearance_progress_fallback)
            stage == "planned_local" -> getString(R.string.appearance_progress_planned)
            stage == "local" -> getString(R.string.appearance_progress_local)
            stage == "done" -> getString(R.string.appearance_progress_done)
            stage.startsWith("traits:") ->
                getString(R.string.appearance_progress_traits, stage.removePrefix("traits:"))
            stage.startsWith("drawing:") -> {
                val n = stage.substringAfter(':').toIntOrNull() ?: 1
                getString(R.string.appearance_progress_drawing, n)
            }
            stage.startsWith("checking:") -> getString(R.string.appearance_progress_checking)
            stage.startsWith("revising:") -> getString(R.string.appearance_progress_revising)
            else -> getString(R.string.appearance_generating)
        }
        binding.appearanceStatus.text = text
    }

    private fun successMessageFor(
        result: PetAppearanceGenerator.Result,
        requestedMode: PetAppearanceGenerator.GenerationMode
    ): String = when (result.source) {
        PetAppearanceGenerator.Source.QWEN -> getString(R.string.appearance_success_qwen)
        PetAppearanceGenerator.Source.CHATGPT ->
            getString(R.string.appearance_success_chatgpt, result.attempts, result.score)
        PetAppearanceGenerator.Source.ONLINE -> getString(R.string.appearance_success_online)
        PetAppearanceGenerator.Source.UPLOAD -> getString(R.string.appearance_success_upload)
        PetAppearanceGenerator.Source.PLANNED ->
            getString(R.string.appearance_success_planned)
        PetAppearanceGenerator.Source.LOCAL -> {
            if (requestedMode == PetAppearanceGenerator.GenerationMode.QUALITY) {
                getString(R.string.appearance_success_local_fallback)
            } else {
                getString(R.string.appearance_success_local)
            }
        }
    }

    private fun onImagePicked(uri: Uri) {
        appearanceExecutor.execute {
            val result = runCatching {
                val mime = contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().also { require(it.size <= MAX_UPLOAD_BYTES) { "图片不能超过 20 MB" } }
                } ?: error("无法读取图片")
                require(bytes.isNotEmpty()) { "图片为空" }
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: error("无法解析图片")
                Triple(bytes, if (mime.startsWith("image/")) mime else "image/jpeg", bitmap)
            }
            runOnUiThread {
                result.onSuccess { (bytes, mime, bitmap) ->
                    pendingUploadBytes = bytes
                    pendingUploadMime = mime
                    binding.appearanceUploadPreview.setImageBitmap(bitmap)
                    binding.appearanceUploadPreview.visibility = View.VISIBLE
                    binding.generateFromUploadButton.isEnabled = true
                    binding.applyUploadButton.isEnabled = true
                    binding.appearanceStatus.text = getString(R.string.appearance_status_idle) + " · 已选择参考图"
                }.onFailure {
                    Toast.makeText(this, getString(R.string.appearance_failed, it.localizedMessage ?: it.javaClass.simpleName), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun persistApiKeysFromInput() {
        val dashTyped = binding.dashScopeApiKeyInput.text?.toString().orEmpty().trim()
        if (dashTyped.isNotEmpty()) {
            settings.dashScopeApiKey = dashTyped
        }
        val deepSeekTyped = binding.deepSeekApiKeyInput.text?.toString().orEmpty().trim()
        if (deepSeekTyped.isNotEmpty()) {
            settings.deepSeekApiKey = deepSeekTyped
        }
        val openAiTyped = binding.openAiApiKeyInput.text?.toString().orEmpty().trim()
        if (openAiTyped.isNotEmpty()) {
            settings.openAiApiKey = openAiTyped
        }
        val baseTyped = binding.openAiBaseUrlInput.text?.toString().orEmpty().trim()
        if (baseTyped.isNotEmpty()) {
            settings.openAiBaseUrl = baseTyped
        }
    }

    private fun currentPromptGuide(): PetAppearanceAgent.PromptGuide {
        var subject = binding.appearanceSubjectSpinner.selectedItem?.toString().orEmpty()
        var style = binding.appearanceStyleSpinner.selectedItem?.toString().orEmpty()
        if (subject.contains("自定义")) {
            subject = binding.appearanceCustomSubjectInput.text?.toString()?.trim().orEmpty()
                .ifBlank { subject }
        }
        if (style.contains("自定义")) {
            style = binding.appearanceCustomStyleInput.text?.toString()?.trim().orEmpty()
                .ifBlank { style }
        }
        val features = binding.appearanceFeaturesInput.text?.toString().orEmpty()
        val free = binding.appearanceIdeaInput.text?.toString().orEmpty()
        return PetAppearanceAgent.PromptGuide(
            subjectType = subject,
            artStyle = style,
            coreFeatures = features,
            freeIdea = free
        )
    }

    private fun setupAppearanceCustomFields() {
        fun refresh() {
            val subjectCustom = binding.appearanceSubjectSpinner.selectedItem?.toString().orEmpty().contains("自定义")
            val styleCustom = binding.appearanceStyleSpinner.selectedItem?.toString().orEmpty().contains("自定义")
            binding.appearanceCustomSubjectInput.visibility = if (subjectCustom) View.VISIBLE else View.GONE
            binding.appearanceCustomStyleInput.visibility = if (styleCustom) View.VISIBLE else View.GONE
        }
        binding.appearanceSubjectSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = refresh()
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        binding.appearanceStyleSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = refresh()
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        refresh()
    }

    private fun restoreDefaultAppearance() {
        assets.clearCustomAppearance()
        binding.petPreview.show(assets.fileFor(PetState.IDLE))
        sendServiceAction(PetService.ACTION_REFRESH)
        updateAppearanceStatus()
        Toast.makeText(this, R.string.appearance_restored, Toast.LENGTH_SHORT).show()
    }

    private fun setAppearanceGenerating(active: Boolean, statusRes: Int = R.string.appearance_generating) {
        generatingAppearance = active
        binding.generateAppearanceButton.isEnabled = !active
        binding.restoreAppearanceButton.isEnabled = !active
        binding.appearanceIdeaInput.isEnabled = !active
        binding.appearanceFeaturesInput.isEnabled = !active
        binding.appearanceSubjectSpinner.isEnabled = !active
        binding.appearanceStyleSpinner.isEnabled = !active
        binding.appearanceCustomSubjectInput.isEnabled = !active
        binding.appearanceCustomStyleInput.isEnabled = !active
        binding.appearanceModeFast.isEnabled = !active
        binding.appearanceModeQuality.isEnabled = !active
        binding.pickAppearanceImageButton.isEnabled = !active
        binding.generateFromUploadButton.isEnabled = !active && pendingUploadBytes != null
        binding.applyUploadButton.isEnabled = !active && pendingUploadBytes != null
        binding.dashScopeApiKeyInput.isEnabled = !active
        binding.deepSeekApiKeyInput.isEnabled = !active
        binding.openAiApiKeyInput.isEnabled = !active
        binding.openAiBaseUrlInput.isEnabled = !active
        if (active) {
            binding.appearanceStatus.setText(statusRes)
        } else {
            updateAppearanceStatus()
        }
    }

    private fun setupAppearanceMode() {
        when (settings.appearanceMode) {
            PetAppearanceGenerator.GenerationMode.QUALITY ->
                binding.appearanceModeQuality.isChecked = true
            PetAppearanceGenerator.GenerationMode.FAST ->
                binding.appearanceModeFast.isChecked = true
        }
        binding.appearanceModeGroup.setOnCheckedChangeListener { _, checkedId ->
            settings.appearanceMode = when (checkedId) {
                R.id.appearanceModeQuality -> PetAppearanceGenerator.GenerationMode.QUALITY
                else -> PetAppearanceGenerator.GenerationMode.FAST
            }
        }
    }

    private fun persistAppearanceMode() {
        settings.appearanceMode = when (binding.appearanceModeGroup.checkedRadioButtonId) {
            R.id.appearanceModeQuality -> PetAppearanceGenerator.GenerationMode.QUALITY
            else -> PetAppearanceGenerator.GenerationMode.FAST
        }
    }

    private fun updateAppearanceStatus() {
        val idea = assets.customIdea()
        binding.appearanceStatus.text = if (assets.hasCustomAppearance() && !idea.isNullOrBlank()) {
            getString(R.string.appearance_status_custom, idea)
        } else {
            getString(R.string.appearance_status_idle)
        }
        binding.restoreAppearanceButton.isEnabled = assets.hasCustomAppearance() && !generatingAppearance
        binding.generateFromUploadButton.isEnabled = pendingUploadBytes != null && !generatingAppearance
        binding.applyUploadButton.isEnabled = pendingUploadBytes != null && !generatingAppearance
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
        appearanceExecutor.shutdownNow()
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
        private const val MAX_UPLOAD_BYTES = 20 * 1024 * 1024
        private const val KEY_SETTINGS_EXPANDED = "settings_expanded"
        private const val SERVICE_START_POLL_INTERVAL_MS = 500L
        private const val MAX_SERVICE_START_ATTEMPTS = 16
        private const val WALK_SPEED_MIN = 10
        private const val WALK_SPEED_MAX = 500
    }
}
