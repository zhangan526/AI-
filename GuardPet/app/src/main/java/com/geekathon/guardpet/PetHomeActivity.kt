package com.geekathon.guardpet

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.geekathon.guardpet.databinding.ActivityPetHomeBinding

class PetHomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPetHomeBinding
    private lateinit var settings: PetSettings
    private lateinit var assets: PetAssetRepository
    private val homePrefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private var interactions: Int
        get() = homePrefs.getInt(KEY_INTERACTIONS, 0)
        set(value) = homePrefs.edit().putInt(KEY_INTERACTIONS, value).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPetHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = PetSettings(this)
        assets = PetAssetRepository(this)
        binding.homePet.show(assets.randomFileFor(PetState.HAPPY))
        animateEntrance()
        binding.gameCard.setOnClickListener {
            startActivity(Intent(this, GameMenuActivity::class.java))
        }
        binding.careCard.setOnClickListener { reveal(binding.carePanel) }
        binding.touchCard.setOnClickListener { touch() }
        binding.feedHomeButton.setOnClickListener { feed() }
        binding.bathButton.setOnClickListener { bath() }
        binding.touchHomeButton.setOnClickListener { touch() }
        renderStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) renderStatus()
    }

    private fun feed() {
        if (settings.foodCount <= 0) {
            Toast.makeText(this, R.string.no_food, Toast.LENGTH_SHORT).show()
            return
        }
        settings.foodCount -= 1
        settings.hunger += 20
        settings.mood += 3
        interactions++
        binding.homeMessage.text = getString(R.string.pet_home_fed)
        bounce(binding.homePet)
        sendPetState(PetState.FEED)
        renderStatus()
    }

    private fun bath() {
        homePrefs.edit().putInt(KEY_HYGIENE, 100).apply()
        settings.mood += 5
        interactions++
        binding.homeMessage.text = getString(R.string.pet_home_bathed)
        bounce(binding.homePet)
        sendPetState(PetState.HAPPY)
        renderStatus()
    }

    private fun touch() {
        interactions++
        settings.mood += 2
        binding.homeMessage.text = touchLines.random()
        bounce(binding.homePet)
        sendPetState(PetState.PET)
        renderStatus()
    }

    private fun renderStatus() {
        binding.homeMood.text = getString(R.string.mood_value, settings.mood)
        binding.homeHunger.text = getString(R.string.hunger_value, settings.hunger)
        binding.homeHygiene.text = getString(R.string.pet_home_hygiene, homePrefs.getInt(KEY_HYGIENE, 80))
        val games = homePrefs.getInt(KEY_GAMES, 0)
        binding.homeSummary.text = getString(
            if (settings.mood >= 70) R.string.pet_home_summary_happy else R.string.pet_home_summary_need,
            interactions,
            games
        )
    }

    private fun sendPetState(state: PetState) {
        if (PetService.isRunning) {
            startService(
                Intent(this, PetService::class.java)
                    .setAction(PetService.ACTION_SET_STATE)
                    .putExtra(PetService.EXTRA_STATE, state.key)
            )
        }
    }

    private fun reveal(view: View) {
        view.visibility = if (view.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (view.visibility == View.VISIBLE) {
            view.alpha = 0f
            view.translationY = 18f
            view.animate().alpha(1f).translationY(0f).setDuration(260).start()
        }
    }

    private fun bounce(view: View) {
        view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(130).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
        }.start()
    }

    private fun animateEntrance() {
        listOf(binding.heroCard, binding.gameCard, binding.careCard).forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 24f
            view.animate().alpha(1f).translationY(0f).setStartDelay(index * 70L).setDuration(360).start()
        }
    }

    companion object {
        const val PREFS = "pet_home"
        const val KEY_INTERACTIONS = "interactions"
        const val KEY_HYGIENE = "hygiene"
        const val KEY_GAMES = "games_played"
        const val KEY_BEST = "best_game_score"
        private val touchLines = listOf(
            "摸摸收到啦，守伴今天也会陪着你。",
            "再摸一下也可以，我喜欢这样被记住。",
            "嘿嘿，今天的陪伴进度又增加了！"
        )
    }
}
