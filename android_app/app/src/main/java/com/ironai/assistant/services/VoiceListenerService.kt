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
 * Continuous background voice listener service.
 * Operates offline using Android's on-device speech recognizer.
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
        const val CHANNEL_ID = "IronVoiceServiceChannel"
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
            Log.e("VoiceListener", "Offline speech recognition not available on this device.")
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
        val isJarvis = personaManager.currentPersona == PersonaType.JARVIS

        return when (parsed.type) {
            // Touchless Unlock Phone
            CommandType.UNLOCK_PHONE -> {
                deviceController.wakeAndUnlockDevice(activeMainActivity)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Phone-ah touchless-ah unlock panniten boss!"
                    ResponseLanguage.TAMIL -> "போன் திரை திறக்கப்பட்டது பாஸ்."
                    ResponseLanguage.ENGLISH -> if (isJarvis) "Device unlocked touchlessly, sir." else "Screen unlocked."
                }
            }
            CommandType.TORCH_ON -> {
                deviceController.setFlashlight(true)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Torch on panniten boss!"
                    ResponseLanguage.TAMIL -> "டார்ச் ஆன் செய்யப்பட்டது பாஸ்."
                    ResponseLanguage.ENGLISH -> if (isJarvis) "Flashlight illuminated, sir." else "Flashlight activated."
                }
            }
            CommandType.TORCH_OFF -> {
                deviceController.setFlashlight(false)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Torch off panniten boss."
                    ResponseLanguage.TAMIL -> "டார்ச் ஆப் செய்யப்பட்டது."
                    ResponseLanguage.ENGLISH -> if (isJarvis) "Flashlight deactivated, sir." else "Flashlight disabled."
                }
            }
            CommandType.MAKE_CALL -> {
                val target = parsed.targetSlot ?: "Contact"
                deviceController.makePhoneCall(target)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "$target-ku ippo call panren boss."
                    ResponseLanguage.TAMIL -> "$target-க்கு இப்போது கால் செய்கிறேன்."
                    ResponseLanguage.ENGLISH -> if (isJarvis) "Calling $target right away, sir." else "Establishing voice link to $target."
                }
            }
            CommandType.SEND_SMS -> {
                val target = parsed.targetSlot ?: "Contact"
                val msg = parsed.extraSlot ?: "Hello"
                deviceController.sendSms(target, msg)
                when (lang) {
                    ResponseLanguage.TANGLISH -> "$target-ku message anupiten boss: '$msg'"
                    ResponseLanguage.TAMIL -> "$target-க்கு மெசேஜ் அனுப்பப்பட்டது."
                    ResponseLanguage.ENGLISH -> if (isJarvis) "Message dispatched to $target, sir." else "Transmission sent to $target."
                }
            }
            CommandType.OPEN_APP -> {
                val app = parsed.targetSlot ?: "App"
                val success = deviceController.launchAppByName(app)
                if (success) {
                    when (lang) {
                        ResponseLanguage.TANGLISH -> "$app open panren boss."
                        ResponseLanguage.TAMIL -> "$app திறக்கப்படுகிறது."
                        ResponseLanguage.ENGLISH -> if (isJarvis) "Launching $app now, sir." else "Accessing $app."
                    }
                } else {
                    when (lang) {
                        ResponseLanguage.TANGLISH -> "$app app unga phone la kedaikala boss."
                        ResponseLanguage.TAMIL -> "$app அப்ளிகேஷன் கிடைக்கவில்லை."
                        ResponseLanguage.ENGLISH -> "Unable to locate $app package."
                    }
                }
            }
            CommandType.VOLUME_UP -> {
                val level = deviceController.increaseVolume()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Sound ah $level percent ku kootiten boss."
                    ResponseLanguage.TAMIL -> "சத்தம் அதிகரிக்கப்பட்டது: $level%."
                    ResponseLanguage.ENGLISH -> "Volume elevated to $level%."
                }
            }
            CommandType.VOLUME_DOWN -> {
                val level = deviceController.decreaseVolume()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Sound ah $level percent ku korachuten boss."
                    ResponseLanguage.TAMIL -> "சத்தம் குறைக்கப்பட்டது: $level%."
                    ResponseLanguage.ENGLISH -> "Volume reduced to $level%."
                }
            }
            CommandType.VOLUME_MUTE -> {
                deviceController.muteVolume()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Phone-ah silent panniten boss."
                    ResponseLanguage.TAMIL -> "போன் சைலன்ட் செய்யப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Device audio muted."
                }
            }
            CommandType.BATTERY_STATUS -> {
                val battery = deviceController.getBatteryLevel()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Battery ippo $battery percent irukku boss. Power full nominal!"
                    ResponseLanguage.TAMIL -> "பேட்டரி அளவு $battery சதவீதம் உள்ளது பாஸ்."
                    ResponseLanguage.ENGLISH -> if (isJarvis) "Battery standing at $battery percent, sir." else "Power levels at $battery%."
                }
            }
            CommandType.TIME_QUERY -> {
                val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Ippo time $timeStr boss."
                    ResponseLanguage.TAMIL -> "தற்போதைய நேரம் $timeStr பாஸ்."
                    ResponseLanguage.ENGLISH -> "The current time is $timeStr, sir."
                }
            }
            CommandType.LOCK_PHONE -> {
                deviceController.lockScreen()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Phone screen-ah lock panniten boss."
                    ResponseLanguage.TAMIL -> "போன் திரை லாக் செய்யப்பட்டது."
                    ResponseLanguage.ENGLISH -> "Device screen locked, sir."
                }
            }
            CommandType.SCREENSHOT -> {
                deviceController.takeScreenshot()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Screenshot capture panniten boss!"
                    ResponseLanguage.TAMIL -> "ஸ்கிரீன்ஷாட் எடுக்கப்பட்டது பாஸ்."
                    ResponseLanguage.ENGLISH -> "Screenshot captured, sir."
                }
            }
            CommandType.NAV_HOME -> {
                deviceController.goHome()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Home screen ku poiten boss."
                    ResponseLanguage.TAMIL -> "முகப்பு பக்கத்திற்கு செல்கிறேன்."
                    ResponseLanguage.ENGLISH -> "Navigating to home screen."
                }
            }
            CommandType.NAV_BACK -> {
                deviceController.goBack()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Back poiten boss."
                    ResponseLanguage.TAMIL -> "பின்னால் செல்கிறேன்."
                    ResponseLanguage.ENGLISH -> "Navigating back."
                }
            }
            CommandType.OPEN_NOTIFICATIONS -> {
                deviceController.openNotifications()
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Notification shade open panren boss."
                    ResponseLanguage.TAMIL -> "நோட்டிபிகேஷன் திறக்கப்படுகிறது."
                    ResponseLanguage.ENGLISH -> "Opening notification panel."
                }
            }
            CommandType.IDENTITY_QUERY -> personaManager.getIdentity(lang)
            CommandType.GREETING -> personaManager.getGreeting(lang)
            CommandType.HOW_ARE_YOU -> personaManager.getHowAreYou(lang)
            CommandType.TELL_JOKE -> personaManager.getJoke(lang)
            else -> {
                when (lang) {
                    ResponseLanguage.TANGLISH -> "Neenga sonnadhu kettuchu boss: '${parsed.rawText}'. Execute panren!"
                    ResponseLanguage.TAMIL -> "உங்கள் கட்டளை பெறப்பட்டது பாஸ்: '${parsed.rawText}'."
                    ResponseLanguage.ENGLISH -> if (isJarvis) "Directive acknowledged, sir: '${parsed.rawText}'." else "Command received."
                }
            }
        }
    }

    override fun onError(error: Int) {
        isListening = false
        android.os.Handler(mainLooper).postDelayed({
            startListening()
        }, 800)
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS & EDITH Personal Core Active")
            .setContentText("Offline Voice Listener Standby (ENG/தமிழ்/Tanglish)")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        ttsManager.shutdown()
    }
}
