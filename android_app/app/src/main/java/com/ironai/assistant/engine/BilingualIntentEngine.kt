package com.ironai.assistant.engine

import java.util.regex.Pattern

/**
 * High-speed offline Natural Language Intent Parser for SIU Voice Engine.
 * Operates in < 2ms with zero network calls and minimal memory footprint (< 4MB RAM).
 * Highly optimized for low-end Android devices (runs smoothly on 2GB-3GB RAM phones).
 */

enum class CommandType {
    // Hardware & Torch
    TORCH_ON,
    TORCH_OFF,

    // Communications
    MAKE_CALL,
    SEND_SMS,
    SEND_WHATSAPP,

    // Apps & Camera
    OPEN_APP,
    TAKE_PHOTO,
    RECORD_VIDEO,

    // Volume & Sound
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_SET,
    VOLUME_MUTE,
    VOLUME_MAX,
    RINGER_SILENT,
    RINGER_VIBRATE,
    RINGER_NORMAL,

    // Brightness & Display
    BRIGHTNESS_UP,
    BRIGHTNESS_DOWN,
    BRIGHTNESS_SET,
    BRIGHTNESS_MAX,

    // Media Playback
    MEDIA_PLAY_PAUSE,
    MEDIA_NEXT,
    MEDIA_PREV,
    MEDIA_STOP,

    // Alarms & Timers
    SET_TIMER,
    SET_ALARM,
    OPEN_ALARMS,
    OPEN_STOPWATCH,

    // System Telemetry & Time
    BATTERY_STATUS,
    STORAGE_STATUS,
    SYSTEM_DIAGNOSTICS,
    TIME_QUERY,
    DATE_QUERY,

    // Navigation & Accessibility
    LOCK_PHONE,
    UNLOCK_PHONE,
    SCREENSHOT,
    NAV_HOME,
    NAV_BACK,
    NAV_RECENTS,
    OPEN_NOTIFICATIONS,
    OPEN_QUICK_SETTINGS,
    POWER_MENU,

    // Settings Shortcuts
    OPEN_SETTINGS_WIFI,
    OPEN_SETTINGS_BLUETOOTH,
    OPEN_SETTINGS_HOTSPOT,
    OPEN_SETTINGS_DISPLAY,
    OPEN_SETTINGS_SOUND,
    OPEN_SETTINGS_LOCATION,
    OPEN_SETTINGS_BATTERY,
    OPEN_SETTINGS_APPS,
    OPEN_SETTINGS_AIRPLANE,
    OPEN_SETTINGS_GENERAL,

    // Conversation & Banter
    IDENTITY_QUERY,
    GREETING,
    HOW_ARE_YOU,
    TELL_JOKE,
    UNKNOWN
}

data class ParsedIntent(
    val type: CommandType,
    val language: ResponseLanguage,
    val rawText: String,
    val targetSlot: String? = null,
    val extraSlot: String? = null,
    val valueSlot: Int? = null
)

class BilingualIntentEngine {

