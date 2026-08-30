package com.example.particlekeyboard

import android.inputmethodservice.InputMethodService
import android.view.View

class ParticleImeService : InputMethodService() {

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
