/* ==========================================================================
   ALEXA // ECHO SMART VOICE AI & PHONE ASSISTANT (100% OFFLINE)
   Amazon Echo Signature Experience: Cyan/Blue Light Ring & Edge Intelligence
   Full English + Tamil (தமிழ்) + Tanglish On-Device AI Engine
   Features Complete Device Hardware, Settings & Accessibility Voice Controls
   ========================================================================== */

// Current Persona Configuration
let currentPersona = 'alexa_echo'; // 'alexa_echo' or 'alexa_show'
let isListening = false;
let isSpeaking = false;
let recognition = null;
let animationFrameId = null;
let cachedVoices = [];
let audioCtx = null;

// Virtual Phone State
const phoneState = {
  isLocked: false,
  torch: false,
  volume: 65,
  brightness: 75,
  wifi: true,
  bluetooth: true,
  call: 'IDLE',
  battery: 88,
  activeApp: 'HomeScreen',
  timerSeconds: 0,
  musicPlaying: false
};

// Persona Configurations
const personas = {
  alexa_echo: {
    title: 'ALEXA',
    subtitle: 'AMAZON ECHO SUITE',
    version: 'Echo OS v6.0 (100% Offline)',
    greetingEng: 'Hello! I\'m Alexa. Your offline smart assistant is ready for all phone operations and settings. How can I help you?',
    greetingTam: 'வணக்கம்! நான் அலெக்சா. உங்கள் போன் மற்றும் அமைப்புகள் அனைத்தும் ஆப்லைனில் தயாராக உள்ளன. என்ன உதவி செய்ய வேண்டும்?',
    greetingTan: 'Hello! Alexa inga iruken. Unga phone settings and hardware controls 100% offline-la ready. Enna pannanum solunga!',
    wakePrompt: 'Say "Alexa" or tap Echo Ring to speak',
    pitch: 1.02,
    rate: 0.95,
    tag: 'ALEXA (ECHO CORE)'
  },
  alexa_show: {
    title: 'ALEXA',
    subtitle: 'ECHO SHOW PRO',
    version: 'Show OS v6.0 (Smart Hub Edition)',
    greetingEng: 'Echo Show smart hub online. Ready to execute your touchless voice commands for every device setting.',
    greetingTam: 'அலெக்சா எக்கோ ஷோ தயார் நிலையில் உள்ளது. உங்கள் சாதன அமைப்புகளின் கட்டளைகளை நிறைவேற்ற காத்திருக்கிறேன்.',
    greetingTan: 'Echo Show hub ready boss! Phone settings, hardware and device controls 100% offline-la ready. Solunga!',
    wakePrompt: 'Say "Alexa" or tap Echo Ring to speak',
    pitch: 1.04,
    rate: 0.96,
    tag: 'ALEXA (ECHO SHOW)'
  }
};

// ==========================================================================
// 100% Offline Alexa Web Audio Chime Generator
// ==========================================================================
function getAudioContext() {
  if (!audioCtx) {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (AudioContextClass) {
      audioCtx = new AudioContextClass();
    }
  }
  if (audioCtx && audioCtx.state === 'suspended') {
    audioCtx.resume();
  }
  return audioCtx;
}

// Authentic Alexa 2-tone Wake Sound: 784Hz (G5) -> 1046.5Hz (C6)
function playAlexaWakeChime() {
  try {
    const ctx = getAudioContext();
    if (!ctx) return;

    const now = ctx.currentTime;

    // Tone 1: 784 Hz
    const osc1 = ctx.createOscillator();
    const gain1 = ctx.createGain();
    osc1.type = 'sine';
    osc1.frequency.setValueAtTime(784, now);
    gain1.gain.setValueAtTime(0.001, now);
    gain1.gain.exponentialRampToValueAtTime(0.28, now + 0.02);
    gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.14);

    osc1.connect(gain1);
    gain1.connect(ctx.destination);
    osc1.start(now);
    osc1.stop(now + 0.15);

    // Tone 2: 1046.5 Hz (Higher pleasant tone)
    const osc2 = ctx.createOscillator();
    const gain2 = ctx.createGain();
    osc2.type = 'sine';
    osc2.frequency.setValueAtTime(1046.5, now + 0.11);
    gain2.gain.setValueAtTime(0.001, now + 0.11);
    gain2.gain.exponentialRampToValueAtTime(0.32, now + 0.13);
    gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.32);

    osc2.connect(gain2);
    gain2.connect(ctx.destination);
    osc2.start(now + 0.11);
    osc2.stop(now + 0.34);
  } catch (err) {
    console.warn('Audio chime warning:', err);
  }
}

// Confirmation Soft Tone
function playAlexaSuccessChime() {
  try {
    const ctx = getAudioContext();
    if (!ctx) return;
    const now = ctx.currentTime;

    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(1046.5, now);
    osc.frequency.exponentialRampToValueAtTime(1318.5, now + 0.08); // C6 -> E6
    gain.gain.setValueAtTime(0.001, now);
    gain.gain.exponentialRampToValueAtTime(0.22, now + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.22);

    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start(now);
    osc.stop(now + 0.24);
  } catch (err) {
    console.warn('Success chime warning:', err);
  }
}

// ==========================================================================
// Voice Engine Preloader (Ensures Alexa Natural Speech Cadence)
// ==========================================================================
function preloadVoices() {
  if ('speechSynthesis' in window) {
    cachedVoices = window.speechSynthesis.getVoices();
    window.speechSynthesis.onvoiceschanged = () => {
      cachedVoices = window.speechSynthesis.getVoices();
    };
  }
}

