package com.ironai.assistant.engine

import java.util.Calendar

/**
 * Manages the AI persona: JARVIS (Stark Industries) vs E.D.I.T.H. (Tactical Defense)
 * Supports English, pure Tamil (தமிழ்), and natural conversational Tanglish.
 */
enum class PersonaType {
    JARVIS,
    EDITH
}

enum class ResponseLanguage {
    ENGLISH,
    TAMIL,
    TANGLISH
}

class PersonaManager {
    var currentPersona: PersonaType = PersonaType.JARVIS
    var preferredLanguage: ResponseLanguage = ResponseLanguage.TANGLISH

    fun togglePersona(): PersonaType {
        currentPersona = if (currentPersona == PersonaType.JARVIS) {
            PersonaType.EDITH
        } else {
            PersonaType.JARVIS
        }
        return currentPersona
    }

    // Voice modulation parameters for Android TextToSpeech
    val speechPitch: Float
        get() = when (currentPersona) {
            PersonaType.JARVIS -> 0.85f // Sophisticated, gentleman tone
            PersonaType.EDITH -> 1.10f  // Crisp, tactical tone
        }

    val speechRate: Float
        get() = when (currentPersona) {
            PersonaType.JARVIS -> 0.95f
            PersonaType.EDITH -> 1.05f
        }

    val displayName: String
        get() = when (currentPersona) {
            PersonaType.JARVIS -> "JOE"
            PersonaType.EDITH -> "JOE (E.D.I.T.H.)"
        }

    val systemSubTitle: String
        get() = when (currentPersona) {
            PersonaType.JARVIS -> "JOE EXECUTIVE SUITE // TACTICAL A.I."
            PersonaType.EDITH -> "JOE E.D.I.T.H. PROTOCOL // TACTICAL SUITE"
        }

    // Time-aware Personal Greeting
    fun getGreeting(lang: ResponseLanguage): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreetingEng = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Late night hours"
        }

        return when (lang) {
            ResponseLanguage.TANGLISH -> {
                when (currentPersona) {
                    PersonaType.JARVIS -> "$timeGreetingEng boss! Joe here. Unga phone complete control la irukku. Enna command execute pannanum?"
                    PersonaType.EDITH -> "$timeGreetingEng boss. Joe E.D.I.T.H. tactical core ready. Tactical defense and phone controls standing by. Solunga boss!"
                }
            }
            ResponseLanguage.TAMIL -> {
                when (currentPersona) {
                    PersonaType.JARVIS -> "வணக்கம் பாஸ். ஜோ அசிஸ்டன்ட் தயார் நிலையில் உள்ளது. என்ன கட்டளை?"
                    PersonaType.EDITH -> "ஜோ எடித் தற்காப்பு அமைப்பு தயார். ஆப்லைன் மோடில் உங்கள் குரல் கட்டளைக்காக காத்திருக்கிறேன் பாஸ்."
                }
            }
            ResponseLanguage.ENGLISH -> {
                when (currentPersona) {
                    PersonaType.JARVIS -> "$timeGreetingEng, sir. JOE online. All tactical systems nominal. How may I assist you today?"
                    PersonaType.EDITH -> "$timeGreetingEng, boss. JOE E.D.I.T.H. standing by. Tactical defense systems ready for instructions."
                }
            }
        }
    }

    // Human-like Identity Response
    fun getIdentity(lang: ResponseLanguage): String {
        return when (lang) {
            ResponseLanguage.TANGLISH -> {
                when (currentPersona) {
                    PersonaType.JARVIS -> "Naan unga personal AI assistant Joe, boss! Internet illama unga phone call, message, apps, torch, volume ellathayum voice commands la handle pannuven."
                    PersonaType.EDITH -> "Naan unga tactical AI assistant Joe, boss! Spider-Man EDITH maadhiri touchless voice commands moolama unga phone-ah instant-ah operate panna ready-ah iruken."
                }
            }
            ResponseLanguage.TAMIL -> {
                when (currentPersona) {
                    PersonaType.JARVIS -> "நான் ஜோ. உங்கள் போனை முழுமையாக ஆப்லைனில் கட்டுப்படுத்தும் உங்கள் தனிப்பட்ட AI உதவியாளர்."
                    PersonaType.EDITH -> "நான் ஜோ எடித். உங்கள் போனை பாதுகாக்கவும் உங்கள் குரல் கட்டளைகளை ஆப்லைனில் இயக்கவும் உருவாக்கப்பட்ட தற்காப்பு AI பாஸ்."
                }
            }
            ResponseLanguage.ENGLISH -> {
                when (currentPersona) {
                    PersonaType.JARVIS -> "I am JOE, your personal offline artificial intelligence. I manage your phone and execute your voice commands touchlessly without requiring internet."
                    PersonaType.EDITH -> "I am JOE (E.D.I.T.H. Engine), your personal tactical assistant inspired by Spider-Man. Ready to secure and control your mobile device with your voice."
                }
            }
        }
    }

    // Friendly Human-like Status Check
    fun getHowAreYou(lang: ResponseLanguage): String {
        return when (lang) {
            ResponseLanguage.TANGLISH -> {
                "Full power la mass-ah iruken boss! Hardware temperature cool, battery and memory ellam super stable. Neenga epdi irukinga boss?"
            }
            ResponseLanguage.TAMIL -> {
                "முழு திறனில் சிறப்பாக இயங்குகிறேன் பாஸ்! போன் அமைப்புகள் அனைத்தும் நன்று. நீங்கள் எப்படி இருக்கிறீர்கள்?"
            }
            ResponseLanguage.ENGLISH -> {
                "Operating at peak efficiency, sir! Local processors and battery levels are optimal. How are you doing today?"
            }
        }
    }

    // Humor / Easter Egg
    fun getJoke(lang: ResponseLanguage): String {
        return when (lang) {
            ResponseLanguage.TANGLISH -> {
                "Boss, Tony Stark kitta oru joke keten: Internet illama AI vela seiyuma nu... Adhuku nan sonnen: 'JARVIS iruka bayam yen boss!' haha!"
            }
            ResponseLanguage.TAMIL -> {
                "ஒரு முறை டோனி ஸ்டார்க் என்னிடம் கேட்டார், இணையம் இல்லாமல் வேலை செய்ய முடியுமா என்று. நான் சொன்னேன்: 'பாஸ், இது ஜார்விஸ் மார்க் 85!' என்று."
            }
            ResponseLanguage.ENGLISH -> {
                "Tony Stark once asked me if I could run without the cloud. I replied: 'Sir, I don't need a cloud when I have the whole arc reactor right here!'"
            }
        }
    }
}
