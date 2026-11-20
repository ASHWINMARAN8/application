package com.ironai.assistant

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ironai.assistant.databinding.ActivityMainBinding
import com.ironai.assistant.engine.DeviceController
import com.ironai.assistant.engine.IronTtsManager
import com.ironai.assistant.engine.PersonaManager
import com.ironai.assistant.engine.PersonaType
import com.ironai.assistant.services.VoiceListenerService

/**
 * Main Activity featuring the iconic Amazon Echo Glowing Light Ring HUD.
 * Configured with touchless wake, dismissKeyguard, and full offline hardware/settings controls.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        var instance: MainActivity? = null
            private set
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var personaManager: PersonaManager
    private lateinit var deviceController: DeviceController
    private lateinit var ttsManager: IronTtsManager

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.CAMERA
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            startBackgroundVoiceService()
        } else {
            Toast.makeText(this, "Permissions needed for full offline phone control", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        VoiceListenerService.activeMainActivity = this

        enableTouchlessScreenWake()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        personaManager = PersonaManager()
        deviceController = DeviceController(this)
        ttsManager = IronTtsManager(this, personaManager) { isSpeaking ->
            runOnUiThread {
                binding.tvSpeechState.text = if (isSpeaking) "ALEXA SPEAKING..." else "ALEXA STANDBY"
                if (isSpeaking) {
                    binding.btnArcCore.setCardBackgroundColor(
                        ContextCompat.getColor(this, R.color.alexa_primary)
                    )
                } else {
                    binding.btnArcCore.setCardBackgroundColor(0xFF0D1B2D.toInt())
                }
            }
        }

        setupUI()
        startEchoRingRotation()
        checkAndRequestPermissions()
        checkWriteSettingsPermission()
        setupVoiceCallback()
        updateDashboardTelemetry()
    }

    /**
     * Configures the window to wake and show above lock screen on voice trigger
     */
    private fun enableTouchlessScreenWake() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun setupUI() {
        binding.btnPersonaToggle.setOnClickListener {
            val newPersona = personaManager.togglePersona()
            applyPersonaTheme(newPersona)
        }

        binding.btnArcCore.setOnClickListener {
            binding.tvSpeechState.text = "LISTENING FOR \"ALEXA\"..."
            ttsManager.speak(personaManager.getGreeting(personaManager.preferredLanguage), false)
        }

        binding.btnEnableAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Enable 'Alexa Assistant' in Accessibility to allow full touchless phone control", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyPersonaTheme(persona: PersonaType) {
        if (persona == PersonaType.ECHO_SHOW || persona == PersonaType.EDITH) {
            binding.tvTitle.text = "ALEXA SHOW"
            binding.tvTitle.setTextColor(ContextCompat.getColor(this, R.color.show_primary))
            binding.tvBrandSub.text = "ECHO SHOW HUB // SMART DISPLAY"
            binding.tvBrandSub.setTextColor(ContextCompat.getColor(this, R.color.show_secondary))
            binding.btnPersonaToggle.text = "SWITCH: ECHO"
            binding.btnPersonaToggle.backgroundTintList = ContextCompat.getColorStateList(this, R.color.show_primary)
            binding.tvSpeechState.setTextColor(ContextCompat.getColor(this, R.color.show_primary))
            binding.ivMicIcon.setColorFilter(ContextCompat.getColor(this, R.color.show_primary))
            ttsManager.speak(personaManager.getGreeting(personaManager.preferredLanguage), false)
        } else {
            binding.tvTitle.text = "ALEXA ECHO"
            binding.tvTitle.setTextColor(ContextCompat.getColor(this, R.color.text_white))
            binding.tvBrandSub.text = "AMAZON ECHO SUITE // EDGE AI"
            binding.tvBrandSub.setTextColor(ContextCompat.getColor(this, R.color.alexa_primary))
            binding.btnPersonaToggle.text = "SWITCH: SHOW"
            binding.btnPersonaToggle.backgroundTintList = ContextCompat.getColorStateList(this, R.color.alexa_primary)
            binding.tvSpeechState.setTextColor(ContextCompat.getColor(this, R.color.alexa_primary))
            binding.ivMicIcon.setColorFilter(ContextCompat.getColor(this, R.color.alexa_primary))
            ttsManager.speak(personaManager.getGreeting(personaManager.preferredLanguage), false)
        }
    }

    private fun startEchoRingRotation() {
        val rotate = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 16000
            repeatCount = Animation.INFINITE
        }
        binding.ringOuter.startAnimation(rotate)
    }

    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            startBackgroundVoiceService()
        }
    }

    private fun checkWriteSettingsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
            // Optional: can request write settings for direct brightness modification
            // Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
        }
    }

    private fun startBackgroundVoiceService() {
        val serviceIntent = Intent(this, VoiceListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun setupVoiceCallback() {
        VoiceListenerService.onSpeechResultCallback = { userVoice, aiResponse, _ ->
            runOnUiThread {
                binding.tvUserInput.text = "You: \"$userVoice\""
                binding.tvAiOutput.text = "${personaManager.displayName}: $aiResponse"
                updateDashboardTelemetry()
            }
        }
    }

    fun updateDashboardTelemetry() {
        val battery = deviceController.getBatteryLevel()
        val volume = deviceController.getVolumePercentage()
        val brightness = deviceController.getBrightnessPercentage()
        binding.cardBattery.text = "🔋 $battery%"
        binding.cardVolume.text = "🔊 $volume%"
        binding.cardBrightness.text = "🔆 $brightness%"
    }
}