// Find clear, natural female Alexa-style voice
function getAlexaVoice(lang) {
  if (!cachedVoices || cachedVoices.length === 0) {
    if ('speechSynthesis' in window) cachedVoices = window.speechSynthesis.getVoices();
  }

  if (lang === 'tamil') {
    const tamilVoice = cachedVoices.find(v => v.lang.includes('ta') || v.lang.includes('ta-IN'));
    if (tamilVoice) return tamilVoice;
  }

  // Indian English gives natural Tanglish inflection
  if (lang === 'tanglish') {
    const indianVoice = cachedVoices.find(v => (v.lang === 'en-IN' || v.lang.includes('IN')) && (v.name.includes('Female') || v.name.includes('Zira') || v.name.includes('Google') || v.name.includes('Heera')));
    if (indianVoice) return indianVoice;
  }

  // Authentic English Alexa fallback
  const preferredNames = ['Google UK English Female', 'Microsoft Zira', 'Samantha', 'Karen', 'Victoria', 'Moira'];
  for (const name of preferredNames) {
    const match = cachedVoices.find(v => v.name.includes(name));
    if (match) return match;
  }

  return cachedVoices.find(v => v.lang.startsWith('en')) || null;
}

// ==========================================================================
// App Initialization
// ==========================================================================
window.addEventListener('DOMContentLoaded', () => {
  preloadVoices();
  initCanvasVisualizer();
  initClock();
  initSpeechRecognition();
  updatePhoneUI();

  // Register service worker for 100% offline usage
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js')
      .then(() => logTicker('Alexa Offline Service Worker active & cached.'))
      .catch(err => console.log('SW registration error:', err));
  }
});

function initClock() {
  const clockEl = document.getElementById('phone-clock');
  const lockClockEl = document.getElementById('lock-time');
  function updateTime() {
    const now = new Date();
    const hours = String(now.getHours()).padStart(2, '0');
    const mins = String(now.getMinutes()).padStart(2, '0');
    const timeStr = `${hours}:${mins}`;
    if (clockEl) clockEl.innerText = timeStr;
    if (lockClockEl) lockClockEl.innerText = timeStr;
  }
  updateTime();
  setInterval(updateTime, 1000);
}

// ==========================================================================
// Persona Switching (Alexa Echo <-> Alexa Show)
// ==========================================================================
function switchPersona(personaName) {
  currentPersona = personaName;
  const body = document.body;
  const btnEcho = document.getElementById('btn-alexa-echo');
  const btnShow = document.getElementById('btn-alexa-show');
  const titleEl = document.getElementById('persona-title');
  const aiTag = document.getElementById('ai-tag');
  const wakePrompt = document.getElementById('wake-prompt');

  playAlexaSuccessChime();

  if (personaName === 'alexa_echo') {
    body.className = 'theme-alexa-echo';
    if (btnShow) btnShow.classList.remove('active');
    if (btnEcho) btnEcho.classList.add('active');
    if (titleEl) titleEl.innerHTML = `ALEXA <span>${personas.alexa_echo.version}</span>`;
    if (aiTag) aiTag.innerText = personas.alexa_echo.tag;
    if (wakePrompt) wakePrompt.innerText = personas.alexa_echo.wakePrompt;
    speakResponse(personas.alexa_echo.greetingTan, 'tanglish');
    logTicker('Switched to Alexa Echo Core.');
  } else {
    body.className = 'theme-alexa-show';
    if (btnEcho) btnEcho.classList.remove('active');
    if (btnShow) btnShow.classList.add('active');
    if (titleEl) titleEl.innerHTML = `ALEXA <span>${personas.alexa_show.version}</span>`;
    if (aiTag) aiTag.innerText = personas.alexa_show.tag;
    if (wakePrompt) wakePrompt.innerText = personas.alexa_show.wakePrompt;
    speakResponse(personas.alexa_show.greetingTan, 'tanglish');
    logTicker('Switched to Alexa Echo Show Hub.');
  }
}

// ==========================================================================
// Language Detector: Pure Tamil, Tanglish, or English
// ==========================================================================
function detectLanguage(input) {
  if (/[\u0B80-\u0BFF]/.test(input)) {
    return 'tamil';
  }
  const lower = input.toLowerCase();
  const tanglishMarkers = [
    'podu', 'pannu', 'kootu', 'kurai', 'korai', 'anupu', 'sollu', 'solunga',
    'iruka', 'irukinga', 'irukku', 'evalo', 'evvalavu', 'enna', 'yaaru', 'ne',
    'inga', 'anga', 'amma ku', 'appa ku', 'ku call', 'ah', 'romba', 'nalla', 'unlock', 'vai'
  ];
  if (tanglishMarkers.some(word => lower.includes(word))) {
    return 'tanglish';
  }
  return 'english';
}

