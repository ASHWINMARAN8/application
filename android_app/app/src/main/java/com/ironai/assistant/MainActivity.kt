package com.ironai.assistant

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 * Main Activity featuring the futuristic Arc Reactor HUD.
 * Configured with touchless wake and dismissKeyguard capabilities.
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
                binding.tvSpeechState.text = if (isSpeaking) "AI SPEAKING..." else "VOICE ENGINE: STANDBY"
                if (isSpeaking) {
                    binding.btnArcCore.setCardBackgroundColor(
                        ContextCompat.getColor(this, R.color.jarvis_accent)
                    )
                } else {
                    binding.btnArcCore.setCardBackgroundColor(0xFF031620.toInt())
                }
            }
        }

        setupUI()
        startArcRotation()
        checkAndRequestPermissions()
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
            binding.tvSpeechState.text = "LISTENING (MIC ACTIVE)..."
            ttsManager.speak(personaManager.getGreeting(personaManager.preferredLanguage), false)
        }

        binding.btnEnableAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Enable 'A.I.D.A. Assistant' in Accessibility to allow touchless swipe unlock & phone control", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyPersonaTheme(persona: PersonaType) {
        if (persona == PersonaType.EDITH) {
            binding.tvTitle.text = "E.D.I.T.H."
            binding.tvTitle.setTextColor(ContextCompat.getColor(this, R.color.edith_primary))
            binding.tvBrandSub.text = "STARK TACTICAL DEFENSE // EDITH"
            binding.tvBrandSub.setTextColor(ContextCompat.getColor(this, R.color.edith_secondary))
            binding.btnPersonaToggle.text = "SWITCH: JARVIS"
            binding.btnPersonaToggle.backgroundTintList = ContextCompat.getColorStateList(this, R.color.edith_primary)
            binding.tvSpeechState.setTextColor(ContextCompat.getColor(this, R.color.edith_primary))
            binding.ivMicIcon.setColorFilter(ContextCompat.getColor(this, R.color.edith_primary))
            ttsManager.speak(personaManager.getGreeting(personaManager.preferredLanguage), false)
        } else {
            binding.tvTitle.text = "JARVIS OS"
            binding.tvTitle.setTextColor(ContextCompat.getColor(this, R.color.text_white))
            binding.tvBrandSub.text = "STARK TACTICAL A.I."
            binding.tvBrandSub.setTextColor(ContextCompat.getColor(this, R.color.jarvis_secondary))
            binding.btnPersonaToggle.text = "SWITCH: EDITH"
            binding.btnPersonaToggle.backgroundTintList = ContextCompat.getColorStateList(this, R.color.jarvis_primary)
            binding.tvSpeechState.setTextColor(ContextCompat.getColor(this, R.color.jarvis_primary))
            binding.ivMicIcon.setColorFilter(ContextCompat.getColor(this, R.color.jarvis_primary))
            ttsManager.speak(personaManager.getGreeting(personaManager.preferredLanguage), false)
        }
    }

    private fun startArcRotation() {
        val rotate = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 20000
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

    private fun updateDashboardTelemetry() {
        val battery = deviceController.getBatteryLevel()
        val volume = deviceController.getVolumePercentage()
        binding.cardBattery.text = "🔋 $battery%"
        binding.cardVolume.text = "🔊 VOL: $volume%"
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        VoiceListenerService.activeMainActivity = null
        ttsManager.shutdown()
    }
}
