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
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import com.ironai.assistant.services.IronAccessibilityService

/**
 * Executes comprehensive low-level hardware and system commands natively on the phone.
 * Operates 100% offline with zero cloud dependency.
 * Highly optimized for low-end Android devices (2GB-4GB RAM).
 */
class DeviceController(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private var cameraId: String? = null
    private var isTorchOn: Boolean = false

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

    // ==========================================
    // 1. TOUCHLESS WAKE & SCREEN UNLOCK / LOCK
    // ==========================================
    @Suppress("DEPRECATION")
    fun wakeAndUnlockDevice(activity: Activity? = null) {
        try {
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "SiuAssistant:TouchlessWake"
            )
            wakeLock.acquire(3000)

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
            IronAccessibilityService.instance?.performSwipeUpToUnlock()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun lockScreen(): Boolean {
        return IronAccessibilityService.instance?.lockScreen() ?: false
    }

    // ==========================================
    // 2. FLASHLIGHT / TORCH
    // ==========================================
    fun setFlashlight(enabled: Boolean): Boolean {
        return try {
            cameraId?.let {
                cameraManager.setTorchMode(it, enabled)
                isTorchOn = enabled
                true
            } ?: false
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            false
        }
    }

    fun toggleFlashlight(): Boolean {
        return setFlashlight(!isTorchOn)
    }

    // ==========================================
    // 3. AUDIO & VOLUME CONTROLS
    // ==========================================
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

    fun adjustVolume(increase: Boolean): Int {
        return if (increase) increaseVolume() else decreaseVolume()
    }

    fun muteVolume() {
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            0,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun setVolume(percentage: Int): Int {
        val clamped = percentage.coerceIn(0, 100)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (clamped * max) / 100
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        return getVolumePercentage()
    }

    fun getVolumePercentage(): Int {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) (current * 100) / max else 0
    }

    // Ringer Mode (Silent / Vibrate / Normal)
    fun setRingerMode(mode: String): String {
        return try {
            when (mode.lowercase()) {
                "silent", "சைலன்ட்" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    "Silent"
                }
                "vibrate", "வைப்ரேட்" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    "Vibrate"
                }
                else -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    "Normal"
                }
            }
        } catch (e: Exception) {
            "Protected (DND permission needed)"
        }
    }

    // ==========================================
    // 4. DISPLAY & BRIGHTNESS CONTROLS
    // ==========================================
    fun setBrightness(percentage: Int): Boolean {
        val clamped = percentage.coerceIn(0, 100)
        val brightnessValue = (clamped * 255) / 100

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
            try {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightnessValue
                )
                true
            } catch (e: Exception) {
                false
            }
        } else {
            openDisplaySettings()
            false
        }
    }

    fun increaseBrightness(): Int {
        val current = getBrightnessPercentage()
        val next = (current + 25).coerceAtMost(100)
        setBrightness(next)
        return next
    }

    fun decreaseBrightness(): Int {
        val current = getBrightnessPercentage()
        val next = (current - 25).coerceAtLeast(10)
        setBrightness(next)
        return next
    }

    fun getBrightnessPercentage(): Int {
        return try {
            val brightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            (brightness * 100) / 255
        } catch (e: Exception) {
            50
        }
    }

    // ==========================================
    // 5. CALLS & SMS & WHATSAPP
    // ==========================================
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

    fun sendWhatsAppMessage(recipient: String, messageText: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(messageText)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppByName("whatsapp")
        }
    }

    // ==========================================
    // 6. MEDIA PLAYBACK CONTROLS
    // ==========================================
    fun playPauseMedia() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        audioManager.dispatchMediaKeyEvent(event)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    fun nextTrack() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
        audioManager.dispatchMediaKeyEvent(event)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    fun previousTrack() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        audioManager.dispatchMediaKeyEvent(event)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    fun stopMedia() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP)
        audioManager.dispatchMediaKeyEvent(event)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    // ==========================================
    // 7. TIMERS & ALARMS
    // ==========================================
    fun setTimer(seconds: Int, label: String = "SIU Timer"): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setAlarm(hour: Int, minute: Int, message: String = "SIU Alarm"): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openAlarms(): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openStopwatch(): Boolean {
        return launchAppByName("clock") || launchAppByName("stopwatch")
    }

    // ==========================================
    // 8. CAMERA & PHOTOS
    // ==========================================
    fun openCamera(): Boolean {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            launchAppByName("camera")
        }
    }

    fun takePhoto(): Boolean {
        return openCamera()
    }

    fun recordVideo(): Boolean {
        return try {
            val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // 9. APP LAUNCHER
    // ==========================================
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
            "spotify", "ஸ்பாடிஃபை" -> "com.spotify.music"
            "maps", "மேப்ஸ்" -> "com.google.android.apps.maps"
            "settings", "செட்டிங்ஸ்" -> "com.android.settings"
            "calculator", "கால்குலேட்டர்" -> "com.google.android.calculator"
            "clock", "அலாரம்" -> "com.google.android.deskclock"
            "gallery", "கேலரி", "photos", "போட்டோஸ்" -> "com.google.android.apps.photos"
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

    fun openAppByName(appName: String): Boolean = launchAppByName(appName)

    // ==========================================
    // 10. SYSTEM SETTINGS SHORTCUTS & PANELS
    // ==========================================
    fun openWifiSettings(): Boolean = launchSettingsIntent(Settings.ACTION_WIFI_SETTINGS)
    fun openBluetoothSettings(): Boolean = launchSettingsIntent(Settings.ACTION_BLUETOOTH_SETTINGS)
    fun openDisplaySettings(): Boolean = launchSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS)
    fun openSoundSettings(): Boolean = launchSettingsIntent(Settings.ACTION_SOUND_SETTINGS)
    fun openLocationSettings(): Boolean = launchSettingsIntent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    fun openBatterySettings(): Boolean = launchSettingsIntent(Intent.ACTION_POWER_USAGE_SUMMARY)
    fun openAirplaneModeSettings(): Boolean = launchSettingsIntent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
    fun openApplicationSettings(): Boolean = launchSettingsIntent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)
    fun openDeviceSettings(): Boolean = launchSettingsIntent(Settings.ACTION_SETTINGS)
    fun openHotspotSettings(): Boolean = launchSettingsIntent("android.settings.TETHER_SETTINGS")

    private fun launchSettingsIntent(action: String): Boolean {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            openDeviceSettings()
        }
    }

    // ==========================================
    // 11. NAVIGATION & SYSTEM UI
    // ==========================================
    fun goHome(): Boolean = IronAccessibilityService.instance?.goHome() ?: false
    fun goBack(): Boolean = IronAccessibilityService.instance?.goBack() ?: false
    fun openNotifications(): Boolean = IronAccessibilityService.instance?.openNotifications() ?: false
    fun openQuickSettings(): Boolean = IronAccessibilityService.instance?.openQuickSettings() ?: false
    fun openRecentApps(): Boolean = IronAccessibilityService.instance?.openRecents() ?: false
    fun openPowerMenu(): Boolean = IronAccessibilityService.instance?.openPowerDialog() ?: false
    fun takeScreenshot(): Boolean = IronAccessibilityService.instance?.takeScreenshot() ?: false

    // ==========================================
    // 12. BATTERY & DIAGNOSTICS TELEMETRY
    // ==========================================
    fun getBatteryLevel(): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else 85
    }

    fun isCharging(): Boolean {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun getFreeInternalStorageMb(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
        } catch (e: Exception) {
            4096
        }
    }
}
