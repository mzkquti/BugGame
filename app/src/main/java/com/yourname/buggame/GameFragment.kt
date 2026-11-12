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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yourname.buggame.game.GameEvent
import com.yourname.buggame.game.GameViewModel
import com.yourname.buggame.game.RoundResult
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale

// Экран игры. Состояния здесь нет: очки, время и итог берутся из GameViewModel,
// а фрагмент только показывает их и передаёт нажатия.
class GameFragment : Fragment(R.layout.fragment_game), SensorEventListener {

    // ViewModel создаёт Koin; она переживает поворот экрана
    private val viewModel: GameViewModel by viewModel()

    private lateinit var tvScore: TextView
    private lateinit var tvTime: TextView
    private lateinit var btnStart: Button
    private lateinit var gameView: GameView
    private lateinit var tvBonus: TextView

    private lateinit var resultCard: View
    private lateinit var tvResultScore: TextView
    private lateinit var tvResultHits: TextView
    private lateinit var tvResultMisses: TextView
    private lateinit var tvResultAccuracy: TextView
    private lateinit var btnReplay: Button

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
        tvBonus = view.findViewById(R.id.tvBonus)

        resultCard = view.findViewById(R.id.resultCard)
        tvResultScore = view.findViewById(R.id.tvResultScore)
        tvResultHits = view.findViewById(R.id.tvResultHits)
        tvResultMisses = view.findViewById(R.id.tvResultMisses)
        tvResultAccuracy = view.findViewById(R.id.tvResultAccuracy)
        btnReplay = view.findViewById(R.id.btnReplay)

        gameView.attach(viewModel.engine)
        btnStart.setOnClickListener { viewModel.startGame() }
        btnReplay.setOnClickListener { viewModel.startGame() }

        setupSensor()
        setupSound()
        observeViewModel()
    }

    // Подписка на состояние игры: после поворота экрана всё восстанавливается само
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.score.collect { tvScore.text = "Очки: $it" } }
                launch { viewModel.secondsLeft.collect { tvTime.text = "Время: $it" } }
                launch { viewModel.tiltSeconds.collect { showTilt(it) } }
                launch {
                    viewModel.running.collect {
                        btnStart.isEnabled = !it
                        gameView.setLoopRunning(it)
                    }
                }
                launch { viewModel.result.collect { showResult(it) } }
                launch { viewModel.events.collect { handleEvent(it) } }
            }
        }
    }

    private fun showTilt(secondsLeft: Int) {
        if (secondsLeft > 0) {
            tvBonus.text = "🎁 Наклоняйте телефон! Осталось: $secondsLeft с"
            tvBonus.visibility = View.VISIBLE
        } else {
            tvBonus.visibility = View.GONE
        }
    }

    private fun showResult(result: RoundResult?) {
        if (result == null) {
            resultCard.visibility = View.GONE
            return
        }
        tvResultScore.text = "Очки: ${result.score}"
        tvResultHits.text = "Попадания: ${result.hits}"
        tvResultMisses.text = "Промахи: ${result.misses}"
        tvResultAccuracy.text = "Точность: ${result.accuracy}%"
        resultCard.visibility = View.VISIBLE
    }

    private fun handleEvent(event: GameEvent) {
        when (event) {
            GameEvent.NeedPlayer -> toast(
                "Сначала зарегистрируйте или выберите игрока (вкладка «Регистрация»)",
                Toast.LENGTH_LONG
            )
            GameEvent.BonusCollected -> {
                if (soundLoaded) soundPool?.play(screamId, 1f, 1f, 1, 0, 1f)
                toast("Бонус! Наклоняйте телефон")
            }
            is GameEvent.GoldCaught -> {
                val rateText = event.ratePerGram?.let {
                    " (курс золота: ${String.format(Locale("ru", "RU"), "%,.0f", it)} ₽/г)"
                } ?: ""
                toast("Золотой таракан! +${event.points} очков$rateText")
            }
            GameEvent.ResultSaved -> toast("Результат сохранён")
        }
    }

    private fun toast(text: String, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(requireContext(), text, length).show()
    }

    private fun setupSensor() {
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun setupSound() {
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
    }

    // Вкладка стала активной — слушаем акселерометр и обновляем курс золота
    override fun onResume() {
        super.onResume()
        viewModel.refreshGoldRate()
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        // Поворот экрана раунд не прерывает (состояние хранит ViewModel),
        // а уход с вкладки или из приложения — прерывает.
        if (!requireActivity().isChangingConfigurations) viewModel.stopGame()
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
            Surface.ROTATION_90 -> viewModel.setTilt(ay, ax)
            Surface.ROTATION_180 -> viewModel.setTilt(ax, -ay)
            Surface.ROTATION_270 -> viewModel.setTilt(-ay, -ax)
            else -> viewModel.setTilt(-ax, ay)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
