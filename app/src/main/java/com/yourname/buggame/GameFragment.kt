package com.yourname.buggame

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

class GameFragment : Fragment(R.layout.fragment_game), GameView.Listener {

    private lateinit var tvScore: TextView
    private lateinit var tvTime: TextView
    private lateinit var btnStart: Button
    private lateinit var gameView: GameView

    private lateinit var resultCard: View
    private lateinit var tvResultScore: TextView
    private lateinit var tvResultHits: TextView
    private lateinit var tvResultMisses: TextView
    private lateinit var tvResultAccuracy: TextView
    private lateinit var btnReplay: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvScore = view.findViewById(R.id.tvScore)
        tvTime = view.findViewById(R.id.tvTime)
        btnStart = view.findViewById(R.id.btnStart)
        gameView = view.findViewById(R.id.gameView)

        resultCard = view.findViewById(R.id.resultCard)
        tvResultScore = view.findViewById(R.id.tvResultScore)
        tvResultHits = view.findViewById(R.id.tvResultHits)
        tvResultMisses = view.findViewById(R.id.tvResultMisses)
        tvResultAccuracy = view.findViewById(R.id.tvResultAccuracy)
        btnReplay = view.findViewById(R.id.btnReplay)

        gameView.listener = this
        btnStart.setOnClickListener { startGame() }
        btnReplay.setOnClickListener { startGame() }
    }

    private fun startGame() {
        val settings = GameSettings(requireContext())
        resultCard.visibility = View.GONE
        btnStart.isEnabled = false
        gameView.start(settings.speed, settings.maxBugs, settings.roundSeconds)
    }

    override fun onScoreChanged(score: Int) {
        tvScore.text = "Очки: $score"
    }

    override fun onTimeChanged(secondsLeft: Int) {
        tvTime.text = "Время: $secondsLeft"
    }

    override fun onGameOver(score: Int, hits: Int, misses: Int) {
        val total = hits + misses
        val accuracy = if (total > 0) hits * 100 / total else 0

        tvResultScore.text = "Очки: $score"
        tvResultHits.text = "Попадания: $hits"
        tvResultMisses.text = "Промахи: $misses"
        tvResultAccuracy.text = "Точность: $accuracy%"

        resultCard.visibility = View.VISIBLE
        btnStart.isEnabled = true
    }

    // Ушли с вкладки — раунд прерывается
    override fun onPause() {
        super.onPause()
        gameView.stop()
        btnStart.isEnabled = true
    }
}