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
import rikka.shizuku.Shizuku

enum class SwitchState {
    Init, SwitchCalled, Switching, SwitchDone
}

val ids = arrayOf(
    "com.adamrocker.android.input.simeji/.OpenWnnSimeji",
    "com.tencent.wetype/.plugin.hld.WxHldService"
)

val REQUEST_CODE = 3434;

class SwitchKeyboardActivity : Activity() {
    private var switchState = SwitchState.Init

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("KeyboardSwitcher", "onCreate")

        val im = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        this.tryObtainSystemPermission(Manifest.permission.WRITE_SECURE_SETTINGS, { hasPermission ->
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
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed({
                    kotlin.run {
                        Log.d("KeyboardSwitcher", "call showInputMethodPicker $switchState")
                        im.showInputMethodPicker()
                        switchState = SwitchState.SwitchCalled
                    }
                }, 500)
            }
        })
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

    private fun tryObtainSystemPermission(permission: String, callback: (Boolean) -> Unit) {
        if (applicationContext.checkSelfPermission(permission) ==
                PackageManager.PERMISSION_GRANTED) {
            callback(true)
            return
        }
        this.requestShizukuPermission(REQUEST_CODE, { shizukuAvailable ->
            Log.d("KeyboardSwitcher", "shizukuAvailable: $shizukuAvailable")
            if (!shizukuAvailable) {
                callback(false)
            } else try {
                Log.d("KeyboardSwitcher", "Try granting direct switch permission by Shizuku")

                val packageName = getPackageName()
                val command = "pm grant $packageName ${permission}"
                val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                process.waitFor()

                Log.d("KeyboardSwitcher", "done Shizuku")

                callback(true)
            } catch (e: Exception) {
                Log.d("KeyboardSwitcher", "Error: ${e.message} ${e.stackTrace}")
                callback(false)
            }
        })
    }

    var listener: Shizuku.OnRequestPermissionResultListener? = null
    private fun requestShizukuPermission(code: Int, callback: (Boolean) -> Unit) {
        if (Shizuku.isPreV11()) {
            callback(false)
        } else if (!Shizuku.pingBinder()) {
            callback(false)
        } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            callback(true)
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            callback(false)
        } else {
            listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == code) {
                    Shizuku.removeRequestPermissionResultListener(listener!!)
                    listener = null
                    callback(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener!!)
            Shizuku.requestPermission(code)
        }
    }
}
