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

private enum class Page { AR, EN, NUM, SYMBOLS, EMOJI }

class ParticleKeyboardView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var keyListener: KeyListener? = null

    private var startTime = System.nanoTime()
    private lateinit var stripField: ParticleField
    private lateinit var bgField: ParticleField
    private var initialized = false
    private var lastBgBounds = RectF()
    private var charAreaRect = RectF()

    private val currentWord = StringBuilder()

    private val stripHeightDp = 56f
    private var stripHeightPx = 0f

    private var currentPage = Page.AR
    private var lastLetterPage = Page.AR
    private var shiftOn = false

    // ---------- scrolling (emoji page only) ----------
    private var emojiScrollPx = 0f
    private var maxEmojiScroll = 0f
    private var downX = 0f
    private var downY = 0f
    private var isDragging = false
    private val touchSlopPx = 14f

    // ---------- Character sets ----------
    // Arabic — full 28-letter alphabet plus hamza forms (ئ ء ؤ ى), taa
    // marbuta (ة) and the lam-alif ligature (لا). Checked against the
    // standard alphabet to make sure nothing (like د / ذ) is missing.
    private val arRows: List<List<KeyDef>> = listOf(
        listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج").map { KeyDef(it, "char") },
        listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط", "ذ").map { KeyDef(it, "char") },
        listOf("ئ", "ء", "ؤ", "ر", "د", "لا", "ى", "ة", "و", "ز", "ظ").map { KeyDef(it, "char") }
    )
    // English — full 26-letter alphabet + shift for caps + apostrophe
    // (the rest of English punctuation lives on the dedicated Symbols page).
    private val enRows: List<List<KeyDef>> = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").map { KeyDef(it, "char") },
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l").map { KeyDef(it, "char") },
        listOf(KeyDef("⇧", "shift")) + listOf("z", "x", "c", "v", "b", "n", "m", "'").map { KeyDef(it, "char") }
    )
    // Numbers — digits plus the symbols people actually pair with numbers
    // (math operators, currency, basic punctuation for phone/date/time entry).
    private val numRows: List<List<KeyDef>> = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { KeyDef(it, "char") },
        listOf("+", "-", "×", "÷", "=", "%", "°", "#", "@", "&").map { KeyDef(it, "char") },
        listOf("(", ")", ".", ",", ":", ";", "$", "€", "£", "¥").map { KeyDef(it, "char") }
    )
    // Symbols — its own dedicated page: quotes, brackets, and the
    // punctuation/operator characters programmers and everyday users need
    // that don't fit on the numbers page.
    private val symbolRows: List<List<KeyDef>> = listOf(
        listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")").map { KeyDef(it, "char") },
        listOf("-", "_", "=", "+", "[", "]", "{", "}", "\\", "|").map { KeyDef(it, "char") },
        listOf(";", ":", "'", "\"", ",", ".", "?", "/", "~", "`").map { KeyDef(it, "char") }
    )
    // Emoji — a broad practical set across categories (faces, gestures,
    // hearts, nature/objects). Scroll up/down inside the emoji area to see
    // the rest — it doesn't all fit on one screen, exactly like a real
    // emoji picker.
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
        Page.SYMBOLS -> symbolRows
        Page.EMOJI -> emojiRows
    }

    // rect, definition — recomputed by layoutKeys() whenever the page or
    // emoji scroll position changes
    private val keyRects = mutableListOf<Pair<RectF, KeyDef>>()

    // fixed, realistic key height — keys never stretch to fill the whole
    // remaining screen, and the char area is always exactly 3 rows tall
    // for every page, so the keyboard's total height never jumps around.
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
        textSize = 34f
    }
    private val smallKeyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 22f
    }
    private val dividerPaint = Paint().apply { color = Color.argb(50, 255, 255, 255) }

    // "frosted glass" look: soft glow behind the key + translucent
    // glass fill with a top-to-bottom sheen + a bright thin border.
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val activeKeyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // A real keyboard must NOT take the whole screen — only enough
        // height for the strip + its key rows, so the app's own screen
        // (the text field you're typing into) stays visible above it.
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
            stripField = ParticleField(stripBounds, particleCount = 260, textPoolSize = 160, hasGap = true)
            bgField = ParticleField(bgBounds, particleCount = 240, textPoolSize = 0, hasGap = false)
            initialized = true
        } else {
            stripField.setBounds(stripBounds)
            bgField.setBounds(bgBounds)
        }
        layoutKeys(bgBounds)
    }

    private fun layoutKeys(area: RectF) {
        keyRects.clear()
        val keyboardHeight = visibleCharRows * keyHeightPx + bottomBarHeightPx
        // anchor the keyboard to the BOTTOM of the available area, like a
        // real keyboard — everything above it stays open particle background
        val keyboardTop = area.bottom - keyboardHeight
        charAreaRect = RectF(area.left, keyboardTop, area.right, keyboardTop + visibleCharRows * keyHeightPx)
        var y = keyboardTop

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
                    keyRects.add(RectF(x + keyGapPx, rowY + keyGapPx, x + keyWidth - keyGapPx, rowY + keyHeightPx - keyGapPx) to def)
                    x += keyWidth
                }
            }
        } else {
            val rows = rowsForCurrentPage()
            for (row in rows) {
                val keyWidth = area.width() / row.size
                var x = area.left
                for (def in row) {
                    keyRects.add(RectF(x + keyGapPx, y + keyGapPx, x + keyWidth - keyGapPx, y + keyHeightPx - keyGapPx) to def)
                    x += keyWidth
                }
                y += keyHeightPx
            }
        }

        // bottom control row: [globe][123/ABC][#+=/ABC][emoji/ABC][backspace][space][enter]
        y = keyboardTop + visibleCharRows * keyHeightPx
        val small = area.width() * 0.105f
        var x = area.left
        val numLabel = if (currentPage == Page.NUM) "ABC" else "123"
        val symLabel = if (currentPage == Page.SYMBOLS) "ABC" else "#+="
        val emojiLabel = if (currentPage == Page.EMOJI) "ABC" else "🙂"

        fun addSmall(label: String, action: String) {
            keyRects.add(RectF(x + keyGapPx, y + keyGapPx, x + small - keyGapPx, y + bottomBarHeightPx - keyGapPx) to KeyDef(label, action))
            x += small
        }

        addSmall("🌐", "lang_toggle")
        addSmall(numLabel, "toggle_num")
        addSmall(symLabel, "toggle_symbols")
        addSmall(emojiLabel, "toggle_emoji")
        addSmall("⌫", "backspace")

        val remaining = area.width() - small * 5
        val enterWidth = remaining * 0.32f
        val spaceWidth = remaining - enterWidth

        keyRects.add(RectF(x + keyGapPx, y + keyGapPx, x + spaceWidth - keyGapPx, y + bottomBarHeightPx - keyGapPx) to KeyDef("مسافة", "space"))
        x += spaceWidth
        keyRects.add(RectF(x + keyGapPx, y + keyGapPx, area.right - keyGapPx, y + bottomBarHeightPx - keyGapPx) to KeyDef("⏎", "enter"))
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
                stripField.applyText(currentWord.toString())
            }
            "shift" -> {
                shiftOn = !shiftOn
                invalidate()
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
                // "send": particles explode away from the word, the text
                // exits normally into the host app, and particles return to
                // their natural up/down flow.
                keyListener?.onEnter()
                currentWord.clear()
                stripField.clearText()
            }
            "lang_toggle" -> {
                switchPage(if (lastLetterPage == Page.AR) Page.EN else Page.AR)
            }
            "toggle_num" -> {
                switchPage(if (currentPage == Page.NUM) lastLetterPage else Page.NUM)
            }
            "toggle_symbols" -> {
                switchPage(if (currentPage == Page.SYMBOLS) lastLetterPage else Page.SYMBOLS)
            }
            "toggle_emoji" -> {
                switchPage(if (currentPage == Page.EMOJI) lastLetterPage else Page.EMOJI)
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val t = (System.nanoTime() - startTime) / 1_000_000_000f
        // ONE shared color for the entire keyboard (strip + background +
        // keys together), oscillating smoothly — never a per-particle rainbow.
        val sharedHue = 260f + sin(t * 0.15f) * 70f

        canvas.drawColor(Color.rgb(4, 4, 6))

        stripField.update(t)
        stripField.draw(canvas, sharedHue)
        bgField.update(t)
        bgField.draw(canvas, sharedHue)

        canvas.drawRect(0f, stripHeightPx - 2f, width.toFloat(), stripHeightPx, dividerPaint)

        val accentColor = Color.HSVToColor(255, floatArrayOf(sharedHue, 0.65f, 1f))
        glowPaint.color = Color.HSVToColor(90, floatArrayOf(sharedHue, 0.6f, 1f))
        glassStrokePaint.color = Color.argb(150, 255, 255, 255)
        activeKeyFillPaint.color = Color.HSVToColor(140, floatArrayOf(sharedHue, 0.7f, 0.9f))

        canvas.save()
        canvas.clipRect(charAreaRect.left, charAreaRect.top, charAreaRect.right, charAreaRect.bottom + bottomBarHeightPx)

        for ((rect, def) in keyRects) {
            canvas.drawRoundRect(
                RectF(rect.left - 3f, rect.top - 3f, rect.right + 3f, rect.bottom + 3f),
                16f, 16f, glowPaint
            )

            glassFillPaint.shader = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                Color.argb(55, 255, 255, 255),
                Color.argb(18, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
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