// ==========================================================================
// Complete Offline Natural Language Intent Router
// ==========================================================================
function processCommand(rawInput) {
  const input = rawInput.trim();
  // Strip wake words if present (Alexa, Hey Alexa, Echo, Amazon)
  const cleaned = input
    .replace(/^(alexa|hey alexa|echo|amazon|அலெக்சா|ஹேய் அலெக்சா)\s*[,:]?\s*/i, '')
    .trim();

  const lower = cleaned.toLowerCase();
  const userTranscriptEl = document.getElementById('user-transcript');
  if (userTranscriptEl) userTranscriptEl.innerText = `"${input}"`;

  logTicker(`Processing voice command: "${input}"`);

  const lang = detectLanguage(cleaned.length > 0 ? cleaned : input);

  // If user only says "Alexa" or greeting
  if (lower === '' || lower === 'alexa' || lower === 'hello' || lower === 'hi' || lower === 'hey' || lower === 'vanakkam') {
    playAlexaWakeChime();
    const reply = lang === 'tanglish'
      ? 'Solunga boss, Alexa inga iruken! Enna help venum?'
      : (lang === 'tamil' ? 'வணக்கம், நான் அலெக்சா. உங்களுக்கு என்ன உதவி தேவை?' : 'Hello! I\'m listening. How can I help with your device?');
    speakResponse(reply, lang);
    return;
  }

  // 1. TOUCHLESS PHONE UNLOCK
  if (
    lower.includes('unlock phone') || lower.includes('phone ah unlock pannu') ||
    lower.includes('unlock pannu') || lower.includes('screen ah open pannu') ||
    lower.includes('போனை அன்லாக் பண்ணு') || lower.includes('திரையை திற') ||
    lower.includes('wake up and unlock') || lower.includes('unlock')
  ) {
    phoneState.isLocked = false;
    updatePhoneUI();
    const reply = lang === 'tanglish'
      ? 'Phone-ah touchless-ah unlock pannitten!'
      : (lang === 'tamil' ? 'போன் திரை திறக்கப்பட்டது.' : 'Sure, unlocking your phone touchlessly.');
    triggerPhoneAction('Touchless Unlock', 'Automated Screen Unlock Dispatched', '🔓');
    speakResponse(reply, lang);
    return;
  }

  // 2. LOCK PHONE
  if (
    lower.includes('lock phone') || lower.includes('phone ah lock pannu') ||
    lower.includes('lock pannu') || lower.includes('போனை லாக் பண்ணு') || lower.includes('lock')
  ) {
    phoneState.isLocked = true;
    updatePhoneUI();
    const reply = lang === 'tanglish'
      ? "Phone display-ah lock pannitten. 'Alexa' nu sonna wake aaguven."
      : (lang === 'tamil' ? 'போன் திரை லாக் செய்யப்பட்டது.' : 'Device display locked. Say "Alexa" anytime to wake.');
    triggerPhoneAction('Security Lock', 'Accessibility: Screen Locked', '🔒');
    speakResponse(reply, lang);
    return;
  }

  // 3. FLASHLIGHT / TORCH
  if (
    lower.includes('torch podu') || lower.includes('டார்ச் போடு') || lower.includes('டார்ச் ஆன்') ||
    lower.includes('torch on') || lower.includes('turn on flashlight') || lower.includes('light on') ||
    lower.includes('light podu') || lower.includes('torch ah on pannu') || lower.includes('torch on pannu')
  ) {
    phoneState.torch = true;
    updatePhoneUI();
    const reply = lang === 'tanglish'
      ? 'Sure! Torch on pannitten.'
      : (lang === 'tamil' ? 'டார்ச் ஆன் செய்யப்பட்டது.' : 'Sure, turning on the flashlight.');
    triggerPhoneAction('Flashlight', 'Camera Torch ON', '🔦');
    speakResponse(reply, lang);
    return;
  }

  if (
    lower.includes('torch off') || lower.includes('டார்ச் ஆப்') || lower.includes('டார்ச் அணை') ||
    lower.includes('turn off flashlight') || lower.includes('light off') || lower.includes('torch ah off pannu') ||
    lower.includes('torch off pannu') || lower.includes('light anai')
  ) {
    phoneState.torch = false;
    updatePhoneUI();
    const reply = lang === 'tanglish'
      ? 'Flashlight off pannitten.'
      : (lang === 'tamil' ? 'டார்ச் ஆப் செய்யப்பட்டது.' : 'Flashlight is turned off.');
    triggerPhoneAction('Flashlight', 'Camera Torch OFF', '🔦');
    speakResponse(reply, lang);
    return;
  }

  // 4. SCREEN BRIGHTNESS CONTROLS
  if (lower.includes('brightness max') || lower.includes('full brightness') || lower.includes('முழு வெளிச்சம்')) {
    phoneState.brightness = 100;
    updatePhoneUI();
    const reply = lang === 'tanglish'
      ? 'Brightness full max 100 percent-kku potuten!'
      : (lang === 'tamil' ? 'முழு வெளிச்சம் அமைக்கப்பட்டது.' : 'Screen brightness set to maximum 100 percent.');
    triggerPhoneAction('Brightness Max', 'Display: 100%', '🔆');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('brightness kootu') || lower.includes('increase brightness') || lower.includes('வெளிச்சம் கூட்டு') || lower.includes('brightness up')) {
    phoneState.brightness = Math.min(100, phoneState.brightness + 25);
    updatePhoneUI();
    const reply = lang === 'tanglish'
      ? `Brightness ${phoneState.brightness} percent-kku kootitten.`
      : (lang === 'tamil' ? `வெளிச்சம் ${phoneState.brightness} சதவீதமாக அதிகரிக்கப்பட்டது.` : `Brightness increased to ${phoneState.brightness} percent.`);
    triggerPhoneAction('Brightness Up', `Display: ${phoneState.brightness}%`, '🔆');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('brightness kurai') || lower.includes('brightness korai') || lower.includes('decrease brightness') || lower.includes('reduce brightness') || lower.includes('வெளிச்சம் குறை') || lower.includes('brightness down')) {
    phoneState.brightness = Math.max(10, phoneState.brightness - 25);
    updatePhoneUI();
    const reply = lang === 'tanglish'
      ? `Brightness ${phoneState.brightness} percent-kku korachutten.`
      : (lang === 'tamil' ? `வெளிச்சம் ${phoneState.brightness} சதவீதமாக குறைக்கப்பட்டது.` : `Brightness decreased to ${phoneState.brightness} percent.`);
    triggerPhoneAction('Brightness Down', `Display: ${phoneState.brightness}%`, '🔅');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('brightness') || lower.includes('வெளிச்சம்')) {
    const match = lower.match(/(\d+)/);
    const target = match ? parseInt(match[1]) : 75;
    phoneState.brightness = Math.min(100, Math.max(0, target));
    updatePhoneUI();
    const reply = lang === 'tanglish'
      ? `Brightness ${phoneState.brightness} percent-kku set pannitten.`
      : (lang === 'tamil' ? `வெளிச்சம் ${phoneState.brightness} சதவீதத்தில் அமைக்கப்பட்டது.` : `Brightness set to ${phoneState.brightness} percent.`);
    triggerPhoneAction('Brightness', `Display: ${phoneState.brightness}%`, '🔆');
    speakResponse(reply, lang);
    return;
  }

  // 5. CAMERA & PHOTOS
  if (lower.includes('take photo') || lower.includes('photo edu') || lower.includes('போட்டோ எடு') || lower.includes('take a picture') || lower.includes('click photo')) {
    phoneState.activeApp = 'Camera Shutter';
    const reply = lang === 'tanglish'
      ? 'Camera open panni photo capture mode potuten!'
      : (lang === 'tamil' ? 'கேமரா திறக்கப்பட்டது, படம் எடுக்கலாம்.' : 'Opening camera in photo capture mode.');
    triggerPhoneAction('Camera Capture', 'Taking Photo...', '📸');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('record video') || lower.includes('video edu') || lower.includes('வீடியோ எடு')) {
    phoneState.activeApp = 'Video Recorder';
    const reply = lang === 'tanglish'
      ? 'Video recording mode open panren!'
      : (lang === 'tamil' ? 'வீடியோ பதிவு முறை திறக்கப்படுகிறது.' : 'Starting video recording mode.');
    triggerPhoneAction('Video Camera', 'Recording Ready', '🎥');
    speakResponse(reply, lang);
    return;
  }

  // 6. MEDIA PLAYBACK
  if (lower.includes('play music') || lower.includes('song play') || lower.includes('பாட்டு போடு') || lower.includes('resume music')) {
    phoneState.musicPlaying = true;
    const reply = lang === 'tanglish'
      ? 'Music playback start panren.'
      : (lang === 'tamil' ? 'பாடல் இயக்கப்படுகிறது.' : 'Playing media.');
    triggerPhoneAction('Media Player', 'Playing Track...', '🎵');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('pause music') || lower.includes('stop music') || lower.includes('பாட்டை நிறுத்து') || lower.includes('song pause')) {
    phoneState.musicPlaying = false;
    const reply = lang === 'tanglish'
      ? 'Music pause pannitten.'
      : (lang === 'tamil' ? 'பாடல் நிறுத்தப்பட்டது.' : 'Media paused.');
    triggerPhoneAction('Media Player', 'Paused', '⏸️');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('next song') || lower.includes('next track') || lower.includes('அடுத்த பாடல்')) {
    const reply = lang === 'tanglish'
      ? 'Next song-ku skip panren.'
      : (lang === 'tamil' ? 'அடுத்த பாடலுக்கு செல்லப்பட்டது.' : 'Skipping to next track.');
    triggerPhoneAction('Media Player', 'Next Track >>', '⏭️');
    speakResponse(reply, lang);
    return;
  }

  // 7. PHONE CALLS
  if (lower.includes('call pannu') || lower.includes('கால் பண்ணு') || lower.includes('call') || lower.includes('dial') || lower.includes('phone pannu')) {
    let contact = 'Contact';
    if (lang === 'tamil') {
      const matchTam = cleaned.match(/(.*?)(?:-க்கு|க்கு|\s+)கால்\s*பண்ணு/);
      if (matchTam && matchTam[1]) contact = matchTam[1].replace(/^(நீ|தயவுசெய்து)\s*/, '').trim();
    } else if (lang === 'tanglish') {
      const matchTan1 = lower.match(/(?:call\s+pannu|phone\s+pannu)\s+([a-zA-Z0-9]+)/);
      const matchTan2 = lower.match(/([a-zA-Z0-9]+)\s*(?:-?ku|-?kku)?\s*(?:call|phone)\s*pannu/);
      if (matchTan1) contact = matchTan1[1];
      else if (matchTan2) contact = matchTan2[1];
    } else {
      const matchEng = lower.match(/(?:call|dial|phone)\s+([a-zA-Z0-9\s]+)/);
      if (matchEng && matchEng[1]) contact = matchEng[1].trim();
    }

    phoneState.call = `Calling ${contact}`;
    updatePhoneUI();
    const reply = lang === 'tanglish'
      ? `${contact}-kku ippo call panren.`
      : (lang === 'tamil' ? `${contact}-க்கு இப்போது கால் செய்கிறேன்.` : `Calling ${contact} now.`);
    triggerPhoneAction(`Call: ${contact}`, 'Dialing via Telecom Service...', '📞');
    speakResponse(reply, lang);
    return;
  }

  // 8. WHATSAPP & SMS MESSAGING
  if (lower.includes('message') || lower.includes('மெசேஜ்') || lower.includes('whatsapp') || lower.includes('வாட்ஸ்அப்') || lower.includes('anupu')) {
    let recipient = 'Contact';
    let msg = 'Hello!';

    if (cleaned.includes(':')) {
      const parts = cleaned.split(':');
      msg = parts[1].trim();
      const leftPart = parts[0];
      const match = leftPart.match(/(?:to|anupu|அனுப்பு|க்கு|ku)\s*([a-zA-Z\u0B80-\u0BFF\s]+)/i);
      if (match && match[1]) recipient = match[1].trim();
    } else {
      recipient = lang === 'english' ? 'Alex' : 'ராமு';
      msg = lang === 'tanglish' ? 'Naan ippo kelambiten' : (lang === 'tamil' ? 'நான் கிளம்பிட்டேன்' : 'I will arrive shortly');
    }

    const reply = lang === 'tanglish'
      ? `${recipient}-kku message anupitten: "${msg}"`
      : (lang === 'tamil' ? `${recipient}-க்கு மெசேஜ் அனுப்பப்பட்டது.` : `Message sent to ${recipient}: "${msg}"`);
    triggerPhoneAction(`Message to ${recipient}`, `"${msg}"`, '💬');
    speakResponse(reply, lang);
    return;
  }

  // 9. SETTINGS SHORTCUTS & PANELS
  if (lower.includes('wifi') || lower.includes('வைபை')) {
    const reply = lang === 'tanglish'
      ? 'Wi-Fi settings open panren.'
      : (lang === 'tamil' ? 'வைஃபை அமைப்புகள் திறக்கப்படுகிறது.' : 'Opening Wi-Fi settings.');
    triggerPhoneAction('Settings', 'Wi-Fi Control Panel', '📶');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('bluetooth') || lower.includes('புளூடூத்')) {
    const reply = lang === 'tanglish'
      ? 'Bluetooth settings open panren.'
      : (lang === 'tamil' ? 'புளூடூத் அமைப்புகள் திறக்கப்படுகிறது.' : 'Opening Bluetooth settings.');
    triggerPhoneAction('Settings', 'Bluetooth Panel', 'ᛒ');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('hotspot') || lower.includes('ஹாட்ஸ்பாட்')) {
    const reply = lang === 'tanglish'
      ? 'Hotspot & Tethering settings open panren.'
      : (lang === 'tamil' ? 'ஹாட்ஸ்பாட் அமைப்புகள் திறக்கப்படுகிறது.' : 'Opening Hotspot and Tethering settings.');
    triggerPhoneAction('Settings', 'Tethering & Hotspot', '📡');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('display settings') || lower.includes('screen settings') || lower.includes('திரை அமைப்புகள்')) {
    const reply = lang === 'tanglish'
      ? 'Display settings open panren.'
      : (lang === 'tamil' ? 'திரை அமைப்புகள் திறக்கப்படுகிறது.' : 'Opening Display settings.');
    triggerPhoneAction('Settings', 'Display & Brightness', '📱');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('sound settings') || lower.includes('ஒலி அமைப்புகள்')) {
    const reply = lang === 'tanglish'
      ? 'Sound and vibration settings open panren.'
      : (lang === 'tamil' ? 'ஒலி அமைப்புகள் திறக்கப்படுகிறது.' : 'Opening Sound and Vibration settings.');
    triggerPhoneAction('Settings', 'Sound & Haptics', '🔔');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('location') || lower.includes('gps') || lower.includes('இருப்பிடம்')) {
    const reply = lang === 'tanglish'
      ? 'GPS Location settings open panren.'
      : (lang === 'tamil' ? 'இருப்பிட அமைப்புகள் திறக்கப்படுகிறது.' : 'Opening GPS Location settings.');
    triggerPhoneAction('Settings', 'Location & GPS', '📍');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('airplane mode') || lower.includes('flight mode') || lower.includes('விமான நிலை')) {
    const reply = lang === 'tanglish'
      ? 'Airplane mode settings open panren.'
      : (lang === 'tamil' ? 'விமான நிலை அமைப்புகள் திறக்கப்படுகிறது.' : 'Opening Airplane mode settings.');
    triggerPhoneAction('Settings', 'Airplane Mode', '✈️');
    speakResponse(reply, lang);
    return;
  }

  // 10. SYSTEM NAVIGATION & ACCESSIBILITY SHORTCUTS
  if (lower.includes('screenshot') || lower.includes('ஸ்கிரீன்ஷாட்') || lower.includes('படம் எடு')) {
    const reply = lang === 'tanglish'
      ? 'Screenshot eduthuten!'
      : (lang === 'tamil' ? 'திரைப்படம் எடுக்கப்பட்டது.' : 'Screenshot captured successfully.');
    triggerPhoneAction('Accessibility', 'Screenshot Captured', '📸');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('quick settings') || lower.includes('control center') || lower.includes('குயிக் செட்டிங்ஸ்')) {
    const reply = lang === 'tanglish'
      ? 'Quick settings control panel pull panren.'
      : (lang === 'tamil' ? 'விரைவு அமைப்புகள் குழு திறக்கப்படுகிறது.' : 'Pulling down Quick Settings.');
    triggerPhoneAction('Accessibility', 'Quick Settings Shade', '🎛️');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('notification') || lower.includes('நோட்டிபிகேஷன்')) {
    const reply = lang === 'tanglish'
      ? 'Notifications open panren.'
      : (lang === 'tamil' ? 'அறிவிப்புகள் திறக்கப்படுகிறது.' : 'Opening notifications panel.');
    triggerPhoneAction('Accessibility', 'Notifications Shade', '🔔');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('recent') || lower.includes('app switcher') || lower.includes('recents')) {
    const reply = lang === 'tanglish'
      ? 'Recent apps switcher open panren.'
      : (lang === 'tamil' ? 'சமீபத்திய செயலிகள் திறக்கப்படுகிறது.' : 'Opening recent apps switcher.');
    triggerPhoneAction('Accessibility', 'Recent Applications', '🔲');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('home') || lower.includes('முகப்பு')) {
    phoneState.activeApp = 'HomeScreen';
    const reply = lang === 'tanglish'
      ? 'Home screen-ku vandhutom.'
      : (lang === 'tamil' ? 'முகப்பு திரைக்கு செல்லப்பட்டது.' : 'Navigated to Home Screen.');
    triggerPhoneAction('Navigation', 'Home Screen', '🏠');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('back') || lower.includes('பின்னாடி')) {
    const reply = lang === 'tanglish'
      ? 'Back poitten.'
      : (lang === 'tamil' ? 'பின்செல்லப்பட்டது.' : 'Navigated back.');
    triggerPhoneAction('Navigation', 'Go Back', '⬅️');
    speakResponse(reply, lang);
    return;
  }

  // 11. APP LAUNCHING
  if (lower.includes('open') || lower.includes('ஓபன்') || lower.includes('launch') || lower.includes('start') || lower.includes('thira') || lower.includes('open pannu')) {
    let app = 'App';
    if (lower.includes('youtube') || lower.includes('யூடியூப்')) app = 'YouTube';
    else if (lower.includes('camera') || lower.includes('கேமரா')) app = 'Camera';
    else if (lower.includes('whatsapp') || lower.includes('வாட்ஸ்அப்')) app = 'WhatsApp';
    else if (lower.includes('instagram') || lower.includes('insta')) app = 'Instagram';
    else if (lower.includes('spotify') || lower.includes('music') || lower.includes('ஸ்பாடிஃபை')) app = 'Spotify';
    else if (lower.includes('settings') || lower.includes('செட்டிங்ஸ்')) app = 'Settings';
    else if (lower.includes('maps') || lower.includes('மேப்ஸ்')) app = 'Maps';
    else if (lower.includes('calculator') || lower.includes('கால்குலேட்டர்')) app = 'Calculator';
    else {
      const match = lower.match(/(?:open|launch|start|open pannu)\s+([a-zA-Z]+)/);
      if (match && match[1]) app = match[1];
    }

    phoneState.activeApp = app;
    const reply = lang === 'tanglish'
      ? `${app} open panren.`
      : (lang === 'tamil' ? `${app} அப்ளிகேஷன் திறக்கப்படுகிறது.` : `Opening ${app} on your device.`);
    triggerPhoneAction(`Launch: ${app}`, 'Application Dispatched', '🚀');
    speakResponse(reply, lang);
    return;
  }

  // 12. VOLUME CONTROLS
  if (lower.includes('kootu') || lower.includes('சத்தத்தை கூட்டு') || lower.includes('volume up') || lower.includes('increase volume')) {
    phoneState.volume = Math.min(100, phoneState.volume + 15);
    updatePhoneUI();
    const reply = lang === 'tanglish'
      ? `Sound ${phoneState.volume} percent-kku kootitten.`
      : (lang === 'tamil' ? `சத்தம் அதிகரிக்கப்பட்டது: ${phoneState.volume} சதவீதம்.` : `Audio volume set to ${phoneState.volume} percent.`);
    triggerPhoneAction('Volume Up', `Audio set to ${phoneState.volume}%`, '🔊');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('korai') || lower.includes('kurai') || lower.includes('சத்தத்தை குறை') || lower.includes('volume down') || lower.includes('decrease volume')) {
    phoneState.volume = Math.max(0, phoneState.volume - 15);
    updatePhoneUI();
    const reply = lang === 'tanglish'
      ? `Sound ${phoneState.volume} percent-kku korachutten.`
      : (lang === 'tamil' ? `சத்தம் குறைக்கப்பட்டது: ${phoneState.volume} சதவீதம்.` : `Audio volume reduced to ${phoneState.volume} percent.`);
    triggerPhoneAction('Volume Down', `Audio set to ${phoneState.volume}%`, '🔉');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('mute') || lower.includes('silent') || lower.includes('சைலன்ட்')) {
    phoneState.volume = 0;
    updatePhoneUI();
    const reply = lang === 'tanglish'
      ? 'Phone silent mode-la potuten.'
      : (lang === 'tamil' ? 'போன் சைலன்ட் மோடில் வைக்கப்பட்டது.' : 'Device muted.');
    triggerPhoneAction('Silent Mode', 'Volume Muted', '🔇');
    speakResponse(reply, lang);
    return;
  }

  // 13. BATTERY & STORAGE & TIME & DATE
  if (lower.includes('storage') || lower.includes('memory') || lower.includes('மெமரி')) {
    const reply = lang === 'tanglish'
      ? 'Phone storage-la 42 GB free space irukku.'
      : (lang === 'tamil' ? 'உள் நினைவகத்தில் 42 GB இடம் காலியாக உள்ளது.' : 'You have 42 GB of free internal storage remaining.');
    triggerPhoneAction('Storage Check', '42 GB Available', '💾');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('battery') || lower.includes('பேட்டரி') || lower.includes('charge') || lower.includes('evalo') || lower.includes('evvalavu')) {
    const reply = lang === 'tanglish'
      ? `Battery ippo ${phoneState.battery} percent irukku.`
      : (lang === 'tamil' ? `பேட்டரி அளவு ${phoneState.battery} சதவீதம் உள்ளது.` : `Your phone battery is at ${phoneState.battery} percent.`);
    triggerPhoneAction('Battery Level', `${phoneState.battery}% remaining`, '🔋');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('time') || lower.includes('நேரம்') || lower.includes('மணி')) {
    const now = new Date();
    const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const reply = lang === 'tanglish'
      ? `Ippo time ${timeStr}.`
      : (lang === 'tamil' ? `இப்போது நேரம் ${timeStr}.` : `The time is currently ${timeStr}.`);
    triggerPhoneAction('Time Check', timeStr, '⏱️');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('date') || lower.includes('தேதி') || lower.includes('inaikku')) {
    const now = new Date();
    const dateStr = now.toLocaleDateString([], { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
    const reply = lang === 'tanglish'
      ? `Inaikku date ${dateStr}.`
      : (lang === 'tamil' ? `இன்றைய தேதி ${dateStr}.` : `Today is ${dateStr}.`);
    triggerPhoneAction('Date Check', dateStr, '📅');
    speakResponse(reply, lang);
    return;
  }

  // 14. TIMER & ALARM
  if (lower.includes('timer') || lower.includes('டைமர்')) {
    const match = lower.match(/(\d+)/);
    const mins = match ? match[1] : '5';
    const reply = lang === 'tanglish'
      ? `${mins} minutes timer start pannitten!`
      : (lang === 'tamil' ? `${mins} நிமிடங்களுக்கான டைமர் தொடங்கப்பட்டது.` : `${mins} minute timer set, starting now.`);
    triggerPhoneAction('Timer Active', `${mins} Minutes Countdown`, '⏱️');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('alarm') || lower.includes('அலாரம்')) {
    const match = lower.match(/(\d+)/);
    const hour = match ? match[1] : '7';
    const reply = lang === 'tanglish'
      ? `Kaalai ${hour} manikku alarm set pannitten!`
      : (lang === 'tamil' ? `காலை ${hour} மணிக்கு அலாரம் அமைக்கப்பட்டது.` : `Alarm set for ${hour}:00 AM.`);
    triggerPhoneAction('Alarm Set', `${hour}:00 AM`, '⏰');
    speakResponse(reply, lang);
    return;
  }

  // 15. ALEXA BANTER: "How are you?" / "epdi iruka?"
  if (lower.includes('epdi iruka') || lower.includes('how are you') || lower.includes('எப்படி இருக்க')) {
    const reply = lang === 'tanglish'
      ? 'Alexa full active-ah super-ah iruken! Device completely offline-la smooth-ah run aagudhu. Neenga epdi irukinga?'
      : (lang === 'tamil' ? 'நான் மிகவும் சிறப்பாக இருக்கிறேன்! நீங்கள் எப்படி இருக்கிறீர்கள்?' : 'I\'m doing great, thank you! Everything is running smoothly and 100% offline.');
    speakResponse(reply, lang);
    return;
  }

  // 16. ALEXA HUMOR / JOKES
  if (lower.includes('joke') || lower.includes('ஜோக்')) {
    const jokes = {
      tanglish: 'Oru computer doc kitta pochu... doc ketaru: "Enna aachu?"... Computer sonnuchu: "Enakku virus pidichurukku!" haha!',
      tamil: 'ஒரு கம்ப்யூட்டர் மருத்துவரிடம் சென்றது. மருத்துவர் கேட்டார்: என்ன பிரச்சனை? கம்ப்யூட்டர் சொன்னது: எனக்கு வைரஸ் காய்ச்சல்!',
      english: 'Why was the mobile phone wearing glasses? Because it lost its contacts!'
    };
    const reply = jokes[lang] || jokes.english;
    speakResponse(reply, lang);
    return;
  }

  // 17. IDENTITY: "ne yaaru?" / "who are you?" / "about alexa"
  if (lower.includes('ne yaaru') || lower.includes('who are you') || lower.includes('நீ யாரு') || lower.includes('about alexa')) {
    const reply = lang === 'tanglish'
      ? 'Naan unga personal AI assistant Alexa! Internet thevai illama unga phone calls, apps, torch, volume, brightness, settings, touchless unlock ellathayum handle pannuven.'
      : (lang === 'tamil'
        ? 'நான் அலெக்சா. இணையம் இல்லாமல் உங்கள் போனை முழுமையாகக் கட்டுப்படுத்தும் உங்கள் தனிப்பட்ட ஏஐ உதவியாளர்.'
        : 'I am Alexa, your personal offline AI assistant. I help you control all device settings, hardware, volume, apps, dial calls, and manage tasks touchlessly without internet.');
    speakResponse(reply, lang);
    return;
  }

  // DEFAULT ALEXA FALLBACK
  const defaultReply = lang === 'tanglish'
    ? `Neenga sonnadhu kettuchu: "${cleaned}". Alexa offline-la execute panren!`
    : (lang === 'tamil' ? `உங்கள் கட்டளை "${cleaned}" பெறப்பட்டது.` : `I heard "${cleaned}". Executing on your device.`);
  triggerPhoneAction('Voice Command', cleaned, '🔵');
  speakResponse(defaultReply, lang);
}

// ==========================================================================
// Speech Synthesis (TTS) - Alexa Natural Female Voice
// ==========================================================================
function speakResponse(text, lang = 'english') {
  const aiResponseEl = document.getElementById('ai-response');
  const echoCore = document.getElementById('echo-reactor');
  const lightRing = document.getElementById('echo-light-ring');
  const speechState = document.getElementById('speech-state');

  if (aiResponseEl) aiResponseEl.innerText = text;
  if (echoCore) echoCore.classList.add('speaking');
  if (lightRing) lightRing.classList.add('speaking');
  if (speechState) speechState.innerText = `ALEXA SPEAKING (${lang.toUpperCase()})`;
  isSpeaking = true;

  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);

    // Alexa Voice Modulation: Warm, courteous, articulate
    utterance.rate = personas[currentPersona].rate || 0.95;
    utterance.pitch = personas[currentPersona].pitch || 1.02;
    utterance.volume = 1.0;

    const matchedVoice = getAlexaVoice(lang);
    if (matchedVoice) {
      utterance.voice = matchedVoice;
      utterance.lang = matchedVoice.lang;
    } else {
      if (lang === 'tamil') utterance.lang = 'ta-IN';
      else if (lang === 'tanglish') utterance.lang = 'en-IN';
      else utterance.lang = 'en-US';
    }

    utterance.onend = () => {
      isSpeaking = false;
      if (echoCore) echoCore.classList.remove('speaking');
      if (lightRing) lightRing.classList.remove('speaking');
      if (speechState) speechState.innerText = isListening ? 'LISTENING (MIC ACTIVE)' : 'ALEXA STANDBY';
    };

    utterance.onerror = () => {
      isSpeaking = false;
      if (echoCore) echoCore.classList.remove('speaking');
      if (lightRing) lightRing.classList.remove('speaking');
      if (speechState) speechState.innerText = 'ALEXA STANDBY';
    };

    setTimeout(() => {
      window.speechSynthesis.speak(utterance);
    }, 60);
  } else {
    setTimeout(() => {
      isSpeaking = false;
      if (echoCore) echoCore.classList.remove('speaking');
      if (lightRing) lightRing.classList.remove('speaking');
      if (speechState) speechState.innerText = 'ALEXA STANDBY';
    }, 2000);
  }
}

