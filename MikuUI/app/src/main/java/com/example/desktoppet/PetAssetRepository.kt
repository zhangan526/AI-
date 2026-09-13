package com.example.desktoppet

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

class PetAssetRepository(private val context: Context) {
    private val assetDir = File(context.filesDir, "pet_assets").apply { mkdirs() }
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    init {
        importBundledCategories()
        // The bundled starter images are copied into the same private directory on first run.
        if (preferences.getString(PetState.IDLE.key, null) == null) {
            restoreBundledDefaults()
        }
    }

    fun fileFor(state: PetState): File? {
        if (state == PetState.IDLE) customAppearanceFile()?.let { return it }
        val selected = preferences.getString(state.key, null)
        val direct = selected?.let { File(assetDir, it) }?.takeIf { it.isFile }
        if (direct != null) return direct
        if (state != PetState.IDLE) return fileFor(PetState.IDLE)
        return null
    }

    fun randomFileFor(state: PetState): File? {
        if (state == PetState.IDLE) customAppearanceFile()?.let { return it }
        val category = categoryFor(state)
        val files = categoryFiles(category)
        if (files.isNotEmpty()) return files.random(Random)
        return fileFor(state)
    }

    fun hasCustomAppearance(): Boolean =
        preferences.getBoolean(KEY_HAS_CUSTOM, false) && customAppearanceFile() != null

    fun customIdea(): String? = preferences.getString(KEY_CUSTOM_IDEA, null)

    fun installGeneratedAppearance(pngBytes: ByteArray, idea: String): File {
        val target = File(assetDir, CUSTOM_FILE_NAME)
        val temp = File(assetDir, "$CUSTOM_FILE_NAME.tmp")
        FileOutputStream(temp).use { output ->
            output.write(pngBytes)
            output.fd.sync()
        }
        require(temp.renameTo(target)) { "无法保存自定义外观" }
        preferences.edit()
            .putBoolean(KEY_HAS_CUSTOM, true)
            .putString(KEY_CUSTOM_IDEA, idea.trim())
            .apply()
        return target
    }

    fun clearCustomAppearance() {
        File(assetDir, CUSTOM_FILE_NAME).delete()
        preferences.edit()
            .putBoolean(KEY_HAS_CUSTOM, false)
            .remove(KEY_CUSTOM_IDEA)
            .apply()
        restoreBundledDefaults()
    }

    private fun customAppearanceFile(): File? {
        if (!preferences.getBoolean(KEY_HAS_CUSTOM, false)) return null
        return File(assetDir, CUSTOM_FILE_NAME).takeIf { it.isFile }
    }

    private fun restoreBundledDefaults() {
        setBundled(PetState.IDLE, R.drawable.pet_01)
        setBundled(PetState.TOUCH, R.drawable.pet_02)
        setBundled(PetState.HAPPY, R.drawable.pet_03)
        setBundled(PetState.FEED, R.drawable.pet_04)
        setBundled(PetState.SAD, R.drawable.pet_05)
        setBundled(PetState.SLEEP, R.drawable.pet_06)
    }

    private fun importBundledCategories() {
        val previousVersion = preferences.getInt(KEY_BUNDLED_VERSION, 0)
        if (previousVersion >= BUNDLED_VERSION) return
        if (previousVersion < 3) {
            // 17.jpg was intentionally reclassified from sleep to sad.
            File(assetDir, "sleep/17.jpg").delete()
        }
        val categories = context.assets.list("pet_assets") ?: emptyArray()
        categories.forEach { category ->
            val targetDir = File(assetDir, category).apply { mkdirs() }
            (context.assets.list("pet_assets/$category") ?: emptyArray()).forEach { name ->
                val target = File(targetDir, name)
                if (!target.isFile) {
                    context.assets.open("pet_assets/$category/$name").use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    }
                }
            }
        }
        preferences.edit().putInt(KEY_BUNDLED_VERSION, BUNDLED_VERSION).apply()
    }

    private fun categoryFiles(category: String): List<File> =
        File(assetDir, category).listFiles()?.filter { it.isFile } ?: emptyList()

    private fun categoryFor(state: PetState): String = when (state) {
        PetState.IDLE -> "random"
        PetState.TOUCH -> "click"
        PetState.FEED -> "eat"
        PetState.PET -> "happy"
        PetState.WALK -> "walk"
        PetState.SLEEP -> "sleep"
        PetState.HAPPY -> "happy"
        PetState.SAD -> "sad"
        PetState.AIR -> "play"
        PetState.HIDDEN -> "bored"
        PetState.PLAY -> "play"
        PetState.RANDOM -> "random"
        PetState.BORED -> "bored"
    }

    private fun setBundled(state: PetState, resourceId: Int) {
        val target = File(assetDir, "${state.key}_default.png")
        context.resources.openRawResource(resourceId).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        preferences.edit().putString(state.key, target.name).apply()
    }

    companion object {
        private const val PREFERENCES = "pet_assets"
        private const val KEY_BUNDLED_VERSION = "bundled_categories_version"
        private const val KEY_HAS_CUSTOM = "has_custom_appearance"
        private const val KEY_CUSTOM_IDEA = "custom_appearance_idea"
        private const val CUSTOM_FILE_NAME = "custom_generated.png"
        private const val BUNDLED_VERSION = 3
    }
}
