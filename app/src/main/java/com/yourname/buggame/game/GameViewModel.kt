package com.yourname.buggame.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.buggame.GameSettings
import com.yourname.buggame.data.AppDatabase
import com.yourname.buggame.data.GameResult
import com.yourname.buggame.gold.GoldRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Итог раунда для итоговой карточки
data class RoundResult(val score: Int, val hits: Int, val misses: Int) {
    val accuracy: Int
        get() = if (hits + misses > 0) hits * 100 / (hits + misses) else 0
}

// Разовые события для экрана (звук, сообщения)
sealed interface GameEvent {
    data object NeedPlayer : GameEvent
    data object BonusCollected : GameEvent
    data class GoldCaught(val points: Int, val ratePerGram: Double?) : GameEvent
    data object ResultSaved : GameEvent
}

// Хранит состояние игры. ViewModel переживает поворот экрана, поэтому раунд,
// очки и положение насекомых не сбрасываются, когда Activity пересоздаётся.
class GameViewModel(
    private val settings: GameSettings,
    private val goldRepo: GoldRepository,
    private val db: AppDatabase
) : ViewModel(), GameEngine.Listener {

    val engine = GameEngine().also { it.listener = this }

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _secondsLeft = MutableStateFlow(0)
    val secondsLeft: StateFlow<Int> = _secondsLeft.asStateFlow()

    private val _tiltSeconds = MutableStateFlow(0)
    val tiltSeconds: StateFlow<Int> = _tiltSeconds.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _result = MutableStateFlow<RoundResult?>(null)
    val result: StateFlow<RoundResult?> = _result.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    fun startGame() {
        if (settings.currentPlayerId <= 0L) {
            _events.tryEmit(GameEvent.NeedPlayer)
            return
        }
        // очки за золотого таракана пропорциональны курсу золота (1 очко за каждые 100 ₽/г)
        val rate = goldRepo.cached()?.pricePerGram ?: DEFAULT_GOLD_RATE
        val goldPoints = (rate / 100).roundToInt().coerceAtLeast(10)

        _result.value = null
        _running.value = true
        engine.start(
            settings.speed, settings.maxBugs, settings.roundSeconds,
            settings.bonusIntervalSec, goldPoints
        )
    }

    fun stopGame() {
        if (!engine.running) return
        engine.stop()
        _running.value = false
        _tiltSeconds.value = 0
    }

    fun setTilt(x: Float, y: Float) = engine.setTilt(x, y)

    // Свежий курс золота нужен для следующего раунда
    fun refreshGoldRate() {
        viewModelScope.launch(Dispatchers.IO) { goldRepo.refreshBlocking() }
    }

    // ---- события движка ----

    override fun onScoreChanged(score: Int) {
        _score.value = score
    }

    override fun onTimeChanged(secondsLeft: Int) {
        _secondsLeft.value = secondsLeft
    }

    override fun onTiltChanged(secondsLeft: Int) {
        _tiltSeconds.value = secondsLeft
    }

    override fun onBonusCollected() {
        _events.tryEmit(GameEvent.BonusCollected)
    }

    override fun onGoldCaught(points: Int) {
        _events.tryEmit(GameEvent.GoldCaught(points, goldRepo.cached()?.pricePerGram))
    }

    override fun onGameOver(score: Int, hits: Int, misses: Int) {
        _running.value = false
        _result.value = RoundResult(score, hits, misses)
        saveResult(score, hits, misses)
    }

    // Результат раунда сохраняется в базу данных (Room)
    private fun saveResult(score: Int, hits: Int, misses: Int) {
        val playerId = settings.currentPlayerId
        if (playerId <= 0L) return
        viewModelScope.launch {
            db.resultDao().insert(
                GameResult(
                    playerId = playerId,
                    score = score,
                    hits = hits,
                    misses = misses,
                    playedAt = System.currentTimeMillis()
                )
            )
            _events.emit(GameEvent.ResultSaved)
        }
    }

    private companion object {
        const val DEFAULT_GOLD_RATE = 5000.0   // курс по умолчанию, если загрузить не удалось
    }
}