// ==========================================================================
// Speech Recognition (Web Speech API)
// ==========================================================================
function initSpeechRecognition() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) {
    logTicker('Web Speech API not supported. Use quick test buttons or manual prompt.');
    return;
  }

  recognition = new SpeechRecognition();
  recognition.continuous = true;
  recognition.interimResults = false;
  recognition.lang = 'en-IN'; // en-IN handles English & Tanglish fluidly

  recognition.onstart = () => {
    isListening = true;
    updateMicVisualState(true);
    playAlexaWakeChime();
    logTicker('Microphone listening. Say "Alexa" or a command.');
  };

  recognition.onresult = (event) => {
    const lastResultIndex = event.results.length - 1;
    const transcript = event.results[lastResultIndex][0].transcript.trim();
    if (transcript) {
      playAlexaSuccessChime();
      processCommand(transcript);
    }
  };

  recognition.onerror = (e) => {
    console.log('Recognition error:', e);
    updateMicVisualState(false);
  };

  recognition.onend = () => {
    if (isListening) {
      try { recognition.start(); } catch (e) { isListening = false; updateMicVisualState(false); }
    } else {
      updateMicVisualState(false);
    }
  };
}

function triggerMic() {
  playAlexaWakeChime();

  if (!recognition) {
    simulateVoice('Alexa, brightness kootu');
    return;
  }

  if (isListening) {
    isListening = false;
    recognition.stop();
    updateMicVisualState(false);
  } else {
    try {
      recognition.start();
    } catch (e) {
      console.log('Already listening');
    }
  }
}

