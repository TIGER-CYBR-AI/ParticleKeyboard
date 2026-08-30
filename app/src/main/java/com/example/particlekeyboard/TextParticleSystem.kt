package com.example.particlekeyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Particles for the text-preview strip are created ONLY as needed — one per
 * visible "ink point" of the current text, not a big pre-allocated pool.
 * Typing a new character grows the list by exactly as many particles as
 * that adds; backspacing shrinks it. Particles spawn from above the strip
 * and ease into their target point, giving the "falling in and locking
 * into place" look. Nothing explodes on space anymore — only on Send or on
 * fully clearing the text (typing all the way back to empty).
 */
class TextParticleSystem(private var bounds: RectF) {

    private data class TP(
        var x: Float, var y: Float,
        var targetX: Float, var targetY: Float,
        var exploding: Boolean = false,
        var vx: Float = 0f, var vy: Float = 0f,
        var life: Int = 0
    )

    private var gapTop = 0f
    private var gapBottom = 0f
    private val active = mutableListOf<TP>()
    private val expiring = mutableListOf<TP>()
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maxPoints = 900

    init { recomputeBand() }

    private fun recomputeBand() {
        val h = bounds.height()
        gapTop = bounds.top + h * 0.30f
        gapBottom = bounds.top + h * 0.70f
    }

    fun setBounds(newBounds: RectF) {
        bounds = newBounds
        recomputeBand()
        clearAll()
    }

    private fun rand(a: Float, b: Float) = Random.nextDouble(a.toDouble(), b.toDouble()).toFloat()

    private fun samplePoints(text: String, fontSize: Float, displayText: String): List<Pair<Float, Float>> {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            isFakeBoldText = true
            textSize = fontSize
        }
        val path = Path()
        val textWidth = textPaint.measureText(displayText)
        val startX = bounds.centerX() - textWidth / 2f
        val baselineY = (gapTop + gapBottom) / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        textPaint.getTextPath(displayText, 0, displayText.length, startX, baselineY, path)

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
        if (points.size > maxPoints) {
            val k = (points.size / maxPoints) + 1
            return points.filterIndexed { i, _ -> i % k == 0 }
        }
        return points
    }

    /** Shrinks the font as the sentence grows; once it hits a legible floor,
     *  it keeps that size and shows only the most recent portion that fits —
     *  like a ticker — while the FULL text is still sent to the app
     *  correctly regardless (this preview is just a visual, not the source
     *  of truth). */
    private fun fitText(text: String): Pair<String, Float> {
        val maxWidth = bounds.width() * 0.9f
        val maxFont = bounds.height() * 0.34f
        val minFont = bounds.height() * 0.15f
        val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }

        measurePaint.textSize = maxFont
        var width = measurePaint.measureText(text)
        if (width <= maxWidth) return text to maxFont

        var fontSize = (maxFont * (maxWidth / width)).coerceAtLeast(minFont)
        measurePaint.textSize = fontSize
        width = measurePaint.measureText(text)
        if (width <= maxWidth) return text to fontSize

        measurePaint.textSize = minFont
        var start = 0
        while (start < text.length && measurePaint.measureText(text.substring(start)) > maxWidth) start++
        return text.substring(start) to minFont
    }

    fun applyText(text: String) {
        if (text.isEmpty()) { clearAll(); return }
        val (displayText, fontSize) = fitText(text)
        val points = samplePoints(text, fontSize, displayText)
        if (points.isEmpty()) return

        for (i in points.indices) {
            val pt = points[i]
            if (i < active.size) {
                active[i].targetX = pt.first
                active[i].targetY = pt.second
            } else {
                active.add(
                    TP(
                        x = pt.first + rand(-25f, 25f),
                        y = gapTop - rand(30f, 90f),
                        targetX = pt.first,
                        targetY = pt.second
                    )
                )
            }
        }
        while (active.size > points.size) {
            popLastToExplode()
        }
    }

    private fun popLastToExplode() {
        val p = active.removeAt(active.size - 1)
        val cx = bounds.centerX()
        val cy = (gapTop + gapBottom) / 2f
        val dx = p.x - cx
        val dy = p.y - cy
        val dist = max(1f, sqrt(dx * dx + dy * dy))
        val power = rand(4f, 9f)
        p.vx = (dx / dist) * power + rand(-1.5f, 1.5f)
        p.vy = (dy / dist) * power + rand(-1.5f, 1.5f)
        p.exploding = true
        p.life = 40
        expiring.add(p)
    }

    /** Send, or backspacing all the way to empty: everything currently
     *  formed explodes away at once. */
    fun clearAll() {
        while (active.isNotEmpty()) popLastToExplode()
    }

    fun update() {
        for (p in active) {
            p.x += (p.targetX - p.x) * 0.09f
            p.y += (p.targetY - p.y) * 0.09f
        }
        val it = expiring.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx
            p.y += p.vy
            p.vx *= 0.92f
            p.vy *= 0.92f
            p.life--
            if (p.life <= 0) it.remove()
        }
    }

    fun draw(canvas: Canvas, sharedHue: Float) {
        dotPaint.color = Color.HSVToColor(70, floatArrayOf(sharedHue, 0.65f, 0.95f))
        for (p in active) canvas.drawCircle(p.x, p.y, 4.8f, dotPaint)
        for (p in expiring) canvas.drawCircle(p.x, p.y, 4.8f, dotPaint)

        dotPaint.color = Color.HSVToColor(255, floatArrayOf(sharedHue, 0.55f, 1f))
        for (p in active) canvas.drawCircle(p.x, p.y, 1.5f, dotPaint)
        for (p in expiring) canvas.drawCircle(p.x, p.y, 1.5f, dotPaint)
    }
}
