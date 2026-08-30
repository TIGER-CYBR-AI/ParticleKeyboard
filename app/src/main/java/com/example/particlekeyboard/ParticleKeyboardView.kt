package com.example.particlekeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin

interface KeyListener {
    fun onChar(c: String)
    fun onBackspace()
    fun onSpace()
    fun onEnter()
}

class ParticleKeyboardView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var keyListener: KeyListener? = null

    private var startTime = System.nanoTime()
    private lateinit var stripField: ParticleField
    private lateinit var bgField: ParticleField
    private var initialized = false

    private val currentWord = StringBuilder()

    private val stripHeightDp = 56f
    private var stripHeightPx = 0f

    // Sample Arabic QWERTY-ish layout — swap these rows for whatever layout
    // (Arabic, English, numbers...) you want, the rest of the code doesn't change.
    private val rows = listOf(
        listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج"),
        listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"),
        listOf("ئ", "ء", "ؤ", "ر", "لا", "ى", "ة", "و", "ز", "ظ")
    )

    // rect, label, action ("char" | "backspace" | "space" | "enter")
    private val keyRects = mutableListOf<Triple<RectF, String, String>>()

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 28, 28, 32)
    }
    private val dividerPaint = Paint().apply { color = Color.argb(40, 255, 255, 255) }
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 42f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        stripHeightPx = stripHeightDp * resources.displayMetrics.density

        val stripBounds = RectF(0f, 0f, w.toFloat(), stripHeightPx)
        val bgBounds = RectF(0f, stripHeightPx, w.toFloat(), h.toFloat())

        if (!initialized) {
            stripField = ParticleField(stripBounds, particleCount = 260, textPoolSize = 160, hasGap = true)
            bgField = ParticleField(bgBounds, particleCount = 220, textPoolSize = 0, hasGap = false)
            initialized = true
        } else {
            stripField.setBounds(stripBounds)
            bgField.setBounds(bgBounds)
        }
        layoutKeys(bgBounds)
    }

    private fun layoutKeys(area: RectF) {
        keyRects.clear()
        val rowCount = rows.size + 1 // +1 for the space/backspace/enter row
        val rowHeight = area.height() / (rowCount + 0.4f)
        var y = area.top + area.height() * 0.08f

        for (row in rows) {
            val keyWidth = area.width() / row.size
            var x = area.left
            for (label in row) {
                keyRects.add(Triple(RectF(x + 4, y + 4, x + keyWidth - 4, y + rowHeight - 4), label, "char"))
                x += keyWidth
            }
            y += rowHeight
        }
        val bw = area.width() / 3f
        keyRects.add(Triple(RectF(area.left + 4, y + 4, area.left + bw - 4, y + rowHeight - 4), "⌫", "backspace"))
        keyRects.add(Triple(RectF(area.left + bw + 4, y + 4, area.left + bw * 2 - 4, y + rowHeight - 4), "مسافة", "space"))
        keyRects.add(Triple(RectF(area.left + bw * 2 + 4, y + 4, area.right - 4, y + rowHeight - 4), "⏎", "enter"))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            for ((rect, label, action) in keyRects) {
                if (rect.contains(event.x, event.y)) {
                    handleKey(label, action)
                    return true
                }
            }
        }
        return true
    }

    private fun handleKey(label: String, action: String) {
        when (action) {
            "char" -> {
                currentWord.append(label)
                keyListener?.onChar(label)
                stripField.applyText(currentWord.toString())
            }
            "backspace" -> {
                if (currentWord.isNotEmpty()) currentWord.deleteCharAt(currentWord.length - 1)
                keyListener?.onBackspace()
                if (currentWord.isEmpty()) stripField.clearText() else stripField.applyText(currentWord.toString())
            }
            "space" -> {
                keyListener?.onSpace()
                currentWord.clear()
                stripField.clearText()
            }
            "enter" -> {
                keyListener?.onEnter()
                currentWord.clear()
                stripField.clearText()
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val t = (System.nanoTime() - startTime) / 1_000_000_000f
        // ONE shared color for the entire keyboard (strip + background together),
        // oscillating smoothly — never a per-particle rainbow.
        val sharedHue = 260f + sin(t * 0.15f) * 70f

        canvas.drawColor(Color.rgb(5, 5, 6))

        stripField.update(t)
        stripField.draw(canvas, sharedHue)
        bgField.update(t)
        bgField.draw(canvas, sharedHue)

        canvas.drawRect(0f, stripHeightPx - 2f, width.toFloat(), stripHeightPx, dividerPaint)

        for ((rect, label, _) in keyRects) {
            canvas.drawRoundRect(rect, 12f, 12f, keyPaint)
            canvas.drawText(label, rect.centerX(), rect.centerY() + keyTextPaint.textSize / 3, keyTextPaint)
        }

        postInvalidateOnAnimation()
    }
}
