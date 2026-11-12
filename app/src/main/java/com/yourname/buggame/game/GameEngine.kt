package com.yourname.buggame.game

import com.yourname.buggame.Bug
import com.yourname.buggame.BugType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

// Вся логика раунда: насекомые, очки, таймер, бонус с наклоном, золотой таракан.
// Здесь нет ничего про экран и View, поэтому объект живёт во ViewModel
// и переживает поворот экрана. GameView только рисует его состояние.
class GameEngine {

    interface Listener {
        fun onScoreChanged(score: Int)
        fun onTimeChanged(secondsLeft: Int)
        fun onTiltChanged(secondsLeft: Int)   // 0 — режим наклона выключен
        fun onBonusCollected()
        fun onGoldCaught(points: Int)
        fun onGameOver(score: Int, hits: Int, misses: Int)
    }

    // Бонус на экране: пока не нажат, исчезает по таймеру
    class Bonus(var x: Float, var y: Float, val sizePx: Float, var lifeMs: Long)

    var listener: Listener? = null
    var density = 1f

    val bugs = mutableListOf<Bug>()

    var bonus: Bonus? = null
        private set

    var running = false
        private set

    private val bugTypes = listOf(
        BugType("🐛", 56f, 0.6f, 1),
        BugType("🐜", 40f, 1.0f, 2),
        BugType("🐞", 44f, 1.3f, 3),
        BugType("🐝", 40f, 1.7f, 4),
        BugType("🕷", 36f, 2.2f, 5)
    )

    // размеры игрового поля в пикселях (приходят от GameView)
    private var width = 0
    private var height = 0

    private var score = 0
    private var hits = 0
    private var misses = 0
    private var timeLeftMs = 0L
    private var lastSecondShown = -1

    private var speedLevel = 5
    private var maxBugs = 10
    private val penalty = 1

    // --- бонус и наклон ---
    private var bonusIntervalMs = 15_000L
    private var nextBonusMs = 0L
    private var tiltLeftMs = 0L
    private var lastTiltSecondShown = 0
    private var tiltX = 0f
    private var tiltY = 0f

    // --- золотой таракан ---
    private var goldPoints = 50
    private var nextGoldMs = 0L
    private var goldenBug: Bug? = null
    private var goldenLeftMs = 0L

    // Поле изменило размер (например, поворот экрана): переносим всё на новые места
    fun onSizeChanged(newWidth: Int, newHeight: Int) {
        if (newWidth <= 0 || newHeight <= 0) return
        if (width > 0 && height > 0 && (width != newWidth || height != newHeight)) {
            val kx = newWidth.toFloat() / width
            val ky = newHeight.toFloat() / height
            for (bug in bugs) {
                bug.x *= kx
                bug.y *= ky
            }
            bonus?.let {
                it.x *= kx
                it.y *= ky
            }
        }
        width = newWidth
        height = newHeight
    }

    fun start(
        speedLevel: Int,
        maxBugs: Int,
        roundSeconds: Int,
        bonusIntervalSec: Int,
        goldPoints: Int
    ) {
        this.speedLevel = speedLevel
        this.maxBugs = maxBugs
        this.goldPoints = goldPoints
        bugs.clear()
        resetSpecialState()
        bonusIntervalMs = bonusIntervalSec * 1000L
        nextBonusMs = bonusIntervalMs
        nextGoldMs = GOLD_INTERVAL_MS
        score = 0
        hits = 0
        misses = 0
        timeLeftMs = roundSeconds * 1000L
        lastSecondShown = roundSeconds
        running = true
        listener?.onScoreChanged(score)
        listener?.onTimeChanged(roundSeconds)
        listener?.onTiltChanged(0)
    }

    fun stop() {
        running = false
        bugs.clear()
        resetSpecialState()
    }

    // Показания акселерометра, уже пересчитанные в оси экрана
    fun setTilt(x: Float, y: Float) {
        tiltX = x
        tiltY = y
    }

    private fun resetSpecialState() {
        goldenBug = null
        bonus = null
        tiltLeftMs = 0L
        lastTiltSecondShown = 0
    }

    private fun finishRound() {
        running = false
        bugs.clear()
        resetSpecialState()
        listener?.onTiltChanged(0)
        listener?.onTimeChanged(0)
        listener?.onGameOver(score, hits, misses)
    }

    // Один кадр игры; dt — секунды с прошлого кадра
    fun update(dt: Float) {
        if (!running) return
        val dtMs = (dt * 1000).toLong()
        timeLeftMs -= dtMs
        if (timeLeftMs <= 0) {
            finishRound()
            return
        }
        val secondsLeft = ((timeLeftMs + 999) / 1000).toInt()
        if (secondsLeft != lastSecondShown) {
            lastSecondShown = secondsLeft
            listener?.onTimeChanged(secondsLeft)
        }

        updateBonus(dtMs)
        updateGolden(dtMs)

        if (bugs.size < maxBugs && Random.nextFloat() < 0.04f) spawnBug()

        val tilting = tiltLeftMs > 0
        // в режиме наклона жуки теряют энергию при ударах и «скатываются»
        val bounce = if (tilting) 0.4f else 1f
        val friction = if (tilting) (1f - dt).coerceAtLeast(0f) else 1f
        val accel = TILT_ACCEL_DP * density

        for (bug in bugs) {
            if (tilting) {
                bug.vx = (bug.vx + tiltX * accel * dt) * friction
                bug.vy = (bug.vy + tiltY * accel * dt) * friction
            }
            bug.x += bug.vx * dt
            bug.y += bug.vy * dt
            val r = bug.sizePx / 2
            if (bug.x < r) {
                bug.x = r
                bug.vx = abs(bug.vx) * bounce
            } else if (bug.x > width - r) {
                bug.x = width - r
                bug.vx = -abs(bug.vx) * bounce
            }
            if (bug.y < r) {
                bug.y = r
                bug.vy = abs(bug.vy) * bounce
            } else if (bug.y > height - r) {
                bug.y = height - r
                bug.vy = -abs(bug.vy) * bounce
            }
        }
    }

