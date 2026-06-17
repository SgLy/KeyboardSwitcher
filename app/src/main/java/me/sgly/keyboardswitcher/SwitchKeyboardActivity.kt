package me.sgly.keyboardswitcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.File

enum class SwitchState {
    Init, SwitchCalled, Switching, SwitchDone
}

private const val REQUEST_CODE = 3434
private const val ACTION_QS_TILE_PREFERENCES = "android.service.quicksettings.action.QS_TILE_PREFERENCES"
private const val LAST_IME_FILE = "last_ime.txt"
private const val TAG = "KeyboardSwitcher"

class SwitchKeyboardActivity : Activity() {
    private var switchState = SwitchState.Init

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate action=${intent.action}")

        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val imeLabelMap = queryImeLabelMap()

        if (ACTION_QS_TILE_PREFERENCES == intent.action) {
            showKeyboardListDialog(inputMethodManager, imeLabelMap)
            return
        }

        val imeIds = imeLabelMap.keys.toList()
        Log.d(TAG, "imeIds count=${imeIds.size}")

        this.tryObtainSystemPermission(Manifest.permission.WRITE_SECURE_SETTINGS) { hasPermission ->
            Log.d(TAG, "hasPermission=$hasPermission")
            if (!hasPermission) {
                fallbackToPicker(inputMethodManager)
                return@tryObtainSystemPermission
            }

            val currentId = inputMethodManager.currentInputMethodInfo?.id
            if (currentId == null) {
                fallbackToPicker(inputMethodManager)
                return@tryObtainSystemPermission
            }

            val targetId = resolveTargetId(currentId, imeIds)
            if (targetId.isEmpty()) {
                fallbackToPicker(inputMethodManager)
                return@tryObtainSystemPermission
            }

            performSwitch(inputMethodManager, targetId, currentId, imeLabelMap)
        }
    }

    // ═══ 核心业务 ═══

    /** 查询系统所有输入法 → {id: label} */
    private fun queryImeLabelMap(): Map<String, String> {
        val queryIntent = Intent("android.view.InputMethod")
        return packageManager.queryIntentServices(queryIntent, PackageManager.GET_META_DATA).associate { resolveInfo ->
            val imeId = ComponentName(resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name).flattenToShortString()
            val label = resolveInfo.loadLabel(packageManager).toString()
            imeId to label
        }
    }

    /** 根据 last_ime 持久化记录，决定下一个切换目标 */
    private fun resolveTargetId(currentId: String, imeIds: List<String>): String {
        val lastId = lastImeFile()
            .takeIf { it.exists() }
            ?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        Log.d(TAG, "lastId=$lastId")

        if (lastId != null && lastId != currentId) {
            return lastId
        }

        val currentIndex = imeIds.indexOf(currentId)
        if (currentIndex < 0 || imeIds.isEmpty()) return ""

        val nextIndex = (currentIndex + 1) % imeIds.size
        val nextId = imeIds[nextIndex]
        Log.d(TAG, "currentIndex=$currentIndex nextId=$nextId")
        return nextId
    }

    /** 保存 fromId、写入系统设置、弹出 toast、关闭 Activity */
    private fun performSwitch(inputMethodManager: InputMethodManager, targetId: String, fromId: String?, imeLabelMap: Map<String, String>) {
        if (fromId != null && fromId != targetId) {
            lastImeFile().writeText(fromId)
            Log.d(TAG, "save lastId=$fromId")
        }

        Log.d(TAG, "switch to $targetId")
        Settings.Secure.putString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, targetId)

        val fromName = inputMethodManager.currentInputMethodInfo?.loadLabel(packageManager)
        val toName = imeLabelMap[targetId] ?: targetId
        Toast.makeText(applicationContext, "$fromName -> $toName", Toast.LENGTH_LONG).show()
        finish()
    }

    /** 无权限或异常时降级：弹出系统输入法选择器 */
    private fun fallbackToPicker(inputMethodManager: InputMethodManager) {
        Log.d(TAG, "fallback to picker")
        Handler(Looper.getMainLooper()).postDelayed({
            inputMethodManager.showInputMethodPicker()
            switchState = SwitchState.SwitchCalled
        }, 500)
    }

    // ═══ 长按列表 ═══

    private fun showKeyboardListDialog(inputMethodManager: InputMethodManager, imeLabelMap: Map<String, String>) {
        val currentId = inputMethodManager.currentInputMethodInfo?.id
        val imeEntries = imeLabelMap.entries.map { (imeId, label) ->
            Triple(imeId, label, imeId == currentId)
        }

        val displayLabels = imeEntries.map { (_, label, isCurrent) ->
            if (isCurrent) "✓ $label" else "   $label"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择输入法")
            .setItems(displayLabels) { dialog, which ->
                dialog.dismiss()
                val selectedId = imeEntries[which].first
                Log.d(TAG, "list selected: $selectedId")
                switchTo(inputMethodManager, selectedId, imeLabelMap)
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun switchTo(inputMethodManager: InputMethodManager, targetId: String, imeLabelMap: Map<String, String>) {
        this.tryObtainSystemPermission(Manifest.permission.WRITE_SECURE_SETTINGS) { hasPermission ->
            if (hasPermission) {
                performSwitch(inputMethodManager, targetId, inputMethodManager.currentInputMethodInfo?.id, imeLabelMap)
            } else {
                inputMethodManager.showInputMethodPicker()
                finish()
            }
        }
    }

    // ═══ 生命周期 ═══

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(TAG, "onWindowFocusChanged $hasFocus $switchState")

        if (switchState == SwitchState.SwitchCalled && !hasFocus) {
            switchState = SwitchState.Switching
            return
        }
        if (switchState == SwitchState.Switching && hasFocus) {
            switchState = SwitchState.SwitchDone
            finish()
        }
    }

    // ═══ Shizuku 权限 ═══

    private fun lastImeFile(): File = File(filesDir, LAST_IME_FILE)

    private fun tryObtainSystemPermission(permission: String, callback: (Boolean) -> Unit) {
        if (applicationContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            callback(true)
            return
        }
        this.requestShizukuPermission { isShizukuReady ->
            Log.d(TAG, "isShizukuReady=$isShizukuReady")
            if (!isShizukuReady) {
                callback(false)
            } else try {
                Log.d(TAG, "granting $permission via Shizuku")
                val packageName = getPackageName()
                val command = "pm grant $packageName $permission"
                @Suppress("DEPRECATION")
                val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                process.waitFor()
                Log.d(TAG, "grant done")
                callback(true)
            } catch (e: Exception) {
                Log.d(TAG, "grant error: ${e.message}")
                callback(false)
            }
        }
    }

    var listener: Shizuku.OnRequestPermissionResultListener? = null
    private fun requestShizukuPermission(callback: (Boolean) -> Unit) {
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
                if (requestCode == REQUEST_CODE) {
                    Shizuku.removeRequestPermissionResultListener(listener!!)
                    listener = null
                    callback(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener!!)
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }
}
