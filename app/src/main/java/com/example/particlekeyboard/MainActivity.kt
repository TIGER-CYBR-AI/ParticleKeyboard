package com.example.particlekeyboard

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.app.Activity

/**
 * Android does not allow any keyboard app — ours included, same as
 * Gboard or SwiftKey — to enable itself automatically. The user always
 * has to approve it once in Settings; that's a deliberate Android security
 * rule, not something an app can skip. This screen makes that one-time
 * step as short as possible: two big buttons that jump straight to the
 * right place instead of making the user hunt through menus.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 8, 12))
            setPadding(48, 96, 48, 48)
        }

        fun title(text: String, size: Float) = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        fun body(text: String) = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.argb(230, 230, 230, 240))
            gravity = Gravity.RIGHT
            setPadding(0, 8, 0, 32)
        }

        fun stepButton(text: String, onClick: () -> Unit) = Button(this).apply {
            this.text = text
            textSize = 17f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(120, 60, 200))
            setPadding(32, 40, 32, 40)
            setOnClickListener { onClick() }
        }

        root.addView(title("⌨️ Tiger Keyboard", 26f))
        root.addView(title("لوحة مفاتيح الذرات", 18f))

        root.addView(body("لتفعيل اللوحة، خطوتين بسيطتين بس — دوس الزر وراح تروح مباشرة للمكان الصحيح:"))

        root.addView(title("الخطوة 1", 16f))
        root.addView(body("فعّل اللوحة من قائمة لوحات المفاتيح (مرة وحدة بس، هاد شرط أندرويد لكل تطبيقات الكيبورد)"))
        root.addView(stepButton("فعّل اللوحة") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48)
        }
        root.addView(spacer)

        root.addView(title("الخطوة 2", 16f))
        root.addView(body("بعد ما تفعّلها، دوس هون لتختارها كلوحة الكتابة الحالية"))
        root.addView(stepButton("اختار Tiger Keyboard") {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }
}
