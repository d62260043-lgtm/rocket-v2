package com.rocket.controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Base64
import java.io.ByteArrayOutputStream

class RocketAccessibilityService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())

    companion object {
        var instance: RocketAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun captureBitmap(callback: (Bitmap?) -> Unit) {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    var bitmap: Bitmap? = null
                    try {
                        bitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Throwable) {
                        bitmap = null
                    } finally {
                        result.hardwareBuffer.close()
                    }
                    callback(bitmap)
                }
                override fun onFailure(errorCode: Int) { callback(null) }
            }
        )
    }

    fun captureToBase64(callback: (String?) -> Unit) {
        captureBitmap { bitmap ->
            if (bitmap == null) { callback(null); return@captureBitmap }
            try {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
                val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                callback(b64)
            } catch (t: Throwable) {
                callback(null)
            } finally {
                bitmap.recycle()
            }
        }
    }

    fun tap(x: Float, y: Float, durationMs: Long, callback: (Boolean) -> Unit) {
        dispatch(Path().apply { moveTo(x, y) }, durationMs, callback)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long, callback: (Boolean) -> Unit) {
        dispatch(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, durationMs.coerceIn(50, 10_000), callback)
    }

    private fun dispatch(path: Path, durationMs: Long, callback: (Boolean) -> Unit) {
        main.post {
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            val accepted = dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) { callback(true) }
                    override fun onCancelled(g: GestureDescription?) { callback(false) }
                },
                null
            )
            if (!accepted) callback(false)
        }
    }

    fun inputText(text: String, callback: (Boolean) -> Unit) {
        main.post {
            val root = rootInActiveWindow
            val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused == null) { callback(false); return@post }
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            callback(focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
        }
    }

    fun globalAction(action: Int): Boolean = performGlobalAction(action)
}
