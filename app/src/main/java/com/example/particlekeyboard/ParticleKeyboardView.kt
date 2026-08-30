package com.example.particlekeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

interface KeyListener {
    fun onChar(c: String)
    fun onBackspace()
    fun onSpace()
    fun onEnter()
}

private data class KeyDef(val label: String, val action: String)
private data class RenderKey(val rect: RectF, val def: KeyDef, val glassShader: Shader)

private enum class Page { AR, EN, NUM, SYMBOLS, EMOJI }

class ParticleKeyboardView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var keyListener: KeyListener? = null

    private var startTime = System.nanoTime()
    private lateinit var stripField: ParticleField
    private lateinit var bgField: ParticleField
    private lateinit var textSystem: TextParticleSystem
    private var initialized = false
    private var lastBgBounds = RectF()
    private var charAreaRect = RectF()

    private val currentWord = StringBuilder()

    // the text-preview strip is now big enough that the word forming out of
    // particles is actually READABLE, not just a blob of dots — with flowing
    // particles visible above AND below the word, same idea as the original
    // browser demo, just at a real, legible size.
    private val stripHeightDp = 150f
    private var stripHeightPx = 0f

    private var currentPage = Page.AR
    private var lastLetterPage = Page.AR
    private var shiftOn = false
    // shows what Enter will actually do in the current app field — lets you
    // SEE whether it's about to Send, Search, Go, Done, or just a plain
    // newline, instead of guessing.
    private var enterLabel = "⏎"

    fun setEnterLabel(label: String) {
        if (enterLabel == label) return
        enterLabel = label
        layoutKeys(lastBgBounds)
        invalidate()
    }

    // ---------- scrolling (emoji page only) ----------
    private var emojiScrollPx = 0f
    private var maxEmojiScroll = 0f
    private var downX = 0f
    private var downY = 0f
    private var isDragging = false
    private val touchSlopPx = 14f

    // ---------- Character sets ----------
    // Arabic — full 28-letter alphabet + hamza forms + backspace at the end
    // of the last row, in its standard keyboard position.
    private val arRows: List<List<KeyDef>> = listOf(
        listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج").map { KeyDef(it, "char") },
        listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط", "ذ").map { KeyDef(it, "char") },
        listOf("ئ", "ء", "ؤ", "ر", "د", "لا", "ى", "ة", "و", "ز", "ظ").map { KeyDef(it, "char") } + KeyDef("⌫", "backspace")
    )
    private val enRows: List<List<KeyDef>> = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").map { KeyDef(it, "char") },
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l").map { KeyDef(it, "char") },
        listOf(KeyDef("⇧", "shift")) + listOf("z", "x", "c", "v", "b", "n", "m", "'").map { KeyDef(it, "char") } + KeyDef("⌫", "backspace")
    )
    private val numRows: List<List<KeyDef>> = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { KeyDef(it, "char") },
        listOf("+", "-", "×", "÷", "=", "%", "°", "#", "@", "&").map { KeyDef(it, "char") },
        listOf("(", ")", ".", ",", ":", ";", "$", "€", "£", "¥").map { KeyDef(it, "char") } + KeyDef("⌫", "backspace")
    )
    // Arabic-specific symbols page: diacritics (tashkeel) + Arabic
    // punctuation — reached automatically when coming from the Arabic page.
    private val arSymbolRows: List<List<KeyDef>> = listOf(
        listOf("َ", "ً", "ُ", "ٌ", "ِ", "ٍ", "ّ", "ْ", "ـ").map { KeyDef(it, "char") },
        listOf("أ", "إ", "آ", "،", "؛", "؟", "«", "»", "٪", "(", ")").map { KeyDef(it, "char") },
        listOf("\"", "“", "”", "…", "–", "@", "#", "/").map { KeyDef(it, "char") } + KeyDef("⌫", "backspace")
    )
    // English-specific symbols page: general programming/punctuation set.
    private val enSymbolRows: List<List<KeyDef>> = listOf(
        listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")").map { KeyDef(it, "char") },
        listOf("-", "_", "=", "+", "[", "]", "{", "}", "\\", "|").map { KeyDef(it, "char") },
        listOf(";", ":", "'", "\"", ",", ".", "?", "/", "~", "`").map { KeyDef(it, "char") } + KeyDef("⌫", "backspace")
    )
    private val emojiRows: List<List<KeyDef>> = listOf(
        listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇").map { KeyDef(it, "char") },
        listOf("🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚").map { KeyDef(it, "char") },
        listOf("😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸").map { KeyDef(it, "char") },
        listOf("🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️").map { KeyDef(it, "char") },
        listOf("😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡").map { KeyDef(it, "char") },
        listOf("🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓").map { KeyDef(it, "char") },
        listOf("👍", "👎", "👏", "🙌", "👋", "🤝", "🙏", "💪", "✌️", "🤞").map { KeyDef(it, "char") },
        listOf("🤟", "🤙", "👌", "🤌", "👈", "👉", "👆", "👇", "✊", "👊").map { KeyDef(it, "char") },
        listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔").map { KeyDef(it, "char") },
        listOf("❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💯", "✨").map { KeyDef(it, "char") },
        listOf("🔥", "🌟", "⭐", "☀️", "🌙", "⚡", "🌈", "☁️", "🌊", "🎉").map { KeyDef(it, "char") },
        listOf("🎊", "🎁", "📌", "📍", "💡", "🔔", "🎵", "🎶", "📷", "📱").map { KeyDef(it, "char") }
    )

    private fun rowsForCurrentPage(): List<List<KeyDef>> = when (currentPage) {
        Page.AR -> arRows
        Page.EN -> enRows
        Page.NUM -> numRows
        Page.SYMBOLS -> if (lastLetterPage == Page.AR) arSymbolRows else enSymbolRows
        Page.EMOJI -> emojiRows
    }

    private val renderKeys = mutableListOf<RenderKey>()
    // separate hit-test list so EMOJI scrolling can reuse simple rect logic
    private val keyRects = mutableListOf<Pair<RectF, KeyDef>>()

    private val keyHeightDp = 46f
    private var keyHeightPx = 0f
    private val keyGapDp = 5f
    private var keyGapPx = 0f
    private val bottomBarHeightDp = 46f
    private var bottomBarHeightPx = 0f
    private val visibleCharRows = 3

    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 32f
    }
    private val smallKeyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 20f
    }
    private val dividerPaint = Paint().apply { color = Color.argb(50, 255, 255, 255) }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val activeKeyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val desiredHeightPx = ((stripHeightDp + visibleCharRows * keyHeightDp + bottomBarHeightDp) * density).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, desiredHeightPx)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val density = resources.displayMetrics.density
        stripHeightPx = stripHeightDp * density
        keyHeightPx = keyHeightDp * density
        keyGapPx = keyGapDp * density
        bottomBarHeightPx = bottomBarHeightDp * density

        val stripBounds = RectF(0f, 0f, w.toFloat(), stripHeightPx)
        val bgBounds = RectF(0f, stripHeightPx, w.toFloat(), h.toFloat())
        lastBgBounds = bgBounds

        if (!initialized) {
            // background is now just a soft glow with a handful of ambient
            // particles — the heavy lifting all happens in TextParticleSystem,
            // which only creates particles when actually needed.
            stripField = ParticleField(stripBounds, particleCount = 70, textPoolSize = 0, hasGap = true)
            bgField = ParticleField(bgBounds, particleCount = 45, textPoolSize = 0, hasGap = false)
            textSystem = TextParticleSystem(stripBounds)
            initialized = true
        } else {
            stripField.setBounds(stripBounds)
            bgField.setBounds(bgBounds)
            textSystem.setBounds(stripBounds)
        }
        layoutKeys(bgBounds)
    }

    private fun layoutKeys(area: RectF) {
        keyRects.clear()
        renderKeys.clear()
        val keyboardHeight = visibleCharRows * keyHeightPx + bottomBarHeightPx
        val keyboardTop = area.bottom - keyboardHeight
        charAreaRect = RectF(area.left, keyboardTop, area.right, keyboardTop + visibleCharRows * keyHeightPx)
        var y = keyboardTop

        fun addKey(rect: RectF, def: KeyDef) {
            keyRects.add(rect to def)
            val shader = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                Color.argb(55, 255, 255, 255),
                Color.argb(18, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            renderKeys.add(RenderKey(rect, def, shader))
        }

        if (currentPage == Page.EMOJI) {
            maxEmojiScroll = max(0f, emojiRows.size * keyHeightPx - charAreaRect.height())
            emojiScrollPx = emojiScrollPx.coerceIn(0f, maxEmojiScroll)
            val contentTop = charAreaRect.top - emojiScrollPx
            for ((rowIndex, row) in emojiRows.withIndex()) {
                val rowY = contentTop + rowIndex * keyHeightPx
                if (rowY + keyHeightPx < charAreaRect.top - keyHeightPx || rowY > charAreaRect.bottom) continue
                val keyWidth = area.width() / row.size
                var x = area.left
                for (def in row) {
                    addKey(RectF(x + keyGapPx, rowY + keyGapPx, x + keyWidth - keyGapPx, rowY + keyHeightPx - keyGapPx), def)
                    x += keyWidth
                }
            }
        } else {
            val rows = rowsForCurrentPage()
            for (row in rows) {
                val keyWidth = area.width() / row.size
                var x = area.left
                for (def in row) {
                    addKey(RectF(x + keyGapPx, y + keyGapPx, x + keyWidth - keyGapPx, y + keyHeightPx - keyGapPx), def)
                    x += keyWidth
                }
                y += keyHeightPx
            }
        }

        // bottom control row. Backspace lives at the end of the last letter
        // row for every page EXCEPT emoji (which scrolls, so it keeps its
        // own dedicated backspace here instead).
        y = keyboardTop + visibleCharRows * keyHeightPx
        val isEmoji = currentPage == Page.EMOJI
        val smallCount = if (isEmoji) 5 else 4
        val small = area.width() * 0.105f
        var x = area.left
        val numLabel = if (currentPage == Page.NUM) "ABC" else "123"
        val symLabel = if (currentPage == Page.SYMBOLS) "ABC" else "#+="
        val emojiLabel = if (currentPage == Page.EMOJI) "ABC" else "🙂"

        fun addSmall(label: String, action: String) {
            addKey(RectF(x + keyGapPx, y + keyGapPx, x + small - keyGapPx, y + bottomBarHeightPx - keyGapPx), KeyDef(label, action))
            x += small
        }

        addSmall("🌐", "lang_toggle")
        addSmall(numLabel, "toggle_num")
        addSmall(symLabel, "toggle_symbols")
        addSmall(emojiLabel, "toggle_emoji")
        if (isEmoji) addSmall("⌫", "backspace")

        val remaining = area.width() - small * smallCount
        val enterWidth = remaining * 0.32f
        val spaceWidth = remaining - enterWidth

        addKey(RectF(x + keyGapPx, y + keyGapPx, x + spaceWidth - keyGapPx, y + bottomBarHeightPx - keyGapPx), KeyDef("مسافة", "space"))
        x += spaceWidth
        addKey(RectF(x + keyGapPx, y + keyGapPx, area.right - keyGapPx, y + bottomBarHeightPx - keyGapPx), KeyDef(enterLabel, "enter"))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentPage == Page.EMOJI && charAreaRect.contains(downX, downY)) {
                    val dy = event.y - downY
                    if (!isDragging && abs(dy) > touchSlopPx) isDragging = true
                    if (isDragging) {
                        emojiScrollPx = (emojiScrollPx - dy).coerceIn(0f, maxEmojiScroll)
                        downY = event.y
                        layoutKeys(lastBgBounds)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    for ((rect, def) in keyRects) {
                        if (rect.contains(event.x, event.y)) {
                            handleKey(def)
                            break
                        }
                    }
                }
                isDragging = false
            }
        }
        return true
    }

    private fun switchPage(page: Page) {
        currentPage = page
        if (page == Page.AR || page == Page.EN) lastLetterPage = page
        if (page == Page.EMOJI) emojiScrollPx = 0f
        layoutKeys(lastBgBounds)
        invalidate()
    }

    private fun handleKey(def: KeyDef) {
        when (def.action) {
            "char" -> {
                val out = if (currentPage == Page.EN && shiftOn) def.label.uppercase() else def.label
                currentWord.append(out)
                keyListener?.onChar(out)
                textSystem.applyText(currentWord.toString())
            }
            "shift" -> {
                shiftOn = !shiftOn
                invalidate()
            }
            "backspace" -> {
                if (currentWord.isNotEmpty()) currentWord.deleteCharAt(currentWord.length - 1)
                keyListener?.onBackspace()
                // exploding only happens here once the text is FULLY empty —
                // removing one character just smoothly re-forms the rest.
                if (currentWord.isEmpty()) textSystem.clearAll() else textSystem.applyText(currentWord.toString())
            }
            "space" -> {
                // space no longer explodes anything — it just extends the
                // sentence so you can keep writing a full sentence, even a
                // long paragraph, without it resetting every word.
                keyListener?.onSpace()
                currentWord.append(" ")
                textSystem.applyText(currentWord.toString())
            }
            "enter" -> {
                // "send": the whole sentence explodes away, text goes out
                // to the app normally, particles return to their natural flow.
                keyListener?.onEnter()
                currentWord.clear()
                textSystem.clearAll()
            }
            "lang_toggle" -> switchPage(if (lastLetterPage == Page.AR) Page.EN else Page.AR)
            "toggle_num" -> switchPage(if (currentPage == Page.NUM) lastLetterPage else Page.NUM)
            "toggle_symbols" -> switchPage(if (currentPage == Page.SYMBOLS) lastLetterPage else Page.SYMBOLS)
            "toggle_emoji" -> switchPage(if (currentPage == Page.EMOJI) lastLetterPage else Page.EMOJI)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val t = (System.nanoTime() - startTime) / 1_000_000_000f
        val sharedHue = 260f + sin(t * 0.15f) * 70f

        canvas.drawColor(Color.rgb(4, 4, 6))

        stripField.update(t)
        stripField.draw(canvas, sharedHue)
        bgField.update(t)
        bgField.draw(canvas, sharedHue)
        textSystem.update()
        textSystem.draw(canvas, sharedHue)

        canvas.drawRect(0f, stripHeightPx - 2f, width.toFloat(), stripHeightPx, dividerPaint)

        val accentColor = Color.HSVToColor(255, floatArrayOf(sharedHue, 0.65f, 1f))
        glowPaint.color = Color.HSVToColor(90, floatArrayOf(sharedHue, 0.6f, 1f))
        glassStrokePaint.color = Color.argb(150, 255, 255, 255)
        activeKeyFillPaint.color = Color.HSVToColor(140, floatArrayOf(sharedHue, 0.7f, 0.9f))

        canvas.save()
        canvas.clipRect(charAreaRect.left, charAreaRect.top, charAreaRect.right, charAreaRect.bottom + bottomBarHeightPx)

        for (rk in renderKeys) {
            val rect = rk.rect
            val def = rk.def
            canvas.drawRoundRect(
                RectF(rect.left - 3f, rect.top - 3f, rect.right + 3f, rect.bottom + 3f),
                16f, 16f, glowPaint
            )

            // shader was precomputed once in layoutKeys() — NOT reallocated
            // every frame. This was the main cause of the lag/double-tap
            // issue: creating ~30 new gradient objects 60 times per second.
            glassFillPaint.shader = rk.glassShader
            canvas.drawRoundRect(rect, 14f, 14f, glassFillPaint)

            if (def.action == "shift" && shiftOn) {
                canvas.drawRoundRect(rect, 14f, 14f, activeKeyFillPaint)
            }

            canvas.drawRoundRect(rect, 14f, 14f, glassStrokePaint)

            val paint = if (def.label.length <= 2 && def.action == "char") keyTextPaint else smallKeyTextPaint
            paint.color = if (def.action != "char") accentColor else Color.WHITE
            val displayLabel = if (def.action == "char" && currentPage == Page.EN && shiftOn) def.label.uppercase() else def.label
            canvas.drawText(displayLabel, rect.centerX(), rect.centerY() + paint.textSize / 3, paint)
        }
        canvas.restore()

        postInvalidateOnAnimation()
    }
}
