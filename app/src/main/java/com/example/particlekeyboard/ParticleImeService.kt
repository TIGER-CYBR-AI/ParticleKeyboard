package com.example.particlekeyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo

class ParticleImeService : InputMethodService() {

    // Keyboard must never take over the whole screen — the app's own
    // screen (the text field you're typing into) has to stay visible.
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
                // Real fix for "press Enter and it just adds a line instead
                // of sending": ask the app what it actually wants Enter to
                // do. Chat apps (WhatsApp, Telegram...) register a specific
                // action like "Send" on their input field — we now trigger
                // THAT action directly, exactly like the app's own send
                // button would. Only plain multi-line fields (that don't
                // request a specific action) get a literal newline.
                val info = currentInputEditorInfo
                val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                val noEnterFlag = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION
                if (info != null && action != null && action != EditorInfo.IME_ACTION_NONE && noEnterFlag == 0) {
                    currentInputConnection?.performEditorAction(action)
                } else {
                    currentInputConnection?.commitText("\n", 1)
                }
            }
        }
        return view
    }
}
