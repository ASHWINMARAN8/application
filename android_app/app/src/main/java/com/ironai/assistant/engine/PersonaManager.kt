package com.ironai.assistant.engine

import java.util.Calendar

/**
 * Manages the SIU AI persona: SIU Core (Smart Voice Assistant) vs SIU Pro (Smart Hub)
 * Supports English, pure Tamil (தமிழ்), and natural conversational Tanglish.
 * 100% Offline Edge Intelligence.
 */
enum class PersonaType {
    ECHO_CORE,
    ECHO_SHOW,
    // Aliases for legacy compatibility
    JARVIS,
    EDITH
}

enum class ResponseLanguage {
    ENGLISH,
    TAMIL,
    TANGLISH
}

class PersonaManager {
    var currentPersona: PersonaType = PersonaType.ECHO_CORE
    var preferredLanguage: ResponseLanguage = ResponseLanguage.TANGLISH

    fun togglePersona(): PersonaType {
        currentPersona = if (currentPersona == PersonaType.ECHO_CORE || currentPersona == PersonaType.JARVIS) {
            PersonaType.ECHO_SHOW
        } else {
            PersonaType.ECHO_CORE
        }
        return currentPersona
    }

    // SIU voice modulation parameters for Android TextToSpeech
    val speechPitch: Float
        get() = when (currentPersona) {
            PersonaType.ECHO_CORE, PersonaType.JARVIS -> 1.02f // Articulate, crisp SIU voice
            PersonaType.ECHO_SHOW, PersonaType.EDITH -> 1.05f  // Dynamic, smart hub tone
        }

    val speechRate: Float
        get() = when (currentPersona) {
            PersonaType.ECHO_CORE, PersonaType.JARVIS -> 0.95f
            PersonaType.ECHO_SHOW, PersonaType.EDITH -> 0.96f
        }

    val displayName: String
        get() = when (currentPersona) {
            PersonaType.ECHO_CORE, PersonaType.JARVIS -> "SIU"
            PersonaType.ECHO_SHOW, PersonaType.EDITH -> "SIU (PRO)"
        }

    val systemSubTitle: String
        get() = when (currentPersona) {
            PersonaType.ECHO_CORE, PersonaType.JARVIS -> "SIU SUITE // EDGE AI"
            PersonaType.ECHO_SHOW, PersonaType.EDITH -> "SIU PRO HUB // SMART DISPLAY"
        }

    // Time-aware Personal Greeting
    fun getGreeting(lang: ResponseLanguage): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreetingEng = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hello"
        }

        return when (lang) {
            ResponseLanguage.TANGLISH -> {
                when (currentPersona) {
                    PersonaType.ECHO_CORE, PersonaType.JARVIS -> "$timeGreetingEng! Siu inga iruken. Unga phone offline voice control la ready-ah irukku. Enna pannanum solunga!"
                    PersonaType.ECHO_SHOW, PersonaType.EDITH -> "$timeGreetingEng! SIU Pro smart hub ready. Device controls 100% offline-la ready. Solunga!"
                }
            }
            ResponseLanguage.TAMIL -> {
                when (currentPersona) {
                    PersonaType.ECHO_CORE, PersonaType.JARVIS -> "வணக்கம்! நான் சியு (Siu). உங்கள் போன் கட்டுப்பாட்டு அமைப்புகள் தயாராக உள்ளன. என்ன உதவி செய்ய வேண்டும்?"
                    PersonaType.ECHO_SHOW, PersonaType.EDITH -> "சியு ப்ரோ தயார் நிலையில் உள்ளது. உங்கள் ஆப்லைன் குரல் கட்டளைக்காக காத்திருக்கிறேன்."
                }
            }
            ResponseLanguage.ENGLISH -> {
                when (currentPersona) {
                    PersonaType.ECHO_CORE, PersonaType.JARVIS -> "$timeGreetingEng! I'm SIU. Your offline smart assistant is online. How may I help you today?"
                    PersonaType.ECHO_SHOW, PersonaType.EDITH -> "$timeGreetingEng! SIU Pro smart hub standing by. Ready for offline instructions."
                }
            }
        }
    }

    // Human-like Identity Response
    fun getIdentity(lang: ResponseLanguage): String {
        return when (lang) {
            ResponseLanguage.TANGLISH -> {
                "Naan unga personal AI assistant Siu! Internet thevai illama unga phone calls, torch, apps, volume, touchless unlock ellathayum handle pannuven."
            }
            ResponseLanguage.TAMIL -> {
                "நான் உங்கள் ஆப்லைன் AI உதவியாளர் சியு (Siu). உங்கள் போனை முழுமையாக இணையம் இல்லாமல் இயக்கும் குரல் உதவியாளர்."
            }
            ResponseLanguage.ENGLISH -> {
                "I am SIU, your personal offline AI assistant. I run locally on your device to execute voice commands and control phone hardware without internet."
            }
        }
    }

    // Friendly Status Check
    fun getHowAreYou(lang: ResponseLanguage): String {
        return when (lang) {
            ResponseLanguage.TANGLISH -> {
                "Siu super active-ah iruken! Local processors cool-ah run aagudhu, zero internet latency. Neenga epdi irukinga?"
            }
            ResponseLanguage.TAMIL -> {
                "நான் மிகவும் சிறப்பாக இயங்குகிறேன்! அனைத்து ஆப்லைன் அமைப்புகளும் தயார். நீங்கள் எப்படி இருக்கிறீர்கள்?"
            }
            ResponseLanguage.ENGLISH -> {
                "I'm operating at peak efficiency and 100% offline! How are you doing today?"
            }
        }
    }

    // SIU Humor
    fun getJoke(lang: ResponseLanguage): String {
        return when (lang) {
            ResponseLanguage.TANGLISH -> {
                "Oru computer doctor kitta pochu... doc ketaru: 'Enna aachu?'... Computer sonnuchu: 'Enakku virus pidichurukku!' haha!"
            }
            ResponseLanguage.TAMIL -> {
                "ஒரு கம்ப்யூட்டர் மருத்துவரிடம் சென்றது. மருத்துவர் கேட்டார்: என்ன பிரச்சனை? கம்ப்யூட்டர் சொன்னது: எனக்கு வைரஸ் காய்ச்சல்!"
            }
            ResponseLanguage.ENGLISH -> {
                "Why was the cell phone wearing glasses? Because it lost its contacts!"
            }
        }
    }
}
