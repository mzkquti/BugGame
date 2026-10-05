package com.yourname.buggame

import android.content.Context
import android.graphics.Canvas
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
    }

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

    fun start(speedLevel: Int, maxBugs: Int, roundSeconds: Int) {
        this.speedLevel = speedLevel
        this.maxBugs = maxBugs
        bugs.clear()
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
        invalidate()
    }

    private fun finishRound() {
        running = false
        bugs.clear()
        invalidate()
        listener?.onTimeChanged(0)
        listener?.onGameOver(score, hits, misses)
    }

    private fun update(dt: Float) {
        timeLeftMs -= (dt * 1000).toLong()
        if (timeLeftMs <= 0) {
            finishRound()
            return
        }
        val secondsLeft = ((timeLeftMs + 999) / 1000).toInt()
        if (secondsLeft != lastSecondShown) {
            lastSecondShown = secondsLeft
            listener?.onTimeChanged(secondsLeft)
        }

        if (bugs.size < maxBugs && Random.nextFloat() < 0.04f) spawnBug()

        for (bug in bugs) {
            bug.x += bug.vx * dt
            bug.y += bug.vy * dt
            val r = bug.sizePx / 2
            if (bug.x < r) {
                bug.x = r
                bug.vx = abs(bug.vx)
            } else if (bug.x > width - r) {
                bug.x = width - r
                bug.vx = -abs(bug.vx)
            }
            if (bug.y < r) {
                bug.y = r
                bug.vy = abs(bug.vy)
            } else if (bug.y > height - r) {
                bug.y = height - r
                bug.vy = -abs(bug.vy)
            }
        }
    }

    private fun spawnBug() {
        if (width == 0 || height == 0) return
        val type = bugTypes.random()
        val size = type.sizeDp * density
        val speed = 30f * density * speedLevel * type.speedMultiplier
        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
        val x = size / 2 + Random.nextFloat() * (width - size)
        val y = size / 2 + Random.nextFloat() * (height - size)
        bugs.add(Bug(type, x, y, cos(angle) * speed, sin(angle) * speed, size))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (bug in bugs) {
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
            // ищем насекомое под пальцем (верхнее из нарисованных последним)
            val hit = bugs.lastOrNull {
                hypot(event.x - it.x, event.y - it.y) <= it.sizePx * 0.6f
            }
            if (hit != null) {
                bugs.remove(hit)
                score += hit.type.points
                hits++
            } else {
                score -= penalty
                misses++
            }
            listener?.onScoreChanged(score)
            invalidate()
        }
        return true
    }
}