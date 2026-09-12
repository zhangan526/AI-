package com.example.desktoppet

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.desktoppet.databinding.ActivityPetPanelBinding

class PetPanelActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPetPanelBinding
    private lateinit var settings: PetSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPetPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = PetSettings(this)

        window.setDimAmount(0f)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        loadTodos()
        binding.freeWalkSwitch.isChecked = settings.edgeWalkEnabled
        binding.freeWalkSwitch.setOnCheckedChangeListener { _, enabled ->
            settings.edgeWalkEnabled = enabled
            if (PetService.isRunning) {
                startService(
                    Intent(this, PetService::class.java)
                        .setAction(PetService.ACTION_REFRESH_SETTINGS)
                )
            }
        }
        binding.addTodoButton.setOnClickListener { addTodoRow("") }
        binding.focusButton.setOnClickListener { FocusLauncher.openTimer(this) }
        binding.closePanelButton.setOnClickListener { finish() }
        binding.stopPetButton.setOnClickListener {
            stopService(Intent(this, PetService::class.java))
            finish()
        }
        updateTodoCount()
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        val maxHeight = (320 * metrics.density).toInt()
        val minHeight = (220 * metrics.density).toInt()
        val panelHeight = (metrics.heightPixels * 0.34f).toInt().coerceIn(minHeight, maxHeight)
        window.setLayout((metrics.widthPixels * 0.92f).toInt(), panelHeight)
    }

    override fun onPause() {
        saveTodos()
        super.onPause()
    }

    private fun loadTodos() {
        val preferences = getSharedPreferences(TODO_PREFERENCES, MODE_PRIVATE)
        repeat(preferences.getInt(KEY_COUNT, 0)) { index ->
            addTodoRow(preferences.getString("todo_$index", "").orEmpty())
        }
    }

    private fun addTodoRow(initialText: String) {
        val row = layoutInflater.inflate(R.layout.item_todo, binding.todoContainer, false)
        val input = row.findViewById<EditText>(R.id.todoInput)
        val doneButton = row.findViewById<android.view.View>(R.id.todoDone)
        val deleteButton = row.findViewById<android.view.View>(R.id.todoDelete)
        val actions = row.findViewById<LinearLayout>(R.id.todoActions)
        input.setText(initialText)

        row.setOnClickListener { actions.visibility = android.view.View.VISIBLE }
        input.setOnFocusChangeListener { _, focused -> if (focused) actions.visibility = android.view.View.VISIBLE }

        doneButton.setOnClickListener {
            settings.foodCount += 1
            settings.mood += TODO_MOOD_REWARD
            binding.todoContainer.removeView(row)
            saveTodos()
            updateTodoCount()
            Toast.makeText(this, R.string.todo_reward, Toast.LENGTH_SHORT).show()
        }
        deleteButton.setOnClickListener {
            binding.todoContainer.removeView(row)
            saveTodos()
            updateTodoCount()
        }
        binding.todoContainer.addView(row)
        updateTodoCount()
    }

    private fun saveTodos() {
        val editor = getSharedPreferences(TODO_PREFERENCES, MODE_PRIVATE).edit().clear()
        val todoTexts = buildList {
            for (index in 0 until binding.todoContainer.childCount) {
                val input = binding.todoContainer.getChildAt(index)
                    .findViewById<EditText>(R.id.todoInput)
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) add(text)
            }
        }
        editor.putInt(KEY_COUNT, todoTexts.size)
        todoTexts.forEachIndexed { index, text -> editor.putString("todo_$index", text) }
        editor.apply()
    }

    private fun updateTodoCount() {
        binding.todoCount.text = binding.todoContainer.childCount.toString()
    }

    companion object {
        private const val TODO_PREFERENCES = "todos"
        private const val KEY_COUNT = "count"
        private const val TODO_MOOD_REWARD = 8
    }
}