    fun parse(rawInput: String): ParsedIntent {
        val input = rawInput.trim()
        val lower = input.lowercase()

        // 1. Language Detection: Pure Tamil, Tanglish, or English
        val language = detectLanguage(input, lower)

        // 2. Strip wake words if present (Siu, Hey Siu, Siiiu, Alexa)
        val cleaned = lower
            .replace(Regex("^(siu|hey siu|siiiuuu|siiuu|sii|alexa|hey alexa|echo|amazon|சியு|ஹேய் சியு|அலெக்சா|ஹேய் அலெக்சா)[\\s,:]*"), "")
            .trim()
        val effectiveText = if (cleaned.isNotEmpty()) cleaned else lower

        // 3. TOUCHLESS UNLOCK / WAKE PHONE
        if (containsAny(effectiveText, "unlock phone", "unlock my phone", "phone ah unlock pannu", "unlock pannu", "screen ah open pannu", "போனை அன்லாக் பண்ணு", "திரையை திற", "wake up and unlock", "unlock")) {
            return ParsedIntent(CommandType.UNLOCK_PHONE, language, input)
        }

        // 4. FLASHLIGHT / TORCH
        if (containsAny(effectiveText, "டார்ச் போடு", "டார்ச் ஆன்", "லைட் போடு", "torch on", "turn on flashlight", "light on", "torch podu", "turn on torch", "torch ah on pannu", "light podu", "torch on pannu")) {
            return ParsedIntent(CommandType.TORCH_ON, language, input)
        }
        if (containsAny(effectiveText, "டார்ச் ஆப்", "டார்ச் அணை", "லைட் ஆப்", "torch off", "turn off flashlight", "light off", "torch off pannu", "turn off torch", "light anai", "torch ah off pannu")) {
            return ParsedIntent(CommandType.TORCH_OFF, language, input)
        }

        // 5. CAMERA & PHOTOS
        if (containsAny(effectiveText, "take photo", "take a picture", "photo edu", "போட்டோ எடு", "படம் எடு", "click photo")) {
            return ParsedIntent(CommandType.TAKE_PHOTO, language, input)
        }
        if (containsAny(effectiveText, "record video", "video edu", "வீடியோ எடு", "start recording")) {
            return ParsedIntent(CommandType.RECORD_VIDEO, language, input)
        }

        // 6. PHONE CALLS
        if (containsAny(effectiveText, "கால் பண்ணு", "call", "dial", "போன் பண்ணு", "phone pannu", "call pannu")) {
            var contact = extractTamilCallContact(input)
            if (contact.isNullOrBlank()) {
                contact = extractTanglishCallContact(effectiveText)
            }
            if (contact.isNullOrBlank()) {
                contact = extractEnglishCallContact(effectiveText)
            }
            return ParsedIntent(CommandType.MAKE_CALL, language, input, targetSlot = contact ?: "Contact")
        }

        // 7. WHATSAPP & SMS MESSAGING
        if (containsAny(effectiveText, "whatsapp", "வாட்ஸ்அப்", "whatsapp message", "whatsapp msg")) {
            val (recipient, message) = extractMessagingSlots(input, effectiveText)
            return ParsedIntent(
                CommandType.SEND_WHATSAPP,
                language,
                input,
                targetSlot = recipient ?: "Contact",
                extraSlot = message ?: "Hello from SIU Assistant"
            )
        }
        if (containsAny(effectiveText, "மெசேஜ்", "message", "sms", "message anupu", "msg podu", "send sms")) {
            val (recipient, message) = extractMessagingSlots(input, effectiveText)
            return ParsedIntent(
                CommandType.SEND_SMS,
                language,
                input,
                targetSlot = recipient ?: "Contact",
                extraSlot = message ?: "Hello from SIU Assistant"
            )
        }

        // 8. BRIGHTNESS CONTROLS
        if (containsAny(effectiveText, "brightness max", "full brightness", "brightness kootu", "வெளிச்சம் கூட்டு", "increase brightness", "brightness up")) {
            return ParsedIntent(CommandType.BRIGHTNESS_UP, language, input)
        }
        if (containsAny(effectiveText, "brightness low", "reduce brightness", "brightness kurai", "வெளிச்சம் குறை", "decrease brightness", "brightness down", "brightness korai")) {
            return ParsedIntent(CommandType.BRIGHTNESS_DOWN, language, input)
        }
        if (effectiveText.contains("brightness") || effectiveText.contains("வெளிச்சம்")) {
            val num = extractNumber(effectiveText)
            if (num != null) {
                return ParsedIntent(CommandType.BRIGHTNESS_SET, language, input, valueSlot = num)
            }
        }

        // 9. VOLUME CONTROLS
        if (containsAny(effectiveText, "சத்தத்தை கூட்டு", "volume up", "increase volume", "volume kootu", "sound kootu", "sound ah kootu")) {
            return ParsedIntent(CommandType.VOLUME_UP, language, input)
        }
        if (containsAny(effectiveText, "சத்தத்தை குறை", "volume down", "decrease volume", "volume kurai", "sound kurai", "volume korai", "sound ah korai")) {
            return ParsedIntent(CommandType.VOLUME_DOWN, language, input)
        }
        if (containsAny(effectiveText, "volume max", "full volume", "sound max", "100% volume")) {
            return ParsedIntent(CommandType.VOLUME_MAX, language, input)
        }
        if (containsAny(effectiveText, "சைலன்ட்", "mute", "silent", "silent pannu", "sound off pannu", "mute volume")) {
            return ParsedIntent(CommandType.VOLUME_MUTE, language, input)
        }
        if (effectiveText.contains("volume") || effectiveText.contains("sound")) {
            val num = extractNumber(effectiveText)
            if (num != null) {
                return ParsedIntent(CommandType.VOLUME_SET, language, input, valueSlot = num)
            }
        }

        // 10. RINGER / VIBRATION MODES
        if (containsAny(effectiveText, "vibrate mode", "vibration", "வைப்ரேட்", "vibrate pannu")) {
            return ParsedIntent(CommandType.RINGER_VIBRATE, language, input)
        }
        if (containsAny(effectiveText, "normal mode", "ring mode", "ringer on", "sound on")) {
            return ParsedIntent(CommandType.RINGER_NORMAL, language, input)
        }

        // 11. MEDIA PLAYBACK
        if (containsAny(effectiveText, "play music", "pause music", "பாட்டு போடு", "பாட்டை நிறுத்து", "song play", "song pause", "resume music", "play", "pause")) {
            return ParsedIntent(CommandType.MEDIA_PLAY_PAUSE, language, input)
        }
        if (containsAny(effectiveText, "next song", "next track", "அடுத்த பாடல்", "adutha song")) {
            return ParsedIntent(CommandType.MEDIA_NEXT, language, input)
        }
        if (containsAny(effectiveText, "previous song", "prev track", "முந்தைய பாடல்", "munnadi song")) {
            return ParsedIntent(CommandType.MEDIA_PREV, language, input)
        }

        // 12. TIMER & ALARM & STOPWATCH
        if (containsAny(effectiveText, "stopwatch", "ஸ்டாப்வாட்ச்")) {
            return ParsedIntent(CommandType.OPEN_STOPWATCH, language, input)
        }
        if (containsAny(effectiveText, "alarm", "அலாரம்", "wake me up", "alarm vai")) {
            val num = extractNumber(effectiveText)
            return ParsedIntent(CommandType.SET_ALARM, language, input, valueSlot = num ?: 7)
        }
        if (containsAny(effectiveText, "timer", "டைமர்", "timer vai")) {
            val num = extractNumber(effectiveText)
            return ParsedIntent(CommandType.SET_TIMER, language, input, valueSlot = num ?: 5)
        }

        // 13. SETTINGS SHORTCUTS
        if (containsAny(effectiveText, "wifi settings", "open wifi", "வைபை செட்டிங்ஸ்", "wifi open pannu", "wifi")) {
            return ParsedIntent(CommandType.OPEN_SETTINGS_WIFI, language, input)
        }
        if (containsAny(effectiveText, "bluetooth settings", "open bluetooth", "புளூடூத்", "bluetooth open pannu", "bluetooth")) {
            return ParsedIntent(CommandType.OPEN_SETTINGS_BLUETOOTH, language, input)
        }
        if (containsAny(effectiveText, "hotspot", "ஹாட்ஸ்பாட்", "tethering", "hotspot settings")) {
            return ParsedIntent(CommandType.OPEN_SETTINGS_HOTSPOT, language, input)
        }
        if (containsAny(effectiveText, "display settings", "screen settings", "திரை அமைப்புகள்", "brightness settings")) {
            return ParsedIntent(CommandType.OPEN_SETTINGS_DISPLAY, language, input)
        }
        if (containsAny(effectiveText, "sound settings", "audio settings", "ஒலி அமைப்புகள்")) {
            return ParsedIntent(CommandType.OPEN_SETTINGS_SOUND, language, input)
        }
        if (containsAny(effectiveText, "location settings", "gps settings", "ஜிபிஎஸ்", "location open")) {
            return ParsedIntent(CommandType.OPEN_SETTINGS_LOCATION, language, input)
        }
        if (containsAny(effectiveText, "battery settings", "battery usage", "பேட்டரி செட்டிங்ஸ்")) {
            return ParsedIntent(CommandType.OPEN_SETTINGS_BATTERY, language, input)
        }
        if (containsAny(effectiveText, "airplane mode", "flight mode", "விமான நிலை")) {
            return ParsedIntent(CommandType.OPEN_SETTINGS_AIRPLANE, language, input)
        }
        if (containsAny(effectiveText, "app settings", "manage apps", "apps list")) {
            return ParsedIntent(CommandType.OPEN_SETTINGS_APPS, language, input)
        }
        if (containsAny(effectiveText, "open settings", "phone settings", "செட்டிங்ஸ் திற", "settings open pannu") && !effectiveText.contains("wifi") && !effectiveText.contains("bluetooth")) {
            return ParsedIntent(CommandType.OPEN_SETTINGS_GENERAL, language, input)
        }

        // 14. BATTERY, STORAGE & SYSTEM TELEMETRY
        if (containsAny(effectiveText, "storage", "memory", "storage evalo", "மெமரி", "storage status")) {
            return ParsedIntent(CommandType.STORAGE_STATUS, language, input)
        }
        if (containsAny(effectiveText, "பேட்டரி", "battery", "power level", "சார்ஜ்", "charge evalo", "battery evvalavu", "battery status")) {
            return ParsedIntent(CommandType.BATTERY_STATUS, language, input)
        }
        if (containsAny(effectiveText, "டைம் என்ன", "நேரம் என்ன", "what time is it", "time enna", "time sollu", "time")) {
            return ParsedIntent(CommandType.TIME_QUERY, language, input)
        }
        if (containsAny(effectiveText, "date enna", "what is today's date", "இன்றைய தேதி என்ன", "date sollu", "date")) {
            return ParsedIntent(CommandType.DATE_QUERY, language, input)
        }
        if (containsAny(effectiveText, "சிஸ்டம் ஸ்டேட்டஸ்", "system status", "diagnostics", "போன் ஸ்டேட்டஸ்", "phone status")) {
            return ParsedIntent(CommandType.SYSTEM_DIAGNOSTICS, language, input)
        }

        // 15. NAVIGATION & ACCESSIBILITY ACTIONS
        if (containsAny(effectiveText, "லாக் பண்ணு", "lock phone", "screen lock", "lock my phone", "பூட்டு", "lock pannu", "screen ah lock pannu", "lock")) {
            return ParsedIntent(CommandType.LOCK_PHONE, language, input)
        }
        if (containsAny(effectiveText, "ஸ்கிரீன்ஷாட்", "screenshot", "take screenshot", "படம் எடு", "screenshot edu")) {
            return ParsedIntent(CommandType.SCREENSHOT, language, input)
        }
        if (containsAny(effectiveText, "ஹோம் போ", "go home", "home screen", "முகப்பு", "home ku po", "home screen ku po")) {
            return ParsedIntent(CommandType.NAV_HOME, language, input)
        }
        if (containsAny(effectiveText, "பின்னாடி போ", "go back", "back", "back po")) {
            return ParsedIntent(CommandType.NAV_BACK, language, input)
        }
        if (containsAny(effectiveText, "recent apps", "app switcher", "recents", "ரீசண்ட் ஆப்ஸ்")) {
            return ParsedIntent(CommandType.NAV_RECENTS, language, input)
        }
        if (containsAny(effectiveText, "quick settings", "quick panel", "குயிக் செட்டிங்ஸ்", "control center")) {
            return ParsedIntent(CommandType.OPEN_QUICK_SETTINGS, language, input)
        }
        if (containsAny(effectiveText, "நோட்டிபிகேஷன்", "notifications", "show notifications", "notification kaatu")) {
            return ParsedIntent(CommandType.OPEN_NOTIFICATIONS, language, input)
        }
        if (containsAny(effectiveText, "power menu", "power off menu", "பவர் மெனு", "restart menu")) {
            return ParsedIntent(CommandType.POWER_MENU, language, input)
        }

        // 16. APP LAUNCHING
        if (containsAny(effectiveText, "ஓபன்", "திற", "open", "launch", "start", "open pannu", "thira")) {
            val app = extractAppName(effectiveText)
            return ParsedIntent(CommandType.OPEN_APP, language, input, targetSlot = app)
        }

        // 17. PERSONAL CONVERSATION / ALEXA BANTER
        if (containsAny(effectiveText, "epdi iruka", "how are you", "எப்படி இருக்க", "epdi irukinga", "how do you do")) {
            return ParsedIntent(CommandType.HOW_ARE_YOU, language, input)
        }
        if (containsAny(effectiveText, "joke sollu", "tell me a joke", "ஜோக் சொல்லு", "joke")) {
            return ParsedIntent(CommandType.TELL_JOKE, language, input)
        }
        if (containsAny(effectiveText, "நீ யாரு", "who are you", "உன் பெயர் என்ன", "what is your name", "ne yaaru", "unga name enna", "about siu", "about alexa")) {
            return ParsedIntent(CommandType.IDENTITY_QUERY, language, input)
        }
        if (containsAny(lower, "வணக்கம்", "hello", "hey", "hi", "காலை வணக்கம்", "good morning", "vanakkam", "hey siu", "siu", "siiiuuu", "சியு", "ஹேய் சியு", "hey alexa", "alexa", "echo", "amazon", "அலெக்சா", "ஹேய் அலெக்சா")) {
            return ParsedIntent(CommandType.GREETING, language, input)
        }

        return ParsedIntent(CommandType.UNKNOWN, language, input)
    }