function updateMicVisualState(active) {
  const echoCore = document.getElementById('echo-reactor');
  const lightRing = document.getElementById('echo-light-ring');
  const speechState = document.getElementById('speech-state');
  const micFill = document.getElementById('mic-fill');
  const statAudio = document.getElementById('stat-audio-status');

  if (active) {
    if (echoCore) echoCore.classList.add('listening');
    if (lightRing) lightRing.classList.add('listening');
    if (speechState) speechState.innerText = 'LISTENING (MIC ACTIVE)';
    if (micFill) micFill.style.width = '85%';
    if (statAudio) statAudio.innerText = 'Listening for "Alexa"...';
  } else {
    if (echoCore) echoCore.classList.remove('listening');
    if (lightRing) lightRing.classList.remove('listening');
    if (speechState) speechState.innerText = 'ALEXA STANDBY';
    if (micFill) micFill.style.width = '20%';
    if (statAudio) statAudio.innerText = 'Mic Ready';
  }
}

// ==========================================================================
// Simulation Helpers
// ==========================================================================
function simulateVoice(commandText) {
  playAlexaWakeChime();
  setTimeout(() => {
    processCommand(commandText);
  }, 250);
}

function submitManualText() {
  const inputEl = document.getElementById('manual-text-input');
  if (inputEl && inputEl.value.trim() !== '') {
    playAlexaSuccessChime();
    processCommand(inputEl.value.trim());
    inputEl.value = '';
  }
}

