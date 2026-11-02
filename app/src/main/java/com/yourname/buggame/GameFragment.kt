package com.yourname.buggame

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yourname.buggame.data.AppDatabase
import com.yourname.buggame.data.GameResult
import kotlinx.coroutines.launch

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
        if (settings.currentPlayerId <= 0L) {
            Toast.makeText(
                requireContext(),
                "Сначала зарегистрируйте или выберите игрока (вкладка «Регистрация»)",
                Toast.LENGTH_LONG
            ).show()
            return
        }
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

        saveResult(score, hits, misses)
    }

    // Сохраняем результат раунда в базу данных
    private fun saveResult(score: Int, hits: Int, misses: Int) {
        val playerId = GameSettings(requireContext()).currentPlayerId
        if (playerId <= 0L) return
        val db = AppDatabase.get(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            db.resultDao().insert(
                GameResult(
                    playerId = playerId,
                    score = score,
                    hits = hits,
                    misses = misses,
                    playedAt = System.currentTimeMillis()
                )
            )
            Toast.makeText(requireContext(), "Результат сохранён", Toast.LENGTH_SHORT).show()
        }
    }

    // Ушли с вкладки — раунд прерывается
    override fun onPause() {
        super.onPause()
        gameView.stop()
        btnStart.isEnabled = true
    }
}