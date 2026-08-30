package com.example.particlekeyboard

import android.inputmethodservice.InputMethodService
import android.view.View

class ParticleImeService : InputMethodService() {

    // Make sure the keyboard never expands to fullscreen mode on any device
    // (some phones do this automatically in landscape) — we want the app's
    // own screen to always stay visible above the keyboard.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val view = ParticleKeyboardView(this)
        view.keyListener = object : KeyListener {
            override fun onChar(c: String) {
                currentInputConnection?.commitText(c, 1)
            }
            override fun onBackspace() {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
            override fun onSpace() {
                currentInputConnection?.commitText(" ", 1)
            }
            override fun onEnter() {
                currentInputConnection?.commitText("\n", 1)
            }
        }
        return view
    }
}
