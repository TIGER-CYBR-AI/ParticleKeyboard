package com.example.particlekeyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A self-contained particle simulation.
 *
 * hasGap = true  -> used for the small "text preview" strip: particles from
 *                    the UP group live only in the top part of [bounds], DOWN
 *                    group only in the bottom part, leaving a permanently
 *                    empty middle gap where the typed word forms (exactly
 *                    like the web demo).
 * hasGap = false -> used for the animated background behind the keys: all
 *                    particles just flow continuously inside the whole rect,
 *                    no gap needed since the keys are drawn on top.
 */
class ParticleField(
    private var bounds: RectF,
    private val particleCount: Int,
    private val textPoolSize: Int,
    private val hasGap: Boolean
) {
    private enum class Mode { DRIFT, TEXT, EXPLODING }
    private enum class Group { UP, DOWN }

    private inner class Particle {
        val group = if (Random.nextBoolean()) Group.UP else Group.DOWN
        val speed = Random.nextDouble(0.4, 1.0).toFloat()
        val size = Random.nextDouble(1.5, 4.0).toFloat()
        val wanderPhase = Random.nextDouble(0.0, Math.PI * 2).toFloat()
        val wanderFreq = Random.nextDouble(0.3, 0.8).toFloat()

        var x = 0f
        var y = 0f
        var mode = Mode.DRIFT
        var targetX = 0f
        var targetY = 0f
        var vx = 0f
        var vy = 0f

        init { resetIntoBand() }

        fun resetIntoBand() {
            x = Random.nextDouble(bounds.left.toDouble(), bounds.right.toDouble()).toFloat()
            y = if (!hasGap) {
                Random.nextDouble(bounds.top.toDouble(), bounds.bottom.toDouble()).toFloat()
            } else if (group == Group.UP) {
                Random.nextDouble(bounds.top.toDouble(), gapTop.toDouble()).toFloat()
            } else {
                Random.nextDouble(gapBottom.toDouble(), bounds.bottom.toDouble()).toFloat()
            }
        }

        fun driftStep(t: Float) {
            if (!hasGap) {
                if (group == Group.UP) {
                    y -= speed
                    if (y < bounds.top) y = bounds.bottom
                } else {
                    y += speed
                    if (y > bounds.bottom) y = bounds.top
                }
            } else {
                if (group == Group.UP) {
                    y -= speed
                    if (y < bounds.top) {
                        y = gapTop
                        x = Random.nextDouble(bounds.left.toDouble(), bounds.right.toDouble()).toFloat()
                    }
                } else {
                    y += speed
                    if (y > bounds.bottom) {
                        y = gapBottom
                        x = Random.nextDouble(bounds.left.toDouble(), bounds.right.toDouble()).toFloat()
                    }
                }
            }
            x += sin(t * wanderFreq + wanderPhase) * 0.2f
        }

        fun update(t: Float) {
            when (mode) {
                Mode.TEXT -> {
                    x += (targetX - x) * 0.12f
                    y += (targetY - y) * 0.12f
                }
                Mode.EXPLODING -> {
                    x += vx; y += vy
                    vx *= 0.92f; vy *= 0.92f
                    if (abs(vx) < 0.2f && abs(vy) < 0.2f) mode = Mode.DRIFT
                }
                Mode.DRIFT -> driftStep(t)
            }
        }

        fun explodeFrom(cx: Float, cy: Float) {
            val dx = x - cx
            val dy = y - cy
            val dist = sqrt(dx * dx + dy * dy).let { if (it == 0f) 1f else it }
            val power = Random.nextDouble(5.0, 11.0).toFloat()
            vx = (dx / dist) * power + Random.nextDouble(-1.5, 1.5).toFloat()
            vy = (dy / dist) * power + Random.nextDouble(-1.5, 1.5).toFloat()
            mode = Mode.EXPLODING
        }
    }

    private var gapTop = 0f
    private var gapBottom = 0f
    private val particles = mutableListOf<Particle>()
    private var textPool: List<Particle> = emptyList()
    private var currentlyFormed = false
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init { rebuild() }

    private fun rebuild() {
        if (hasGap) {
            val h = bounds.height()
            gapTop = bounds.top + h * 0.30f
            gapBottom = bounds.top + h * 0.70f
        }
        particles.clear()
        repeat(particleCount) { particles.add(Particle()) }
        textPool = particles.take(textPoolSize)
        currentlyFormed = false
    }

    /** Call this whenever the view is resized (rotation, real layout change only). */
    fun setBounds(newBounds: RectF) {
        bounds = newBounds
        rebuild()
    }

    /** Forms the given word out of the text-pool particles, inside the gap. No-op if hasGap=false. */
    fun applyText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) { clearText(); return }
        if (!hasGap || textPool.isEmpty()) return

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            isFakeBoldText = true
        }
        val gapHeight = gapBottom - gapTop
        var fontSize = gapHeight * 0.8f
        textPaint.textSize = fontSize
        val maxWidth = bounds.width() * 0.92f
        val rawWidth = textPaint.measureText(trimmed)
        if (rawWidth > maxWidth) {
            fontSize *= (maxWidth / rawWidth)
            textPaint.textSize = fontSize
        }

        val path = Path()
        val textWidth = textPaint.measureText(trimmed)
        val startX = bounds.centerX() - textWidth / 2f
        val baselineY = (gapTop + gapBottom) / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        textPaint.getTextPath(trimmed, 0, trimmed.length, startX, baselineY, path)

        val points = mutableListOf<Pair<Float, Float>>()
        val pm = PathMeasure(path, false)
        var hasContour = true
        while (hasContour) {
            val len = pm.length
            var dist = 0f
            val step = 3f
            val pos = FloatArray(2)
            while (dist < len) {
                pm.getPosTan(dist, pos, null)
                points.add(pos[0] to pos[1])
                dist += step
            }
            hasContour = pm.nextContour()
        }

        if (points.isEmpty()) return
        val n = minOf(points.size, textPool.size)
        for (i in 0 until n) {
            val p = textPool[i]
            val pt = points[i % points.size]
            p.targetX = pt.first + Random.nextDouble(-1.0, 1.0).toFloat()
            p.targetY = pt.second + Random.nextDouble(-1.0, 1.0).toFloat()
            p.mode = Mode.TEXT
        }
        for (i in n until textPool.size) {
            if (textPool[i].mode == Mode.TEXT) textPool[i].mode = Mode.DRIFT
        }
        currentlyFormed = true
    }

    /** Explodes any currently-formed text particles back into free drift. */
    fun clearText() {
        if (!currentlyFormed) return
        val cx = bounds.centerX()
        val cy = if (hasGap) (gapTop + gapBottom) / 2f else bounds.centerY()
        for (p in textPool) if (p.mode == Mode.TEXT) p.explodeFrom(cx, cy)
        currentlyFormed = false
    }

    fun update(t: Float) {
        for (p in particles) p.update(t)
    }

    /** sharedHue must be the SAME value passed to every field you draw this frame,
     *  so the whole keyboard pulses as one color, not a rainbow of separate ones. */
    fun draw(canvas: Canvas, sharedHue: Float) {
        for (p in particles) {
            // two-pass glow: a soft wide halo + a bright tight core.
            // This is what actually makes particles look like they're
            // GLOWING against the black background instead of just floating
            // flat dots.
            dotPaint.color = Color.HSVToColor(70, floatArrayOf(sharedHue, 0.65f, 0.95f))
            canvas.drawCircle(p.x, p.y, p.size * 3.2f, dotPaint)

            dotPaint.color = Color.HSVToColor(255, floatArrayOf(sharedHue, 0.55f, 1f))
            canvas.drawCircle(p.x, p.y, p.size, dotPaint)
        }
    }
}
