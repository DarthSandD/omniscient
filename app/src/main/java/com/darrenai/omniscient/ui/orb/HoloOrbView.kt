package com.darrenai.omniscient.ui.orb

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.darrenai.omniscient.domain.OrbState
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Holographic core: halo glow, rotating arc segments, scan sweep, particle
 * field and a tick ring, all on Canvas. Zero dependencies.
 *
 * States: IDLE (slow cyan breath), LISTENING (fast cyan pulse),
 * THINKING (gold spin), ACTING (gold blaze — a tool is executing),
 * SPEAKING (cyan waveform).
 */
class HoloOrbView @JvmOverloads constructor(
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
        duration = 4200L
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
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val speck = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9BEFFF")
        style = Paint.Style.FILL
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

        val goldBias = state == OrbState.ACTING || state == OrbState.THINKING
        val (speed, pulseAmp) = when (state) {
            OrbState.IDLE -> 1.0 to 4f
            OrbState.LISTENING -> 3.0 to 12f
            OrbState.THINKING -> 2.4 to 7f
            OrbState.ACTING -> 4.0 to 11f
            OrbState.SPEAKING -> 5.0 to 16f
        }
        val breathe = (sin(t * speed) * pulseAmp).toFloat()

        // Halo (radial falloff, color follows state)
        val haloColor = if (goldBias) Color.parseColor("#FFC857") else Color.parseColor("#00E5FF")
        val haloAlpha = when (state) {
            OrbState.IDLE -> 26
            OrbState.LISTENING -> 60
            OrbState.THINKING -> 44
            OrbState.ACTING -> 70
            OrbState.SPEAKING -> 60
        }
        halo.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(haloColor and 0x00FFFFFF or (haloAlpha shl 24), Color.TRANSPARENT),
            floatArrayOf(0.35f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, halo)

        // Outer ring
        val ring = if (goldBias) gold else cyan
        ring.strokeWidth = 4f
        ring.alpha = 255
        canvas.drawCircle(cx, cy, r * 0.96f - breathe * 0.3f, ring)

        // Rotating arc segments
        val sweep = when (state) {
            OrbState.THINKING -> 120f
            OrbState.ACTING -> 160f
            OrbState.SPEAKING -> 200f
            else -> 70f
        }
        val spin = when (state) {
            OrbState.THINKING -> 2.4f
            OrbState.ACTING -> 3.4f
            else -> 0.7f
        }
        (if (goldBias) cyan else gold).let { arcPaint ->
            arcPaint.strokeWidth = 5f
            arcPaint.alpha = 255
            val base = phase * spin
            for (i in 0 until 3) {
                canvas.drawArc(
                    cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f,
                    base + i * 120f, sweep / 3f + 28f, false, arcPaint
                )
            }
        }

        // Scan sweep: one bright needle revolving (ACTING spins fastest)
        val scanSpeed = if (state == OrbState.ACTING) 3.0 else 1.0
        val sa = t * scanSpeed
        cyan.strokeWidth = 2f
        cyan.alpha = 200
        canvas.drawLine(cx, cy, (cx + cos(sa) * r * 0.9f).toFloat(), (cy + sin(sa) * r * 0.9f).toFloat(), cyan)
        cyan.alpha = 255

        // Particle field: 24 specks orbiting at fixed radii, twinkling
        speck.alpha = if (goldBias) 150 else 200
        for (i in 0 until 24) {
            val a = t * (0.4 + (i % 5) * 0.12) + i * 0.5236
            val pr = r * (0.30f + (i % 7) * 0.085f)
            val tw = 1.6f + (sin(a * 3 + i) * 1.2f).toFloat()
            canvas.drawCircle((cx + cos(a) * pr).toFloat(), (cy + sin(a) * pr).toFloat(), tw, speck)
        }

        // Tick ring: waveform when SPEAKING, dashed ticks otherwise
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

        // Inner ring
        cyan.strokeWidth = 3f
        cyan.alpha = 160
        canvas.drawCircle(cx, cy, r * 0.42f + breathe * 0.4f, cyan)

        // Core: gold, flares in ACTING
        val coreR = r * 0.20f + breathe * 0.35f + (if (state == OrbState.ACTING) r * 0.05f else 0f)
        coreFill.alpha = 255
        canvas.drawCircle(cx, cy, coreR, coreFill)
        cyan.alpha = 255
        gold.alpha = 255
    }
}
