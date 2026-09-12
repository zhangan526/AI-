package com.geekathon.guardpet

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.geekathon.guardpet.databinding.ActivityGameBinding

class GameActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGameBinding
    private lateinit var settings: PetSettings
    private val rewards by lazy { getSharedPreferences(REWARDS, MODE_PRIVATE) }
    private val companionStats by lazy { getSharedPreferences(PetHomeActivity.PREFS, MODE_PRIVATE) }
    private var mode = GameSurface.Mode.RUNNER
    private var rewardClaimed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = PetSettings(this)
        mode = intent.getStringExtra(EXTRA_MODE)
            ?.let { runCatching { GameSurface.Mode.valueOf(it) }.getOrNull() }
            ?: GameSurface.Mode.RUNNER
        binding.gamePet.visibility = View.GONE
        binding.backButton.setOnClickListener { claimReward(); finish() }
        binding.restartButton.setOnClickListener {
            claimReward()
            rewardClaimed = false
            binding.rewardText.text = getString(R.string.game_reward_playing)
            binding.gameSurface.start(mode)
        }
        binding.claimButton.setOnClickListener { claimReward() }
        binding.tetrisLeft.setOnClickListener { binding.gameSurface.moveTetris(-1) }
        binding.tetrisRight.setOnClickListener { binding.gameSurface.moveTetris(1) }
        binding.tetrisRotate.setOnClickListener { binding.gameSurface.rotateTetris() }
        binding.tetrisDrop.setOnClickListener { binding.gameSurface.hardDropTetris() }
        // Keep a click action for keyboard/accessibility activation. Touches are
        // consumed below so a release cannot synthesize a second click and leave
        // the player crouching forever.
        binding.runnerCrouch.setOnClickListener { binding.gameSurface.runnerCrouch() }
        binding.runnerCrouch.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> binding.gameSurface.setRunnerCrouching(true)
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> binding.gameSurface.setRunnerCrouching(false)
            }
            true
        }
        binding.gameSurface.onScore = { score ->
            binding.scoreText.text = getString(R.string.game_score, score)
            val high = maxOf(score, rewards.getInt(highKey(mode), 0))
            binding.highScoreText.text = getString(R.string.game_high_score, high)
        }
        binding.gameSurface.onGameOver = { score ->
            binding.rewardText.text = if (score > 0) {
                getString(R.string.game_reward_ready, score)
            } else {
                getString(R.string.game_reward_none)
            }
        }
        switchTo(mode)
    }

    override fun onResume() {
        super.onResume()
        binding.gameSurface.start(mode)
    }

    override fun onPause() {
        binding.gameSurface.stopGame()
        super.onPause()
    }

    private fun switchTo(next: GameSurface.Mode) {
        mode = next
        rewardClaimed = false
        binding.gameSurface.start(next)
        binding.gameTitle.text = getString(
            when (next) {
                GameSurface.Mode.RUNNER -> R.string.game_runner
                GameSurface.Mode.TETRIS -> R.string.game_tetris
            }
        )
        binding.gameHint.text = getString(
            when (next) {
                GameSurface.Mode.RUNNER -> R.string.game_runner_hint
                GameSurface.Mode.TETRIS -> R.string.game_tetris_hint
            }
        )
        binding.gamePet.visibility = View.GONE
        binding.tetrisButtons.visibility = if (next == GameSurface.Mode.TETRIS) View.VISIBLE else View.GONE
        binding.runnerCrouch.visibility = if (next == GameSurface.Mode.RUNNER) View.VISIBLE else View.GONE
        binding.rewardText.text = getString(R.string.game_reward_playing)
        binding.scoreText.text = getString(R.string.game_score, 0)
        binding.highScoreText.text = getString(R.string.game_high_score, rewards.getInt(highKey(next), 0))
    }

    private fun claimReward() {
        if (rewardClaimed) return
        val score = binding.gameSurface.currentScore()
        if (score <= 0) {
            binding.rewardText.text = getString(R.string.game_reward_none)
            return
        }
        val food = (1 + score / 8).coerceAtMost(20)
        settings.foodCount += food
        settings.mood += (1 + score / 12).coerceAtMost(10)
        companionStats.edit()
            .putInt(PetHomeActivity.KEY_GAMES, companionStats.getInt(PetHomeActivity.KEY_GAMES, 0) + 1)
            .putInt(PetHomeActivity.KEY_BEST, maxOf(score, companionStats.getInt(PetHomeActivity.KEY_BEST, 0)))
            .apply()
        if (PetService.isRunning) {
            startService(
                Intent(this, PetService::class.java)
                    .setAction(PetService.ACTION_SET_STATE)
                    .putExtra(PetService.EXTRA_STATE, PetState.PLAY.key)
            )
        }
        val high = maxOf(score, rewards.getInt(highKey(mode), 0))
        rewards.edit().putInt(highKey(mode), high).apply()
        rewardClaimed = true
        binding.rewardText.text = getString(R.string.game_reward_claimed, food)
    }

    companion object {
        const val EXTRA_MODE = "game_mode"
        const val REWARDS = "game_rewards"
        fun highKey(value: GameSurface.Mode) = "high_${value.name.lowercase()}"
    }
}
