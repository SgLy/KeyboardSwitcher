package me.sgly.keyboardswitcher

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast


enum class SwitchState {
    Init, SwitchCalled, Switching, SwitchDone
}

val ids = arrayOf(
    "com.adamrocker.android.input.simeji/.OpenWnnSimeji",
    "com.tencent.wetype/.plugin.hld.WxHldService"
)

class SwitchKeyboardActivity : Activity() {
    private var switchState = SwitchState.Init

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("KeyboardSwitcher", "onCreate")

        val hasPermission = applicationContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            kotlin.run {
                val im = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                if (hasPermission) {
                    val currentId = im.currentInputMethodInfo?.id
                    val currentIndex = ids.indexOf(currentId)
                    val nextIndex = (currentIndex + 1) % ids.size
                    val nextId = ids[nextIndex]
                    Settings.Secure.putString(
                        getContentResolver(),
                        Settings.Secure.DEFAULT_INPUT_METHOD,
                        nextId
                    )
                    Toast.makeText(applicationContext, "Switched to $nextId", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Log.d("KeyboardSwitcher", "call showInputMethodPicker $switchState")
                    im.showInputMethodPicker()
                    switchState = SwitchState.SwitchCalled
                }
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
