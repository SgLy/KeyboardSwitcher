package me.sgly.keyboardswitcher

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.inputmethod.InputMethodManager

enum class SwitchState {
    Init, SwitchCalled, Switching, SwitchDone
}

class SwitchKeyboardActivity : Activity() {
    private var switchState = SwitchState.Init

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("KeyboardSwitcher", "onCreate")

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            kotlin.run {
                val im = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                Log.d("KeyboardSwitcher", "call showInputMethodPicker $switchState")
                im.showInputMethodPicker()
                switchState = SwitchState.SwitchCalled
            }
        }, 500)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d("KeyboardSwitcher", "onWindowFocusChanged $hasFocus $switchState")

        if (switchState == SwitchState.SwitchCalled && !hasFocus) {
            switchState = SwitchState.Switching
            return
        }
        if (switchState == SwitchState.Switching && hasFocus) {
            switchState = SwitchState.SwitchDone
            finish()
        }
    }
}