function handleKeypress(event) {
  if (event.key === 'Enter') submitManualText();
}

function triggerPhoneAction(title, desc, icon) {
  const card = document.getElementById('action-card');
  const titleEl = document.getElementById('action-card-title');
  const descEl = document.getElementById('action-card-desc');
  const iconEl = document.getElementById('action-card-icon');

  if (card && titleEl && descEl && iconEl) {
    titleEl.innerText = title;
    descEl.innerText = desc;
    iconEl.innerText = icon;
    card.style.display = 'flex';
  }
}

function updatePhoneUI() {
  const torchBeam = document.getElementById('flashlight-beam');
  const torchVal = document.getElementById('torch-val');
  const volVal = document.getElementById('vol-val');
  const callVal = document.getElementById('call-val');
  const lockOverlay = document.getElementById('phone-lock-overlay');

  if (torchBeam) {
    if (phoneState.torch) torchBeam.classList.add('active');
    else torchBeam.classList.remove('active');
  }
  if (torchVal) torchVal.innerText = phoneState.torch ? 'ON' : 'OFF';
  if (volVal) volVal.innerText = `${phoneState.volume}%`;
  if (callVal) callVal.innerText = phoneState.call;

  if (lockOverlay) {
    if (phoneState.isLocked) {
      lockOverlay.style.display = 'flex';
    } else {
      lockOverlay.style.display = 'none';
    }
  }
}

