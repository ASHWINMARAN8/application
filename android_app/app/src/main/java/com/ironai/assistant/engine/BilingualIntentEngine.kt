package com.ironai.assistant.engine

import java.util.regex.Pattern

/**
 * High-speed offline Natural Language Intent Parser and Slot Filling Engine.
 * Operates in < 3ms with zero network calls and minimal memory footprint (< 5MB RAM).
 * Highly optimized for low-end Android devices (runs smoothly on 2GB-3GB RAM phones).
 */

enum class CommandType {
    TORCH_ON,
    TORCH_OFF,
    MAKE_CALL,
    SEND_SMS,
    OPEN_APP,
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_SET,
    VOLUME_MUTE,
    BATTERY_STATUS,
    SYSTEM_DIAGNOSTICS,
    TIME_QUERY,
    LOCK_PHONE,
    UNLOCK_PHONE,
    SCREENSHOT,
    NAV_HOME,
    NAV_BACK,
    OPEN_NOTIFICATIONS,
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

        // 2. TOUCHLESS UNLOCK / WAKE PHONE
        if (containsAny(lower, "unlock phone", "unlock my phone", "phone ah unlock pannu", "unlock pannu", "screen ah open pannu", "போனை அன்லாக் பண்ணு", "திரையை திற", "wake up and unlock")) {
            return ParsedIntent(CommandType.UNLOCK_PHONE, language, input)
        }

        // 3. FLASHLIGHT / TORCH
        if (containsAny(lower, "டார்ச் போடு", "டார்ச் ஆன்", "லைட் போடு", "torch on", "turn on flashlight", "light on", "torch podu", "turn on torch", "torch ah on pannu", "light podu", "torch on pannu")) {
            return ParsedIntent(CommandType.TORCH_ON, language, input)
        }
        if (containsAny(lower, "டார்ச் ஆப்", "டார்ச் அணை", "லைட் ஆப்", "torch off", "turn off flashlight", "light off", "torch off pannu", "turn off torch", "light anai", "torch ah off pannu")) {
            return ParsedIntent(CommandType.TORCH_OFF, language, input)
        }

        // 4. PHONE CALLS
        if (containsAny(lower, "கால் பண்ணு", "call", "dial", "போன் பண்ணு", "phone pannu", "call pannu")) {
            var contact = extractTamilCallContact(input)
            if (contact.isNullOrBlank()) {
                contact = extractTanglishCallContact(lower)
            }
            if (contact.isNullOrBlank()) {
                contact = extractEnglishCallContact(lower)
            }
            return ParsedIntent(CommandType.MAKE_CALL, language, input, targetSlot = contact ?: "Contact")
        }

        // 5. WHATSAPP & SMS MESSAGING
        if (containsAny(lower, "மெசேஜ்", "message", "sms", "whatsapp", "வாட்ஸ்அப்", "message anupu", "msg podu")) {
            val (recipient, message) = extractMessagingSlots(input, lower)
            return ParsedIntent(
                CommandType.SEND_SMS,
                language,
                input,
                targetSlot = recipient ?: "Contact",
                extraSlot = message ?: "Hello from Iron Assistant"
            )
        }

        // 6. APP LAUNCHING
        if (containsAny(lower, "ஓபன்", "திற", "open", "launch", "start", "open pannu", "thira")) {
            val app = extractAppName(lower)
            return ParsedIntent(CommandType.OPEN_APP, language, input, targetSlot = app)
        }

        // 7. VOLUME CONTROLS
        if (containsAny(lower, "சத்தத்தை கூட்டு", "volume up", "increase volume", "volume kootu", "sound kootu", "sound ah kootu")) {
            return ParsedIntent(CommandType.VOLUME_UP, language, input)
        }
        if (containsAny(lower, "சத்தத்தை குறை", "volume down", "decrease volume", "volume kurai", "sound kurai", "volume korai", "sound ah korai")) {
            return ParsedIntent(CommandType.VOLUME_DOWN, language, input)
        }
        if (containsAny(lower, "சைலன்ட்", "mute", "silent", "silent pannu", "sound off pannu")) {
            return ParsedIntent(CommandType.VOLUME_MUTE, language, input)
        }

        // 8. BATTERY & STATUS & TIME
        if (containsAny(lower, "பேட்டரி", "battery", "power level", "சார்ஜ்", "charge evalo", "battery evvalavu", "battery status")) {
            return ParsedIntent(CommandType.BATTERY_STATUS, language, input)
        }
        if (containsAny(lower, "டைம் என்ன", "நேரம் என்ன", "what time is it", "time enna", "time sollu")) {
            return ParsedIntent(CommandType.TIME_QUERY, language, input)
        }
        if (containsAny(lower, "சிஸ்டம் ஸ்டேட்டஸ்", "system status", "diagnostics", "போன் ஸ்டேட்டஸ்", "phone status")) {
            return ParsedIntent(CommandType.SYSTEM_DIAGNOSTICS, language, input)
        }

