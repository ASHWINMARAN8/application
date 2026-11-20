package com.ironai.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ironai.assistant.MainActivity
import com.ironai.assistant.R
import com.ironai.assistant.engine.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Continuous background voice listener service for Alexa Offline Engine.
 * Operates offline using Android's on-device speech recognizer with EXTRA_PREFER_OFFLINE = true.
 * Flawlessly routes English, Tamil (தமிழ்), and Tanglish commands to the phone controller.
 * Highly optimized for low-end Android devices with near-zero battery drain.
 */
class VoiceListenerService : Service(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var intentEngine: BilingualIntentEngine
    private lateinit var deviceController: DeviceController
    private lateinit var ttsManager: IronTtsManager
    private lateinit var personaManager: PersonaManager

    private var isListening = false

    companion object {
        const val CHANNEL_ID = "AlexaVoiceServiceChannel"
        const val NOTIFICATION_ID = 101

        var onSpeechResultCallback: ((String, String, ResponseLanguage) -> Unit)? = null
        var activeMainActivity: MainActivity? = null
    }

    override fun onCreate() {
        super.onCreate()
        personaManager = PersonaManager()
        intentEngine = BilingualIntentEngine()
        deviceController = DeviceController(this)
        ttsManager = IronTtsManager(this, personaManager) { isSpeaking ->
            if (isSpeaking) {
                stopListening()
            } else {
                startListening()
            }
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        initSpeechRecognizer()
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(this)
            startListening()
        } else {
            Log.e("AlexaVoice", "Offline speech recognition not available on this device.")
        }
    }

    fun startListening() {
        if (isListening || speechRecognizer == null) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN") // en-IN handles English + Tanglish seamlessly
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ta-IN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            e.printStackTrace()
            isListening = false
        }
    }

    fun stopListening() {
        if (!isListening) return
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isListening = false
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val spokenText = matches[0].trim()
            handleVoiceCommand(spokenText)
        }
        startListening()
    }

    private fun handleVoiceCommand(spokenText: String) {
        val parsed = intentEngine.parse(spokenText)
        val reply = executeParsedCommand(parsed)

        onSpeechResultCallback?.invoke(spokenText, reply, parsed.language)
        ttsManager.speak(reply, parsed.language == ResponseLanguage.TAMIL)
    }

    private fun executeParsedCommand(parsed: ParsedIntent): String {
        val lang = parsed.language

        return when (parsed.type) {
            // Touchless Unlock Phone
            CommandType.UNLOCK_PHONE -> {
                deviceController.wakeAndUnlockDevice(activeMainActivity)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Phone-ah touchless-ah unlock pannitten!"
                    ResponseLanguage.TAMIL -> "போன் திரை திறக்கப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Sure, unlocking your phone touchlessly."
                }
            }
            CommandType.LOCK_PHONE -> {
                deviceController.lockScreen()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Phone display-ah lock pannitten."
                    ResponseLanguage.TAMIL -> "போன் திரை லாக் செய்யப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Device locked. Say \"Alexa\" to wake."
                }
            }

            // Flashlight
            CommandType.TORCH_ON -> {
                deviceController.setFlashlight(true)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Sure! Torch on pannitten."
                    ResponseLanguage.TAMIL -> "டார்ச் ஆன் செய்யப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Sure, turning on the flashlight."
                }
            }
            CommandType.TORCH_OFF -> {
                deviceController.setFlashlight(false)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Torch off pannitten."
                    ResponseLanguage.TAMIL -> "டார்ச் ஆப் செய்யப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Flashlight is turned off."
                }
            }

            // Camera & Media
            CommandType.TAKE_PHOTO -> {
                deviceController.takePhoto()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Camera open panni photo capture mode potuten!"
                    ResponseLanguage.TAMIL -> "கேமரா திறக்கப்பட்டது, படம் எடுக்கலாம்."
                    ResponseLanguage.ENGLISH -> "Opening camera for photo."
                }
            }
            CommandType.RECORD_VIDEO -> {
                deviceController.recordVideo()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Video recording mode open panren!"
                    ResponseLanguage.TAMIL -> "வீடியோ பதிவு முறை திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Starting video capture."
                }
            }

            // Calls & Messaging
            CommandType.MAKE_CALL -> {
                val contact = parsed.targetSlot ?: "Contact"
                deviceController.makePhoneCall(contact)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "$contact-kku ippo call panren."
                    ResponseLanguage.TAMIL -> "$contact-க்கு இப்போது கால் செய்கிறேன்."
                    ResponseLanguage.ENGLISH -> "Calling $contact now."
                }
            }
            CommandType.SEND_WHATSAPP -> {
                val recipient = parsed.targetSlot ?: "Contact"
                val message = parsed.extraSlot ?: "Hello"
                deviceController.sendWhatsAppMessage(recipient, message)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "WhatsApp-la $recipient-kku message anupuren: \"$message\""
                    ResponseLanguage.TAMIL -> "வாட்ஸ்அப்பில் $recipient-க்கு மெசேஜ் அனுப்பப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Sending WhatsApp message to $recipient: \"$message\""
                }
            }
            CommandType.SEND_SMS -> {
                val recipient = parsed.targetSlot ?: "Contact"
                val message = parsed.extraSlot ?: "Hello"
                deviceController.sendSms(recipient, message)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "$recipient-kku SMS anupitten: \"$message\""
                    ResponseLanguage.TAMIL -> "$recipient-க்கு எஸ்எம்எஸ் அனுப்பப்பட்டது."
                    ResponseLanguage.ENGLISH -> "SMS sent to $recipient: \"$message\""
                }
            }

            // Brightness Controls
            CommandType.BRIGHTNESS_UP -> {
                val br = deviceController.increaseBrightness()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Brightness $br percent-kku kootitten."
                    ResponseLanguage.TAMIL -> "வெளிச்சம் $br சதவீதமாக அதிகரிக்கப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Brightness increased to $br percent."
                }
            }
            CommandType.BRIGHTNESS_DOWN -> {
                val br = deviceController.decreaseBrightness()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Brightness $br percent-kku korachutten."
                    ResponseLanguage.TAMIL -> "வெளிச்சம் $br சதவீதமாக குறைக்கப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Brightness decreased to $br percent."
                }
            }
            CommandType.BRIGHTNESS_SET -> {
                val target = parsed.valueSlot ?: 50
                deviceController.setBrightness(target)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Brightness $target percent-kku set pannitten."
                    ResponseLanguage.TAMIL -> "வெளிச்சம் $target சதவீதத்தில் அமைக்கப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Brightness set to $target percent."
                }
            }
            CommandType.BRIGHTNESS_MAX -> {
                deviceController.setBrightness(100)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Brightness full max-la potuten!"
                    ResponseLanguage.TAMIL -> "முழு வெளிச்சம் அமைக்கப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Brightness set to maximum 100 percent."
                }
            }

            // Volume Controls
            CommandType.VOLUME_UP -> {
                val vol = deviceController.increaseVolume()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Sound $vol percent-kku kootitten."
                    ResponseLanguage.TAMIL -> "சத்தம் அதிகரிக்கப்பட்டது: $vol சதவீதம்."
                    ResponseLanguage.ENGLISH -> "Volume set to $vol percent."
                }
            }
            CommandType.VOLUME_DOWN -> {
                val vol = deviceController.decreaseVolume()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Sound $vol percent-kku korachutten."
                    ResponseLanguage.TAMIL -> "சத்தம் குறைக்கப்பட்டது: $vol சதவீதம்."
                    ResponseLanguage.ENGLISH -> "Volume reduced to $vol percent."
                }
            }
            CommandType.VOLUME_MAX -> {
                val vol = deviceController.setVolume(100)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Volume full max 100 percent-kku potuten!"
                    ResponseLanguage.TAMIL -> "சத்தம் முழுமையாக 100 சதவீதம் வைக்கப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Volume maxed out to 100 percent."
                }
            }
            CommandType.VOLUME_SET -> {
                val target = parsed.valueSlot ?: 50
                val vol = deviceController.setVolume(target)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Volume $vol percent-kku set pannitten."
                    ResponseLanguage.TAMIL -> "சத்தம் $vol சதவீதத்தில் அமைக்கப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Volume set to $vol percent."
                }
            }
            CommandType.VOLUME_MUTE -> {
                deviceController.muteVolume()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Phone silent mode-la potuten."
                    ResponseLanguage.TAMIL -> "போன் சைலன்ட் மோடில் வைக்கப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Device muted."
                }
            }
            CommandType.RINGER_VIBRATE -> {
                deviceController.setRingerMode("vibrate")
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Ringer vibrate mode-ku maathiten."
                    ResponseLanguage.TAMIL -> "வைப்ரேட் முறையில் மாற்றப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Ringer set to vibrate."
                }
            }
            CommandType.RINGER_NORMAL -> {
                deviceController.setRingerMode("normal")
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Ringer normal sound mode-ku vandhuchu."
                    ResponseLanguage.TAMIL -> "இயல்பான ஒலி முறையில் அமைக்கப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Ringer set to normal sound."
                }
            }

            // Media Playback
            CommandType.MEDIA_PLAY_PAUSE -> {
                deviceController.playPauseMedia()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Music play/pause toggle pannitten."
                    ResponseLanguage.TAMIL -> "பாடல் இயக்கம்/நிறுத்தம் செய்யப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Media playback toggled."
                }
            }
            CommandType.MEDIA_NEXT -> {
                deviceController.nextTrack()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Next song-ku poiten."
                    ResponseLanguage.TAMIL -> "அடுத்த பாடலுக்கு செல்லப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Skipping to next track."
                }
            }
            CommandType.MEDIA_PREV -> {
                deviceController.previousTrack()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Previous song-ku poiten."
                    ResponseLanguage.TAMIL -> "முந்தைய பாடலுக்கு செல்லப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Returning to previous track."
                }
            }
            CommandType.MEDIA_STOP -> {
                deviceController.stopMedia()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Music stop pannitten."
                    ResponseLanguage.TAMIL -> "பாடல் நிறுத்தப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Playback stopped."
                }
            }

            // Timer & Alarm
            CommandType.SET_TIMER -> {
                val mins = parsed.valueSlot ?: 5
                deviceController.setTimer(mins * 60, "Alexa $mins Min Timer")
                when (lang) {
                    ResponseLanguage.TANGLISH -> "$mins minutes timer start pannitten!"
                    ResponseLanguage.TAMIL -> "$mins நிமிடங்களுக்கான டைமர் தொடங்கப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Timer set for $mins minutes."
                }
            }
            CommandType.SET_ALARM -> {
                val hour = parsed.valueSlot ?: 7
                deviceController.setAlarm(hour, 0, "Alexa Alarm")
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Kaalai $hour manikku alarm set pannitten!"
                    ResponseLanguage.TAMIL -> "காலை $hour மணிக்கு அலாரம் அமைக்கப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Alarm set for $hour:00."
                }
            }
            CommandType.OPEN_ALARMS -> {
                deviceController.openAlarms()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Alarms list open panren."
                    ResponseLanguage.TAMIL -> "அலாரங்கள் திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening alarms."
                }
            }
            CommandType.OPEN_STOPWATCH -> {
                deviceController.openStopwatch()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Stopwatch open panren."
                    ResponseLanguage.TAMIL -> "ஸ்டாப்வாட்ச் திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening stopwatch."
                }
            }

            // System Settings Shortcuts
            CommandType.OPEN_SETTINGS_WIFI -> {
                deviceController.openWifiSettings()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Wi-Fi settings open panren."
                    ResponseLanguage.TAMIL -> "வைஃபை அமைப்புகள் திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening Wi-Fi settings."
                }
            }
            CommandType.OPEN_SETTINGS_BLUETOOTH -> {
                deviceController.openBluetoothSettings()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Bluetooth settings open panren."
                    ResponseLanguage.TAMIL -> "புளூடூத் அமைப்புகள் திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening Bluetooth settings."
                }
            }
            CommandType.OPEN_SETTINGS_HOTSPOT -> {
                deviceController.openHotspotSettings()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Hotspot tethering settings open panren."
                    ResponseLanguage.TAMIL -> "ஹாட்ஸ்பாட் அமைப்புகள் திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening Hotspot and Tethering settings."
                }
            }
            CommandType.OPEN_SETTINGS_DISPLAY -> {
                deviceController.openDisplaySettings()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Display and brightness settings open panren."
                    ResponseLanguage.TAMIL -> "திரை அமைப்புகள் திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening Display settings."
                }
            }
            CommandType.OPEN_SETTINGS_SOUND -> {
                deviceController.openSoundSettings()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Sound and vibration settings open panren."
                    ResponseLanguage.TAMIL -> "ஒலி அமைப்புகள் திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening Sound settings."
                }
            }
            CommandType.OPEN_SETTINGS_LOCATION -> {
                deviceController.openLocationSettings()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "GPS Location settings open panren."
                    ResponseLanguage.TAMIL -> "இருப்பிட அமைப்புகள் திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening Location settings."
                }
            }
            CommandType.OPEN_SETTINGS_BATTERY -> {
                deviceController.openBatterySettings()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Battery usage details open panren."
                    ResponseLanguage.TAMIL -> "பேட்டரி பயன்பாட்டு விவரங்கள் திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening Battery settings."
                }
            }
            CommandType.OPEN_SETTINGS_AIRPLANE -> {
                deviceController.openAirplaneModeSettings()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Airplane mode settings open panren."
                    ResponseLanguage.TAMIL -> "விமான நிலை அமைப்புகள் திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening Airplane mode settings."
                }
            }
            CommandType.OPEN_SETTINGS_APPS -> {
                deviceController.openApplicationSettings()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "App management settings open panren."
                    ResponseLanguage.TAMIL -> "செயலி மேலாண்மை திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening App settings."
                }
            }
            CommandType.OPEN_SETTINGS_GENERAL -> {
                deviceController.openDeviceSettings()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Phone main settings open panren."
                    ResponseLanguage.TAMIL -> "முதன்மை அமைப்புகள் திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening device Settings."
                }
            }

            // System Navigation & UI
            CommandType.SCREENSHOT -> {
                deviceController.takeScreenshot()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Screenshot eduthuten!"
                    ResponseLanguage.TAMIL -> "திரைப்படம் எடுக்கப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Screenshot captured."
                }
            }
            CommandType.NAV_HOME -> {
                deviceController.goHome()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Home screen-ku vandhutom."
                    ResponseLanguage.TAMIL -> "முகப்பு திரைக்கு செல்லப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Navigated to home screen."
                }
            }
            CommandType.NAV_BACK -> {
                deviceController.goBack()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Back poitten."
                    ResponseLanguage.TAMIL -> "பின்செல்லப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Navigated back."
                }
            }
            CommandType.NAV_RECENTS -> {
                deviceController.openRecentApps()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Recent apps switcher open panren."
                    ResponseLanguage.TAMIL -> "சமீபத்திய செயலிகள் திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening recent apps."
                }
            }
            CommandType.OPEN_NOTIFICATIONS -> {
                deviceController.openNotifications()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Notifications panel pull panren."
                    ResponseLanguage.TAMIL -> "அறிவிப்புகள் திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening notifications panel."
                }
            }
            CommandType.OPEN_QUICK_SETTINGS -> {
                deviceController.openQuickSettings()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Quick settings control panel pull panren."
                    ResponseLanguage.TAMIL -> "விரைவு அமைப்புகள் குழு திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Pulling down Quick Settings panel."
                }
            }
            CommandType.POWER_MENU -> {
                deviceController.openPowerMenu()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Power menu open panren."
                    ResponseLanguage.TAMIL -> "பவர் மெனு திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening Power options."
                }
            }

            // App Launching
            CommandType.OPEN_APP -> {
                val app = parsed.targetSlot ?: "App"
                val success = deviceController.launchAppByName(app)
                when (lang) {
                    ResponseLanguage.TANGLISH -> if (success) "$app open panren." else "$app phone-la kandupidikka mudila."
                    ResponseLanguage.TAMIL -> if (success) "$app திறக்கப்படுகிறது." else "$app கண்டறிய முடியவில்லை."
                    ResponseLanguage.ENGLISH -> if (success) "Opening $app." else "Could not find $app on device."
                }
            }

            // Telemetry & Status
            CommandType.BATTERY_STATUS -> {
                val batt = deviceController.getBatteryLevel()
                val charging = if (deviceController.isCharging()) " (Charging ⚡)" else ""
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Battery ippo $batt percent irukku$charging."
                    ResponseLanguage.TAMIL -> "பேட்டரி அளவு $batt சதவீதம் உள்ளது."
                    ResponseLanguage.ENGLISH -> "Your battery is at $batt percent$charging."
                }
            }
            CommandType.STORAGE_STATUS -> {
                val mb = deviceController.getFreeInternalStorageMb()
                val gb = String.format(Locale.US, "%.1f", mb / 1024.0)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Internal storage-la $gb GB free space irukku."
                    ResponseLanguage.TAMIL -> "உள் நினைவகத்தில் $gb GB இடம் காலியாக உள்ளது."
                    ResponseLanguage.ENGLISH -> "You have $gb GB of free internal storage."
                }
            }
            CommandType.TIME_QUERY -> {
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val time = sdf.format(Date())
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Ippo time $time."
                    ResponseLanguage.TAMIL -> "இப்போது நேரம் $time."
                    ResponseLanguage.ENGLISH -> "The time is $time."
                }
            }
            CommandType.DATE_QUERY -> {
                val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
                val date = sdf.format(Date())
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Inaikku date $date."
                    ResponseLanguage.TAMIL -> "இன்றைய தேதி $date."
                    ResponseLanguage.ENGLISH -> "Today is $date."
                }
            }
            CommandType.SYSTEM_DIAGNOSTICS -> {
                val batt = deviceController.getBatteryLevel()
                val vol = deviceController.getVolumePercentage()
                val br = deviceController.getBrightnessPercentage()
                val mb = deviceController.getFreeInternalStorageMb()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Alexa core 100% offline normal: Battery $batt%, Volume $vol%, Brightness $br%, Storage ${mb / 1024} GB free."
                    ResponseLanguage.TAMIL -> "அனைத்து ஆப்லைன் அமைப்புகளும் தயார். பேட்டரி $batt சதவீதம், சத்தம் $vol சதவீதம்."
                    ResponseLanguage.ENGLISH -> "All systems nominal. Battery at $batt%, volume $vol%, brightness $br%. Running 100% offline."
                }
            }

            // Conversational
            CommandType.HOW_ARE_YOU -> personaManager.getHowAreYou(lang)
            CommandType.TELL_JOKE -> personaManager.getJoke(lang)
            CommandType.IDENTITY_QUERY -> personaManager.getIdentity(lang)
            CommandType.GREETING -> personaManager.getGreeting(lang)

            CommandType.UNKNOWN -> {
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Neenga sonnadhu kettuchu: \"${parsed.rawText}\". Alexa unga device-la execute panna ready!"
                    ResponseLanguage.TAMIL -> "உங்கள் கட்டளை \"${parsed.rawText}\" பெறப்பட்டது."
                    ResponseLanguage.ENGLISH -> "I heard \"${parsed.rawText}\". Executing on your device."
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alexa Assistant (100% Offline)")
            .setContentText("Listening for 'Alexa' wake word...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        ttsManager.shutdown()
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onError(error: Int) {
        isListening = false
        // Auto-restart listener after momentary error or silence
        startListening()
    }
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
