package com.yourname.buggame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onScoreChanged(score: Int)
        fun onTimeChanged(secondsLeft: Int)
        fun onGameOver(score: Int, hits: Int, misses: Int)

        // Игрок нажал на бонус: включился режим наклона
        fun onBonusCollected() {}

        // Сколько секунд ещё действует режим наклона (0 — режим выключен)
        fun onTiltChanged(secondsLeft: Int) {}

        // Игрок поймал золотого таракана и получил столько очков
        fun onGoldCaught(points: Int) {}
    }

    // Бонус на экране: пока не нажат, исчезает по таймеру
    private class Bonus(val x: Float, val y: Float, val sizePx: Float, var lifeMs: Long)

    var listener: Listener? = null

    private val density = resources.displayMetrics.density

    private val bugTypes = listOf(
        BugType("🐛", 56f, 0.6f, 1),
        BugType("🐜", 40f, 1.0f, 2),
        BugType("🐞", 44f, 1.3f, 3),
        BugType("🐝", 40f, 1.7f, 4),
        BugType("🕷", 36f, 2.2f, 5)
    )

    private val bugs = mutableListOf<Bug>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val bonusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD54F")
        style = Paint.Style.FILL
    }

    private val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC107")
        alpha = 200
        style = Paint.Style.FILL
    }

    private var running = false
    private var score = 0
    private var hits = 0
    private var misses = 0
    private var timeLeftMs = 0L
    private var lastSecondShown = -1
    private var lastFrameNs = 0L

    private var speedLevel = 5
    private var maxBugs = 10
    private val penalty = 1

    // --- бонус и наклон ---
    private val bonusLifeMs = 6_000L
    private val tiltDurationMs = 10_000L
    private var bonusIntervalMs = 15_000L
    private var nextBonusMs = 0L
    private var bonus: Bonus? = null
    private var tiltLeftMs = 0L
    private var lastTiltSecondShown = 0

    // --- золотой таракан ---
    private var goldPoints = 50
    private var nextGoldMs = 0L
    private var goldenBug: Bug? = null
    private var goldenLeftMs = 0L

    // ускорение «вниз по экрану» от наклона телефона (м/с², с учётом поворота экрана)
    private var tiltX = 0f
    private var tiltY = 0f

    // Игровой цикл: вызывается на каждый кадр экрана
    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val dt = (now - lastFrameNs) / 1_000_000_000f
            lastFrameNs = now
            update(dt)
            invalidate()
            if (running) postOnAnimation(this)
        }
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
        bugs.clear()
        resetBonusState()
        bonusIntervalMs = bonusIntervalSec * 1000L
        nextBonusMs = bonusIntervalMs
        this.goldPoints = goldPoints
        nextGoldMs = GOLD_INTERVAL_MS
        score = 0
        hits = 0
        misses = 0
        timeLeftMs = roundSeconds * 1000L
        lastSecondShown = roundSeconds
        listener?.onScoreChanged(score)
        listener?.onTimeChanged(roundSeconds)
        running = true
        lastFrameNs = System.nanoTime()
        postOnAnimation(frameRunnable)
    }

    fun stop() {
        running = false
        removeCallbacks(frameRunnable)
        bugs.clear()
        resetBonusState()
        invalidate()
    }

    // Фрагмент передаёт сюда показания акселерометра, уже пересчитанные в оси экрана
    fun setTilt(x: Float, y: Float) {
        tiltX = x
        tiltY = y
    }

    private fun resetBonusState() {
        goldenBug = null
        bonus = null
        tiltLeftMs = 0L
        lastTiltSecondShown = 0
    }

    private fun finishRound() {
        running = false
        bugs.clear()
        resetBonusState()
        invalidate()
        listener?.onTiltChanged(0)
        listener?.onTimeChanged(0)
        listener?.onGameOver(score, hits, misses)
    }

    private fun update(dt: Float) {
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
        // коэффициент отскока: в режиме наклона жуки теряют энергию и «скатываются»
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

    private fun spawnGolden() {
        if (width == 0 || height == 0) return
        val type = BugType("🪳", 52f, 1.8f, goldPoints)
        val size = type.sizeDp * density
        val speed = BASE_SPEED_DP * density * speedLevel * type.speedMultiplier
        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
        val x = size / 2 + Random.nextFloat() * (width - size)
        val y = size / 2 + Random.nextFloat() * (height - size)
        val bug = Bug(type, x, y, cos(angle) * speed, sin(angle) * speed, size, golden = true)
        bugs.add(bug)
        goldenBug = bug
        goldenLeftMs = GOLD_LIFE_MS
    }

    private fun spawnBonus() {
        if (width == 0 || height == 0) return
        val size = BONUS_SIZE_DP * density
        val x = size + Random.nextFloat() * (width - 2 * size)
        val y = size + Random.nextFloat() * (height - 2 * size)
        bonus = Bonus(x, y, size, bonusLifeMs)
    }

    private fun collectBonus() {
        bonus = null
        tiltLeftMs = tiltDurationMs
        lastTiltSecondShown = (tiltDurationMs / 1000).toInt()
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
        val type = bugTypes.random()
        val size = type.sizeDp * density
        val speed = BASE_SPEED_DP * density * speedLevel * type.speedMultiplier
        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
        val x = size / 2 + Random.nextFloat() * (width - size)
        val y = size / 2 + Random.nextFloat() * (height - size)
        bugs.add(Bug(type, x, y, cos(angle) * speed, sin(angle) * speed, size))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        bonus?.let {
            // бонус «пульсирует», чтобы его было видно среди насекомых
            val pulse = 1f + 0.12f * sin(System.nanoTime() / 150_000_000.0).toFloat()
            canvas.drawCircle(it.x, it.y, it.sizePx * 0.6f * pulse, bonusPaint)
            paint.textSize = it.sizePx
            val baseline = it.y - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText("🎁", it.x, baseline, paint)
        }

        for (bug in bugs) {
            if (bug.golden) canvas.drawCircle(bug.x, bug.y, bug.sizePx * 0.65f, goldPaint)
            paint.textSize = bug.sizePx
            val baseline = bug.y - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(bug.type.emoji, bug.x, baseline, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // не даём ViewPager2 перехватывать касания на игровом поле
        parent?.requestDisallowInterceptTouchEvent(true)

        if (!running) return super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // сначала проверяем бонус: нажатие на него не считается ни попаданием, ни промахом
            val b = bonus
            if (b != null && hypot(event.x - b.x, event.y - b.y) <= b.sizePx * 0.8f) {
                collectBonus()
                invalidate()
                return true
            }

            // ищем насекомое под пальцем (верхнее из нарисованных последним)
            val hit = bugs.lastOrNull {
                hypot(event.x - it.x, event.y - it.y) <= it.sizePx * 0.6f
            }
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
            invalidate()
        }
        return true
    }

    private companion object {
        const val BASE_SPEED_DP = 30f      // базовая скорость жука на единицу уровня скорости
        const val TILT_ACCEL_DP = 25f      // насколько сильно наклон разгоняет жуков
        const val BONUS_SIZE_DP = 48f
        const val GOLD_INTERVAL_MS = 20_000L   // золотой таракан появляется каждые 20 секунд
        const val GOLD_LIFE_MS = 8_000L        // и убегает, если его не поймали
    }
}
