package com.geekathon.guardpet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.geekathon.guardpet.databinding.ActivityBigBangBinding

class BigBangActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBigBangBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBigBangBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindTokens()
        binding.closeButton.setOnClickListener { finish() }
        binding.root.setOnClickListener { finish() }
        binding.sheet.setOnClickListener { /* keep taps on the sheet from closing */ }
        binding.invertButton.setOnClickListener { binding.tokenFlow.invertSelection() }
        binding.copyButton.setOnClickListener {
            val selected = binding.tokenFlow.selectedText()
            if (selected.isBlank()) {
                Toast.makeText(this, R.string.bigbang_select_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("bigbang", selected))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
        binding.searchButton.setOnClickListener {
            val selected = binding.tokenFlow.selectedText()
            if (selected.isBlank()) {
                Toast.makeText(this, R.string.bigbang_select_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", selected))
        }
        binding.flashNoteButton.setOnClickListener {
            val selected = binding.tokenFlow.selectedText()
            if (selected.isBlank()) {
                Toast.makeText(this, R.string.bigbang_select_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(
                Intent(this, FlashNoteActivity::class.java)
                    .putExtra(FlashNoteActivity.EXTRA_PREFILL, selected)
                    .putExtra(FlashNoteActivity.EXTRA_SOURCE, "bigbang")
            )
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bindTokens()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (PetService.isRunning) {
            startService(Intent(this, PetService::class.java).setAction(PetService.ACTION_SHOW_PET))
        }
    }

    private fun bindTokens() {
        val tokens = TextCaptureHolder.tokens
        if (tokens.isEmpty()) {
            Toast.makeText(this, R.string.bigbang_empty, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        binding.tokenFlow.setTokens(tokens)
    }
}
