package com.geekathon.guardpet

import android.os.Bundle
import android.text.InputType
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.geekathon.guardpet.databinding.ActivityAiAgentBinding
import kotlinx.coroutines.launch

class AiAgentActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAiAgentBinding
    private lateinit var agent: DeepSeekAgent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiAgentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        agent = DeepSeekAgent(this)
        binding.apiKeyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        binding.apiKeyInput.setText(ApiKeyStore.read(this).orEmpty())
        binding.saveKeyButton.setOnClickListener {
            ApiKeyStore.save(this, binding.apiKeyInput.text.toString())
            Toast.makeText(this, R.string.ai_key_saved, Toast.LENGTH_SHORT).show()
        }
        binding.sendButton.setOnClickListener { sendMessage() }
    }

    private fun sendMessage() {
        val input = binding.messageInput.text.toString().trim()
        if (input.isEmpty()) return
        appendMessage("我", input)
        binding.messageInput.text?.clear()
        binding.sendButton.isEnabled = false
        binding.statusText.setText(R.string.ai_thinking)
        lifecycleScope.launch {
            try {
                val result = agent.ask(input)
                val execution = result.action?.let(::executeToolAction)
                appendMessage("守伴", listOfNotNull(result.text, execution).joinToString("\n"))
                binding.statusText.setText(R.string.ai_ready)
            } catch (error: Exception) {
                binding.statusText.text = error.message ?: getString(R.string.ai_failed)
            } finally {
                binding.sendButton.isEnabled = true
            }
        }
    }

    private fun executeToolAction(action: AgentToolAction): String {
        return when (action) {
            AgentToolAction.Pet -> if (sendPetState(PetState.TOUCH)) "已摸摸守伴" else "桌宠未开启，摸摸没有执行"
            AgentToolAction.Feed -> feedPet()
            AgentToolAction.Sleep -> if (sendPetState(PetState.SLEEP)) "已让守伴睡觉" else "桌宠未开启，睡觉没有执行"
            is AgentToolAction.FlashNote -> {
                val date = if (action.category == FlashNoteCategory.SCHEDULE) {
                    action.date ?: java.time.LocalDate.now().toString()
                } else null
                FlashNoteStore.insert(FlashNote(
                    text = action.text,
                    category = action.category,
                    source = "agent",
                    scheduleDate = date
                ))
                Toast.makeText(this, R.string.ai_note_saved, Toast.LENGTH_SHORT).show()
                "已保存到闪记"
            }
        }
    }

    private fun feedPet(): String {
        if (!PetService.isRunning) {
            Toast.makeText(this, R.string.start_pet_first, Toast.LENGTH_SHORT).show()
            return "桌宠未开启，喂食没有执行"
        }
        val settings = PetSettings(this)
        if (settings.foodCount <= 0) {
            Toast.makeText(this, R.string.no_food, Toast.LENGTH_SHORT).show()
            return "没有食物，喂食没有执行"
        }
        settings.foodCount -= 1
        settings.hunger += 20
        settings.mood += 3
        sendPetState(PetState.FEED)
        return "已喂食守伴"
    }

    private fun sendPetState(state: PetState): Boolean {
        if (!PetService.isRunning) {
            Toast.makeText(this, R.string.start_pet_first, Toast.LENGTH_SHORT).show()
            return false
        }
        startService(android.content.Intent(this, PetService::class.java)
            .setAction(PetService.ACTION_SET_STATE).putExtra(PetService.EXTRA_STATE, state.key))
        return true
    }

    private fun appendMessage(who: String, message: String) {
        binding.chatContainer.addView(TextView(this).apply {
            text = "$who：$message"
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            setPadding(0, 8, 0, 8)
        })
    }
}