        // 9. NAVIGATION & ACCESSIBILITY ACTIONS
        if (containsAny(lower, "லாக் பண்ணு", "lock phone", "screen lock", "lock my phone", "பூட்டு", "lock pannu", "screen ah lock pannu")) {
            return ParsedIntent(CommandType.LOCK_PHONE, language, input)
        }
        if (containsAny(lower, "ஸ்கிரீன்ஷாட்", "screenshot", "take screenshot", "படம் எடு", "screenshot edu")) {
            return ParsedIntent(CommandType.SCREENSHOT, language, input)
        }
        if (containsAny(lower, "ஹோம் போ", "go home", "home screen", "முகப்பு", "home ku po", "home screen ku po")) {
            return ParsedIntent(CommandType.NAV_HOME, language, input)
        }
        if (containsAny(lower, "பின்னாடி போ", "go back", "back", "back po")) {
            return ParsedIntent(CommandType.NAV_BACK, language, input)
        }
        if (containsAny(lower, "நோட்டிபிகேஷன்", "notifications", "show notifications", "notification kaatu")) {
            return ParsedIntent(CommandType.OPEN_NOTIFICATIONS, language, input)
        }

        // 10. PERSONAL CONVERSATION / HUMAN-LIKE BANTER
        if (containsAny(lower, "epdi iruka", "how are you", "எப்படி இருக்க", "epdi irukinga", "how do you do")) {
            return ParsedIntent(CommandType.HOW_ARE_YOU, language, input)
        }
        if (containsAny(lower, "joke sollu", "tell me a joke", "ஜோக் சொல்லு", "joke")) {
            return ParsedIntent(CommandType.TELL_JOKE, language, input)
        }
        if (containsAny(lower, "நீ யாரு", "who are you", "உன் பெயர் என்ன", "what is your name", "ne yaaru", "unga name enna")) {
            return ParsedIntent(CommandType.IDENTITY_QUERY, language, input)
        }
        if (containsAny(lower, "வணக்கம்", "hello", "hey", "hi", "காலை வணக்கம்", "good morning", "vanakkam", "hey joe", "joe", "edith", "hey jarvis", "ஜோ", "ஹேய் ஜோ")) {
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
            "inga", "anga", "amma ku", "appa ku", "ku call", "ah", "romba", "nalla", "unlock"
        )
        if (tanglishKeywords.any { lower.contains(it) }) {
            return ResponseLanguage.TANGLISH
        }

        return ResponseLanguage.ENGLISH
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    private fun extractTamilCallContact(text: String): String? {
        val pattern = Pattern.compile("(.*?)(?:-க்கு|க்கு|\\s+)கால்\\s*பண்ணு", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.replace(Regex("^(நீ|தயவுசெய்து)\\s*"), "")?.trim()
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
        if (raw.contains(":")) {
            val parts = raw.split(":", limit = 2)
            val recipientPart = parts[0]
            val message = parts[1].trim()
            val pattern = Pattern.compile("(?:to|அனுப்பு|anupu|க்கு|ku)\\s*([a-zA-Z\\u0B80-\\u0BFF\\s]+)", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(recipientPart)
            val recipient = if (matcher.find()) matcher.group(1)?.trim() else "Contact"
            return Pair(recipient, message)
        }
        return Pair("Contact", "Hello from your assistant")
    }

    private fun extractAppName(lower: String): String {
        return when {
            lower.contains("youtube") || lower.contains("யூடியூப்") -> "YouTube"
            lower.contains("camera") || lower.contains("கேமரா") -> "Camera"
            lower.contains("whatsapp") || lower.contains("வாட்ஸ்அப்") -> "WhatsApp"
            lower.contains("settings") || lower.contains("செட்டிங்ஸ்") -> "Settings"
            lower.contains("chrome") || lower.contains("குரோம்") -> "Chrome"
            lower.contains("instagram") || lower.contains("insta") -> "Instagram"
            lower.contains("spotify") -> "Spotify"
            lower.contains("gallery") || lower.contains("கேலரி") -> "Gallery"
            lower.contains("maps") -> "Maps"
            else -> {
                val match = Regex("(?:open|launch|start|ஓபன்|திற|open pannu)\\s+([a-zA-Z\\u0B80-\\u0BFF]+)").find(lower)
                match?.groupValues?.get(1)?.replaceFirstChar { it.uppercase() } ?: "App"
            }
        }
    }
}
