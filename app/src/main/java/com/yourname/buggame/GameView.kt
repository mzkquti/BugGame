package com.yourname.buggame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.yourname.buggame.game.GameEngine
import kotlin.math.sin

// Игровое поле. Только рисует состояние GameEngine и передаёт ему нажатия;
// сама игра (и её состояние) живёт во ViewModel, поэтому при повороте экрана
// новый GameView просто подключается к тому же движку.
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var engine: GameEngine? = null

    private val density = resources.displayMetrics.density

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

    private var loopActive = false
    private var lastFrameNs = 0L

    // Игровой цикл: вызывается на каждый кадр экрана
    private val frameRunnable = object : Runnable {
        override fun run() {
            val e = engine
            if (!loopActive || e == null) return
            val now = System.nanoTime()
            // ограничиваем dt, чтобы после паузы насекомые не «прыгали»
            val dt = ((now - lastFrameNs) / 1_000_000_000f).coerceAtMost(0.1f)
            lastFrameNs = now
            e.update(dt)
            invalidate()
            if (e.running) postOnAnimation(this) else loopActive = false
        }
    }

    // Подключаем поле к игровому движку из ViewModel
    fun attach(engine: GameEngine) {
        this.engine = engine
        engine.density = density
        if (width > 0 && height > 0) engine.onSizeChanged(width, height)
        invalidate()
    }

    // Включает и выключает покадровую анимацию
    fun setLoopRunning(run: Boolean) {
        if (run == loopActive) return
        loopActive = run
        if (run) {
            lastFrameNs = System.nanoTime()
            postOnAnimation(frameRunnable)
        } else {
            removeCallbacks(frameRunnable)
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        engine?.onSizeChanged(w, h)
    }

    override fun onDetachedFromWindow() {
        loopActive = false
        removeCallbacks(frameRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val e = engine ?: return

        e.bonus?.let {
            // бонус «пульсирует», чтобы его было видно среди насекомых
            val pulse = 1f + 0.12f * sin(System.nanoTime() / 150_000_000.0).toFloat()
            canvas.drawCircle(it.x, it.y, it.sizePx * 0.6f * pulse, bonusPaint)
            paint.textSize = it.sizePx
            val baseline = it.y - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText("🎁", it.x, baseline, paint)
        }

        for (bug in e.bugs) {
            if (bug.golden) canvas.drawCircle(bug.x, bug.y, bug.sizePx * 0.65f, goldPaint)
            paint.textSize = bug.sizePx
            val baseline = bug.y - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(bug.type.emoji, bug.x, baseline, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // не даём ViewPager2 перехватывать касания на игровом поле
        parent?.requestDisallowInterceptTouchEvent(true)

        val e = engine
        if (e == null || !e.running) return super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            e.onTap(event.x, event.y)
            invalidate()
        }
        return true
    }
}