function filterCommands(category) {
  const items = document.querySelectorAll('.cmd-item');
  const tabs = document.querySelectorAll('.cmd-tab');
  tabs.forEach(t => t.classList.remove('active'));

  if (window.event && window.event.currentTarget) {
    window.event.currentTarget.classList.add('active');
  }

  items.forEach(item => {
    if (category === 'all' || item.getAttribute('data-lang') === category) {
      item.style.display = 'flex';
    } else {
      item.style.display = 'none';
    }
  });
}

function logTicker(msg) {
  const ticker = document.getElementById('log-ticker');
  const now = new Date();
  const timeStr = `[${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}]`;
  if (ticker) {
    ticker.innerText = `${timeStr} ${msg}`;
  }
}

// ==========================================================================
// Canvas Audio Visualizer (Alexa Waveform)
// ==========================================================================
function initCanvasVisualizer() {
  const canvas = document.getElementById('waveform-canvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  let phase = 0;

  function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const width = canvas.width;
    const height = canvas.height;
    const centerY = height / 2;

    const isEcho = currentPersona === 'alexa_echo';
    const primaryColor = isEcho ? '#00cae0' : '#ff9900';
    const secondaryColor = isEcho ? 'rgba(0, 202, 224, 0.15)' : 'rgba(255, 153, 0, 0.15)';

    let amplitude = 5;
    if (isSpeaking) amplitude = 26 + Math.sin(phase * 4) * 10;
    else if (isListening) amplitude = 16 + Math.cos(phase * 3) * 7;

    // Fill wave
    ctx.beginPath();
    ctx.moveTo(0, centerY);
    for (let x = 0; x < width; x += 4) {
      const y = centerY + Math.sin((x * 0.025) + phase) * amplitude * Math.sin(x / width * Math.PI);
      ctx.lineTo(x, y);
    }
    ctx.lineTo(width, centerY);
    ctx.fillStyle = secondaryColor;
    ctx.fill();

    // Primary Alexa Cyan stroke
    ctx.beginPath();
    ctx.lineWidth = 2.5;
    ctx.strokeStyle = primaryColor;
    ctx.shadowBlur = 10;
    ctx.shadowColor = primaryColor;

    for (let x = 0; x < width; x += 2) {
      const y = centerY + Math.sin((x * 0.035) + phase) * amplitude * Math.sin(x / width * Math.PI);
      if (x === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
    ctx.shadowBlur = 0;

    // Secondary Electric Blue stroke
    ctx.beginPath();
    ctx.lineWidth = 1.2;
    ctx.strokeStyle = isEcho ? '#0055ff' : '#00a8e8';
    for (let x = 0; x < width; x += 3) {
      const y = centerY + Math.cos((x * 0.045) - (phase * 1.4)) * (amplitude * 0.6) * Math.sin(x / width * Math.PI);
      if (x === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    phase += 0.06;
    animationFrameId = requestAnimationFrame(draw);
  }

  draw();
}

// ==========================================================================
// PWA Installation & App Management
// ==========================================================================
let deferredPrompt = null;

window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  deferredPrompt = e;
  const installBtn = document.getElementById('btn-install-pwa');
  if (installBtn) {
    installBtn.style.display = 'inline-flex';
  }
});

window.addEventListener('appinstalled', () => {
  deferredPrompt = null;
  const installBtn = document.getElementById('btn-install-pwa');
  if (installBtn) {
    installBtn.innerHTML = '<span>✅</span> ALEXA INSTALLED';
    installBtn.disabled = true;
  }
  logTicker('Alexa App installed on device home screen.');
});

function installPwaApp() {
  if (deferredPrompt) {
    deferredPrompt.prompt();
    deferredPrompt.userChoice.then((choiceResult) => {
      if (choiceResult.outcome === 'accepted') {
        console.log('User installed Alexa App');
      }
      deferredPrompt = null;
    });
  } else {
    alert('To install Alexa directly on your phone:\n\n1. On Android Chrome: Tap menu (⋮) -> "Install App" or "Add to Home screen"\n2. On iPhone Safari: Tap the Share button -> "Add to Home Screen"\n\nOnce added, Alexa runs full-screen 24/7 like a native mobile app with 100% offline support!');
  }
}
