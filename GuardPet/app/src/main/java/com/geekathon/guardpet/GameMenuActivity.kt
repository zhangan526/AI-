package com.geekathon.guardpet

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.geekathon.guardpet.databinding.ActivityGameMenuBinding

class GameMenuActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGameMenuBinding
    private lateinit var assets: PetAssetRepository
    private val records by lazy { getSharedPreferences(GameActivity.REWARDS, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        assets = PetAssetRepository(this)
        binding.menuPet.show(assets.randomFileFor(PetState.HAPPY))
        binding.backButton.setOnClickListener { finish() }
        bindCard(binding.runnerCard, GameSurface.Mode.RUNNER)
        bindCard(binding.tetrisCard, GameSurface.Mode.TETRIS)
        renderScores()
    }

    private fun bindCard(card: View, mode: GameSurface.Mode) {
        card.setOnClickListener {
            startActivity(Intent(this, GameActivity::class.java).putExtra(GameActivity.EXTRA_MODE, mode.name))
        }
    }

    private fun renderScores() {
        binding.runnerHigh.text = getString(R.string.game_menu_high, records.getInt(GameActivity.highKey(GameSurface.Mode.RUNNER), 0))
        binding.tetrisHigh.text = getString(R.string.game_menu_high, records.getInt(GameActivity.highKey(GameSurface.Mode.TETRIS), 0))
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) renderScores()
    }
}