    private fun detectLanguage(raw: String, lower: String): ResponseLanguage {
        if (raw.any { it in '\u0B80'..'\u0BFF' }) {
            return ResponseLanguage.TAMIL
        }

        val tanglishKeywords = arrayOf(
            "pannu", "podu", "kootu", "kurai", "korai", "anupu", "sollu", "solunga",
            "iruka", "irukinga", "irukku", "evalo", "evvalavu", "enna", "yaaru", "ne",
            "inga", "anga", "amma ku", "appa ku", "ku call", "ah", "romba", "nalla", "unlock", "vai"
        )
        if (tanglishKeywords.any { lower.contains(it) }) {
            return ResponseLanguage.TANGLISH
        }

        return ResponseLanguage.ENGLISH
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    private fun extractNumber(text: String): Int? {
        val match = Regex("(\\d+)").find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractTamilCallContact(text: String): String? {
        val pattern = Pattern.compile("(.*?)(?:-க்கு|க்கு|\\s+)கால்\\s*பண்ணு", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.replace(Regex("^(நீ|தயவுசெய்து|சியு|அலெக்சா)\\s*"), "")?.trim()
        }
        return null
    }

    private fun extractTanglishCallContact(lower: String): String? {
        val match1 = Regex("(?:call\\s+pannu|phone\\s+pannu)\\s+([a-zA-Z0-9]+)").find(lower)
        if (match1 != null) return match1.groupValues[1]

        val match2 = Regex("([a-zA-Z0-9]+)\\s*(?:-?ku|-?kku)?\\s*(?:call|phone)\\s*pannu").find(lower)
        if (match2 != null) return match2.groupValues[1]

        return null
    }

    private fun extractEnglishCallContact(text: String): String? {
        val pattern = Pattern.compile("(?:call|dial|phone)\\s+([a-zA-Z0-9\\s]+)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.trim()
        }
        return null
    }

    private fun extractMessagingSlots(raw: String, lower: String): Pair<String?, String?> {
        var recipient: String? = null
        var message: String? = null

        if (raw.contains(":")) {
            val parts = raw.split(":", limit = 2)
            message = parts[1].trim()
            val left = parts[0]
            val rMatch = Regex("(?:to|anupu|அனுப்பு|க்கு|ku)\\s*([a-zA-Z0-9\\u0B80-\\u0BFF]+)", RegexOption.IGNORE_CASE).find(left)
            recipient = rMatch?.groupValues?.get(1)?.trim()
        } else {
            val rMatch = Regex("(?:to|anupu|அனுப்பு|க்கு|ku)\\s*([a-zA-Z0-9\\u0B80-\\u0BFF]+)", RegexOption.IGNORE_CASE).find(raw)
            recipient = rMatch?.groupValues?.get(1)?.trim()
        }

        return Pair(recipient, message)
    }

    private fun extractAppName(lower: String): String {
        return when {
            lower.contains("youtube") || lower.contains("யூடியூப்") -> "YouTube"
            lower.contains("camera") || lower.contains("கேமரா") -> "Camera"
            lower.contains("whatsapp") || lower.contains("வாட்ஸ்அப்") -> "WhatsApp"
            lower.contains("instagram") || lower.contains("insta") -> "Instagram"
            lower.contains("spotify") || lower.contains("music") || lower.contains("ஸ்பாடிஃபை") -> "Spotify"
            lower.contains("maps") || lower.contains("மேப்ஸ்") -> "Maps"
            lower.contains("calculator") || lower.contains("கால்குலேட்டர்") -> "Calculator"
            lower.contains("clock") || lower.contains("அலாரம்") -> "Clock"
            lower.contains("settings") || lower.contains("செட்டிங்ஸ்") -> "Settings"
            lower.contains("chrome") || lower.contains("குரோம்") -> "Chrome"
            lower.contains("gallery") || lower.contains("photos") || lower.contains("கேலரி") -> "Photos"
            else -> {
                val match = Regex("(?:open|launch|start|open pannu)\\s+([a-zA-Z]+)").find(lower)
                match?.groupValues?.get(1)?.replaceFirstChar { it.uppercase() } ?: "Application"
            }
        }
    }
}
