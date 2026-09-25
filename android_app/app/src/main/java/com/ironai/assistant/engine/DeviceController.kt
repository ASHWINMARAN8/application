package com.ironai.assistant.engine

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.telephony.SmsManager
import android.view.WindowManager
import android.widget.Toast
import com.ironai.assistant.services.IronAccessibilityService

/**
 * Executes low-level hardware and system commands natively on the phone.
 * Highly optimized for low-end Android devices (runs on 2GB-3GB RAM without lag).
 */
class DeviceController(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private var cameraId: String? = null

    init {
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                if (hasFlash) {
                    cameraId = id
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 1. Touchless Wake & Screen Unlock (Without Touching Phone)
    @Suppress("DEPRECATION")
    fun wakeAndUnlockDevice(activity: Activity? = null) {
        // Step 1: Wake screen from black/sleep state
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "IronAssistant:TouchlessWake"
        )
        wakeLock.acquire(3000) // Hold briefly to turn screen on

        // Step 2: Dismiss lock screen (Keyguard)
        if (activity != null) {
            activity.runOnUiThread {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    activity.setShowWhenLocked(true)
                    activity.setTurnScreenOn(true)
                    keyguardManager.requestDismissKeyguard(activity, null)
                } else {
                    activity.window.addFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                }
            }
        }

        // Step 3: Perform automated swipe-up gesture via AccessibilityService to clear lockscreen
        IronAccessibilityService.instance?.performSwipeUpToUnlock()
    }

    // 2. Lock Screen
    fun lockScreen(): Boolean {
        return IronAccessibilityService.instance?.performGlobalAction(
            AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
        ) ?: false
    }

    // 3. Flashlight Control
    fun setFlashlight(enabled: Boolean): Boolean {
        return try {
            cameraId?.let {
                cameraManager.setTorchMode(it, enabled)
                true
            } ?: false
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            false
        }
    }

    // 4. Audio & Volume Control
    fun increaseVolume(): Int {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
        return getVolumePercentage()
    }

    fun decreaseVolume(): Int {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
        return getVolumePercentage()
    }

    fun muteVolume() {
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            0,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun getVolumePercentage(): Int {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) (current * 100) / max else 0
    }

    // 5. Direct Phone Calls
    fun makePhoneCall(contactOrNumber: String) {
        val intent = if (contactOrNumber.all { it.isDigit() || it == '+' || it == ' ' }) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:${contactOrNumber.replace(" ", "")}"))
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("tel:$contactOrNumber"))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: SecurityException) {
            Toast.makeText(context, "Call permission required", Toast.LENGTH_SHORT).show()
        }
    }

    // 6. SMS Dispatcher
    fun sendSms(destinationAddress: String, messageText: String) {
        try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(destinationAddress, null, messageText, null, null)
            Toast.makeText(context, "Message sent to $destinationAddress", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 7. App Launcher
    fun launchAppByName(appName: String): Boolean {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(0)

        for (appInfo in packages) {
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.contains(appName, ignoreCase = true) || appName.contains(label, ignoreCase = true)) {
                val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
        }

        val fallbackPackage = when (appName.lowercase()) {
            "whatsapp", "வாட்ஸ்அப்" -> "com.whatsapp"
            "youtube", "யூடியூப்" -> "com.google.android.youtube"
            "camera", "கேமரா" -> "com.google.android.GoogleCamera"
            "chrome", "குரோம்" -> "com.android.chrome"
            else -> null
        }

        fallbackPackage?.let { pkg ->
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        }

        return false
    }

    // 8. Battery Telemetry
    fun getBatteryLevel(): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else 85
    }

    // 9. Navigation Actions
    fun takeScreenshot(): Boolean = IronAccessibilityService.instance?.performGlobalAction(
        AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
    ) ?: false

    fun goHome(): Boolean = IronAccessibilityService.instance?.performGlobalAction(
        AccessibilityService.GLOBAL_ACTION_HOME
    ) ?: false

    fun goBack(): Boolean = IronAccessibilityService.instance?.performGlobalAction(
        AccessibilityService.GLOBAL_ACTION_BACK
    ) ?: false

    fun openNotifications(): Boolean = IronAccessibilityService.instance?.performGlobalAction(
        AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
    ) ?: false
}
