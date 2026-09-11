package com.darrenai.omniscient

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class OrbState { IDLE, LISTENING, THINKING, SPEAKING }

/**
 * JARVIS-style reactor core: concentric glowing rings drawn on Canvas.
 * Each state animates differently — idle breathing, listening fast pulse,
 * thinking rotating arcs, speaking waveform ring. Zero dependencies.
 */
class ReactorOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var state: OrbState = OrbState.IDLE
        set(v) {
            field = v
            invalidate()
        }

    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 4000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    private val cyan = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
    }
    private val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC857")
        style = Paint.Style.STROKE
    }
    private val coreFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC857")
        style = Paint.Style.FILL
    }
    private val coreGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
        alpha = 24
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 8f
        val t = Math.toRadians(phase.toDouble())

        val (speed, pulseAmp) = when (state) {
            OrbState.IDLE -> 1.0 to 4f
            OrbState.LISTENING -> 3.0 to 12f
            OrbState.THINKING -> 2.0 to 7f
            OrbState.SPEAKING -> 5.0 to 16f
        }
        val breathe = (sin(t * speed) * pulseAmp).toFloat()

        // Outer glow disc
        coreGlow.alpha = when (state) {
            OrbState.IDLE -> 18
            OrbState.LISTENING -> 45
            OrbState.THINKING -> 32
            OrbState.SPEAKING -> 55
        }
        canvas.drawCircle(cx, cy, r * 0.98f, coreGlow)

        // Outer ring (cyan)
        cyan.strokeWidth = 4f
        cyan.alpha = 255
        canvas.drawCircle(cx, cy, r - breathe * 0.3f, cyan)

        // Rotating arc segments (gold) — thinking spins, others drift
        gold.strokeWidth = 5f
        val sweep = when (state) {
            OrbState.THINKING -> 120f
            OrbState.SPEAKING -> 200f
            else -> 70f
        }
        val base = (phase * (if (state == OrbState.THINKING) 2.2f else 0.7f))
        for (i in 0 until 3) {
            val start = base + i * 120f
            canvas.drawArc(
                cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f,
                start, sweep / 3f + 28f, false, gold
            )
        }

        // Waveform ring for SPEAKING, dashed ticks otherwise
        cyan.strokeWidth = 2.5f
        val ticks = 48
        for (i in 0 until ticks) {
            val a = Math.toRadians((i * 360f / ticks).toDouble())
            val wobble = if (state == OrbState.SPEAKING) {
                (sin(a * 6 + t * speed * 2) * 10f).toFloat()
            } else {
                (cos(a * 3 + t) * 3f).toFloat()
            }
            val r1 = r * 0.58f
            val r2 = r * 0.58f + 8f + wobble
            canvas.drawLine(
                (cx + cos(a) * r1).toFloat(), (cy + sin(a) * r1).toFloat(),
                (cx + cos(a) * r2).toFloat(), (cy + sin(a) * r2).toFloat(),
                cyan
            )
        }

        // Inner ring (cyan dim)
        cyan.strokeWidth = 3f
        cyan.alpha = 160
        canvas.drawCircle(cx, cy, r * 0.42f + breathe * 0.4f, cyan)

        // Core (gold)
        val coreR = r * 0.20f + breathe * 0.35f
        canvas.drawCircle(cx, cy, coreR, coreFill)
        cyan.alpha = 255
    }
}
