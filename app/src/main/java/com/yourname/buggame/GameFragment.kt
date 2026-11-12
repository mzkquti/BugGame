package com.yourname.buggame

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yourname.buggame.data.AppDatabase
import com.yourname.buggame.data.GameResult
import com.yourname.buggame.gold.GoldRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

class GameFragment : Fragment(R.layout.fragment_game), GameView.Listener, SensorEventListener {

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
    private lateinit var tvBonus: TextView
    private lateinit var goldRepo: GoldRepository

    // акселерометр: наклон телефона влияет на движение жуков, когда нажат бонус
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    // звук «крика жуков» при сборе бонуса
    private var soundPool: SoundPool? = null
    private var screamId = 0
    private var soundLoaded = false

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
        tvBonus = view.findViewById(R.id.tvBonus)
        goldRepo = GoldRepository(requireContext())

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val pool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        pool.setOnLoadCompleteListener { _, _, status -> soundLoaded = status == 0 }
        screamId = pool.load(requireContext(), R.raw.bug_scream, 1)
        soundPool = pool

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
        // очки за золотого таракана пропорциональны курсу золота (1 очко за каждые 100 ₽/г)
        val rate = goldRepo.cached()?.pricePerGram ?: DEFAULT_GOLD_RATE
        val goldPoints = (rate / 100).roundToInt().coerceAtLeast(10)
        gameView.start(
            settings.speed, settings.maxBugs, settings.roundSeconds,
            settings.bonusIntervalSec, goldPoints
        )
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

    override fun onBonusCollected() {
        if (soundLoaded) soundPool?.play(screamId, 1f, 1f, 1, 0, 1f)
        Toast.makeText(requireContext(), "Бонус! Наклоняйте телефон", Toast.LENGTH_SHORT).show()
    }

    override fun onGoldCaught(points: Int) {
        val rate = goldRepo.cached()?.pricePerGram
        val rateText = if (rate != null) {
            " (курс золота: ${String.format(Locale("ru", "RU"), "%,.0f", rate)} ₽/г)"
        } else {
            ""
        }
        Toast.makeText(
            requireContext(), "Золотой таракан! +$points очков$rateText", Toast.LENGTH_SHORT
        ).show()
    }

    override fun onTiltChanged(secondsLeft: Int) {
        if (secondsLeft > 0) {
            tvBonus.text = "🎁 Наклоняйте телефон! Осталось: $secondsLeft с"
            tvBonus.visibility = View.VISIBLE
        } else {
            tvBonus.visibility = View.GONE
        }
    }

    // Вкладка стала активной — начинаем слушать акселерометр
    override fun onResume() {
        super.onResume()
        // обновляем курс золота в фоне: он понадобится в следующем раунде
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) { goldRepo.refreshBlocking() }
        }
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    // Ушли с вкладки — раунд прерывается, датчик отключаем
    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        gameView.stop()
        btnStart.isEnabled = true
        tvBonus.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        soundPool?.release()
        soundPool = null
        soundLoaded = false
    }

    // Показания датчика приходят в осях устройства — переводим в оси экрана
    // (положительное x — вправо, положительное y — вниз) с учётом поворота экрана
    override fun onSensorChanged(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        @Suppress("DEPRECATION")
        val rotation = requireActivity().windowManager.defaultDisplay.rotation
        when (rotation) {
            Surface.ROTATION_90 -> gameView.setTilt(ay, ax)
            Surface.ROTATION_180 -> gameView.setTilt(ax, -ay)
            Surface.ROTATION_270 -> gameView.setTilt(-ay, -ax)
            else -> gameView.setTilt(-ax, ay)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        const val DEFAULT_GOLD_RATE = 5000.0   // курс по умолчанию, если загрузить не удалось
    }
}