    // Нажатие на игровое поле: бонус, насекомое или промах
    fun onTap(x: Float, y: Float) {
        if (!running) return

        // нажатие на бонус не считается ни попаданием, ни промахом
        val b = bonus
        if (b != null && hypot(x - b.x, y - b.y) <= b.sizePx * 0.8f) {
            collectBonus()
            return
        }

        // ищем насекомое под пальцем (верхнее из нарисованных последним)
        val hit = bugs.lastOrNull { hypot(x - it.x, y - it.y) <= it.sizePx * 0.6f }
        if (hit != null) {
            bugs.remove(hit)
            score += hit.type.points
            hits++
            if (hit.golden) {
                goldenBug = null
                listener?.onGoldCaught(hit.type.points)
            }
        } else {
            score -= penalty
            misses++
        }
        listener?.onScoreChanged(score)
    }

    // Появление бонуса по таймеру и отсчёт времени действия наклона
    private fun updateBonus(dtMs: Long) {
        nextBonusMs -= dtMs
        if (nextBonusMs <= 0) {
            nextBonusMs = bonusIntervalMs
            if (bonus == null) spawnBonus()
        }

        bonus?.let {
            it.lifeMs -= dtMs
            if (it.lifeMs <= 0) bonus = null
        }

        if (tiltLeftMs > 0) {
            tiltLeftMs -= dtMs
            if (tiltLeftMs <= 0) {
                tiltLeftMs = 0
                endTilt()
            } else {
                val sec = ((tiltLeftMs + 999) / 1000).toInt()
                if (sec != lastTiltSecondShown) {
                    lastTiltSecondShown = sec
                    listener?.onTiltChanged(sec)
                }
            }
        }
    }

    // Каждые 20 секунд появляется золотой таракан и живёт ограниченное время
    private fun updateGolden(dtMs: Long) {
        nextGoldMs -= dtMs
        if (nextGoldMs <= 0) {
            nextGoldMs = GOLD_INTERVAL_MS
            if (goldenBug == null) spawnGolden()
        }
        goldenBug?.let {
            goldenLeftMs -= dtMs
            if (goldenLeftMs <= 0) {
                bugs.remove(it)
                goldenBug = null
            }
        }
    }

    private fun spawnBonus() {
        if (width == 0 || height == 0) return
        val size = BONUS_SIZE_DP * density
        val x = size + Random.nextFloat() * (width - 2 * size)
        val y = size + Random.nextFloat() * (height - 2 * size)
        bonus = Bonus(x, y, size, BONUS_LIFE_MS)
    }

    private fun collectBonus() {
        bonus = null
        tiltLeftMs = TILT_DURATION_MS
        lastTiltSecondShown = (TILT_DURATION_MS / 1000).toInt()
        listener?.onBonusCollected()
        listener?.onTiltChanged(lastTiltSecondShown)
    }

    // Режим наклона закончился: возвращаем жукам обычную скорость
    private fun endTilt() {
        lastTiltSecondShown = 0
        for (bug in bugs) {
            val baseSpeed = BASE_SPEED_DP * density * speedLevel * bug.type.speedMultiplier
            val current = hypot(bug.vx, bug.vy)
            if (current < 1f) {
                val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                bug.vx = cos(angle) * baseSpeed
                bug.vy = sin(angle) * baseSpeed
            } else {
                bug.vx = bug.vx / current * baseSpeed
                bug.vy = bug.vy / current * baseSpeed
            }
        }
        listener?.onTiltChanged(0)
    }

    private fun spawnBug() {
        if (width == 0 || height == 0) return
        bugs.add(newBug(bugTypes.random(), golden = false))
    }

    private fun spawnGolden() {
        if (width == 0 || height == 0) return
        val bug = newBug(BugType("🪳", 52f, 1.8f, goldPoints), golden = true)
        bugs.add(bug)
        goldenBug = bug
        goldenLeftMs = GOLD_LIFE_MS
    }

    // Новое насекомое в случайном месте со случайным направлением
    private fun newBug(type: BugType, golden: Boolean): Bug {
        val size = type.sizeDp * density
        val speed = BASE_SPEED_DP * density * speedLevel * type.speedMultiplier
        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
        val x = size / 2 + Random.nextFloat() * (width - size)
        val y = size / 2 + Random.nextFloat() * (height - size)
        return Bug(type, x, y, cos(angle) * speed, sin(angle) * speed, size, golden)
    }

    private companion object {
        const val BASE_SPEED_DP = 30f           // скорость жука на единицу уровня скорости
        const val TILT_ACCEL_DP = 25f           // насколько сильно наклон разгоняет жуков
        const val BONUS_SIZE_DP = 48f
        const val BONUS_LIFE_MS = 6_000L        // бонус исчезает, если его не нажали
        const val TILT_DURATION_MS = 10_000L    // сколько действует наклон
        const val GOLD_INTERVAL_MS = 20_000L    // золотой таракан появляется каждые 20 секунд
        const val GOLD_LIFE_MS = 8_000L         // и убегает, если его не поймали
    }
}
