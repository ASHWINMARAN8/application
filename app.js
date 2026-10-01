/* ==========================================================================
   PEPPER // PERSONAL VOICE AI & PHONE ASSISTANT (JS)
   Full English + Tamil (தமிழ்) + Tanglish Personal AI Engine
   Features Touchless Voice Screen Unlock & Low-End Optimization
   ========================================================================== */

// Current Persona Configuration
let currentPersona = 'pepper_executive'; // 'pepper_executive' or 'pepper_tactical'
let isListening = false;
let isSpeaking = false;
let recognition = null;
let animationFrameId = null;

// Virtual Phone State
const phoneState = {
  isLocked: false,
  torch: false,
  volume: 65,
  call: 'IDLE',
  battery: 88,
  activeApp: 'HomeScreen'
};

// Persona Configurations
const personas = {
  pepper_executive: {
    title: 'PEPPER',
    version: 'OS v5.0 (Rescue Mark 49)',
    greetingEng: 'At your service, sir. Pepper initialized. All core executive protocols running smoothly.',
    greetingTam: 'வணக்கம் பாஸ். பெப்பர் உங்கள் சேவையில் உள்ளது. அனைத்து அமைப்புகளும் தயார் நிலையில் உள்ளன. என்ன கட்டளை?',
    greetingTan: 'Hello boss! Pepper here. Unga phone complete control la irukku. Solunga boss, enna pannanum?',
    wakePrompt: 'Say "Hey Pepper" or tap reactor to speak',
    pitch: 1.12,
    rate: 1.0,
    tag: 'PEPPER (EXECUTIVE AI)'
  },
  pepper_tactical: {
    title: 'RESCUE',
    version: 'TAC-AI v5.2 (Stark Armor)',
    greetingEng: 'Rescue tactical armor interface online. Standing by for immediate deployment, sir.',
    greetingTam: 'ரெஸ்க்யூ தற்காப்பு அமைப்பு தயார். ஆப்லைன் மோடில் உங்கள் குரல் கட்டளைக்காக காத்திருக்கிறேன்.',
    greetingTan: 'Rescue armor Pepper tactical core ready, boss! Defense and phone controls standing by. Solunga boss!',
    wakePrompt: 'Say "Rescue" or "Pepper" to speak',
    pitch: 1.05,
    rate: 1.05,
    tag: 'PEPPER (RESCUE TACTICAL)'
  }
};

// ==========================================================================
// Initialization
// ==========================================================================
window.addEventListener('DOMContentLoaded', () => {
  initClock();
  initCanvasVisualizer();
  initSpeechRecognition();
  logTicker('Pepper Personal AI initialized. Touchless Screen Unlock & Low-End Core ready.');
});

function initClock() {
  const clockEl = document.getElementById('phone-clock');
  function updateTime() {
    const now = new Date();
    const hours = String(now.getHours()).padStart(2, '0');
    const mins = String(now.getMinutes()).padStart(2, '0');
    if (clockEl) clockEl.innerText = `${hours}:${mins}`;
  }
  updateTime();
  setInterval(updateTime, 1000);
}

// ==========================================================================
// Persona Switching
// ==========================================================================
function switchPersona(personaName) {
  currentPersona = personaName;
  const body = document.body;
  const btnExec = document.getElementById('btn-pepper-exec');
  const btnTact = document.getElementById('btn-pepper-tact');
  const titleEl = document.getElementById('persona-title');
  const aiTag = document.getElementById('ai-tag');
  const wakePrompt = document.getElementById('wake-prompt');

  if (personaName === 'pepper_tactical') {
    body.className = 'theme-pepper-tactical';
    if (btnExec) btnExec.classList.remove('active');
    if (btnTact) btnTact.classList.add('active');
    if (titleEl) titleEl.innerHTML = `RESCUE <span>${personas.pepper_tactical.version}</span>`;
    if (aiTag) aiTag.innerText = personas.pepper_tactical.tag;
    if (wakePrompt) wakePrompt.innerText = personas.pepper_tactical.wakePrompt;
    speakResponse(personas.pepper_tactical.greetingTan, 'tanglish');
    logTicker('Switched to Pepper Rescue Tactical protocol.');
  } else {
    body.className = 'theme-pepper-exec';
    if (btnTact) btnTact.classList.remove('active');
    if (btnExec) btnExec.classList.add('active');
    if (titleEl) titleEl.innerHTML = `PEPPER <span>${personas.pepper_executive.version}</span>`;
    if (aiTag) aiTag.innerText = personas.pepper_executive.tag;
    if (wakePrompt) wakePrompt.innerText = personas.pepper_executive.wakePrompt;
    speakResponse(personas.pepper_executive.greetingTan, 'tanglish');
    logTicker('Switched to Pepper Executive AI. Arc Reactor nominal.');
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
    'inga', 'anga', 'amma ku', 'appa ku', 'ku call', 'ah', 'romba', 'nalla', 'mass', 'unlock'
  ];
  if (tanglishMarkers.some(word => lower.includes(word))) {
    return 'tanglish';
  }
  return 'english';
}

// ==========================================================================
// Bilingual & Tanglish Intent Router (NLU)
// ==========================================================================
function processCommand(rawInput) {
  const input = rawInput.trim();
  const lower = input.toLowerCase();
  const userTranscriptEl = document.getElementById('user-transcript');
  if (userTranscriptEl) userTranscriptEl.innerText = `"${input}"`;

  logTicker(`Processing voice input: "${input}"`);

  const lang = detectLanguage(input);

  // 1. TOUCHLESS PHONE UNLOCK (Without touching screen)
  if (
    lower.includes('unlock phone') || lower.includes('phone ah unlock pannu') ||
    lower.includes('unlock pannu') || lower.includes('screen ah open pannu') ||
    lower.includes('போனை அன்லாக் பண்ணு') || lower.includes('திரையை திற') ||
    lower.includes('wake up and unlock') || lower.includes('unlock')
  ) {
    phoneState.isLocked = false;
    updatePhoneUI();
    const reply = lang === 'tanglish'
      ? 'Phone-ah touchless-ah unlock panniten boss!'
      : (lang === 'tamil' ? 'போன் திரை திறக்கப்பட்டது பாஸ்.' : 'Device unlocked touchlessly, sir.');
    triggerPhoneAction('Touchless Unlock', 'Automated Swipe Gesture Dispatched', '🔓');
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
      ? "Phone display-ah lock panniten boss. 'Phone unlock pannu' nu sonna unlock pannuven."
      : (lang === 'tamil' ? 'போன் திரை லாக் செய்யப்பட்டது பாஸ்.' : 'Device display locked, sir. Say "unlock phone" to wake.');
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
      ? 'Torch on panniten boss!'
      : (lang === 'tamil' ? 'டார்ச் ஆன் செய்யப்பட்டது பாஸ்.' : 'Flashlight illuminated, sir.');
    triggerPhoneAction('Flashlight Control', 'Camera Torch ON', '🔦');
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
      ? 'Torch off panniten boss.'
      : (lang === 'tamil' ? 'டார்ச் ஆப் செய்யப்பட்டது.' : 'Flashlight deactivated, sir.');
    triggerPhoneAction('Flashlight Control', 'Camera Torch OFF', '🔦');
    speakResponse(reply, lang);
    return;
  }

  // 4. PHONE CALLS
  if (lower.includes('call pannu') || lower.includes('கால் பண்ணு') || lower.includes('call') || lower.includes('dial') || lower.includes('phone pannu')) {
    let contact = 'Contact';
    if (lang === 'tamil') {
      const matchTam = input.match(/(.*?)(?:-க்கு|க்கு|\s+)கால்\s*பண்ணு/);
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
      ? `${contact}-ku ippo call panren boss.`
      : (lang === 'tamil' ? `${contact}-க்கு இப்போது கால் செய்கிறேன்.` : `Initiating call to ${contact} right now, sir.`);
    triggerPhoneAction(`Call: ${contact}`, 'Dialing via Telecom Manager...', '📞');
    speakResponse(reply, lang);
    return;
  }

  // 5. WHATSAPP & SMS MESSAGING
  if (lower.includes('message') || lower.includes('மெசேஜ்') || lower.includes('whatsapp') || lower.includes('வாட்ஸ்அப்') || lower.includes('anupu')) {
    let recipient = 'Contact';
    let msg = 'Hello!';

    if (input.includes(':')) {
      const parts = input.split(':');
      msg = parts[1].trim();
      const leftPart = parts[0];
      const match = leftPart.match(/(?:to|anupu|அனுப்பு|க்கு|ku)\s*([a-zA-Z\u0B80-\u0BFF\s]+)/i);
      if (match && match[1]) recipient = match[1].trim();
    } else {
      recipient = lang === 'english' ? 'Alex' : 'ராமு';
      msg = lang === 'tanglish' ? 'Naan ippo kelambiten' : (lang === 'tamil' ? 'நான் கிளம்பிட்டேன்' : 'I will arrive shortly');
    }

    const reply = lang === 'tanglish'
      ? `${recipient}-ku WhatsApp message anupiten boss: "${msg}"`
      : (lang === 'tamil' ? `${recipient}-க்கு மெசேஜ் அனுப்பப்பட்டது.` : `Message sent to ${recipient}, sir.`);
    triggerPhoneAction(`Message to ${recipient}`, `"${msg}"`, '💬');
    speakResponse(reply, lang);
    return;
  }

  // 6. APP LAUNCHING
  if (lower.includes('open') || lower.includes('ஓபன்') || lower.includes('launch') || lower.includes('start') || lower.includes('thira') || lower.includes('open pannu')) {
    let app = 'App';
    if (lower.includes('youtube') || lower.includes('யூடியூப்')) app = 'YouTube';
    else if (lower.includes('camera') || lower.includes('கேமரா')) app = 'Camera';
    else if (lower.includes('whatsapp') || lower.includes('வாட்ஸ்அப்')) app = 'WhatsApp';
    else if (lower.includes('instagram') || lower.includes('insta')) app = 'Instagram';
    else if (lower.includes('settings') || lower.includes('செட்டிங்ஸ்')) app = 'Settings';
    else {
      const match = lower.match(/(?:open|launch|start|open pannu)\s+([a-zA-Z]+)/);
      if (match && match[1]) app = match[1];
    }

    phoneState.activeApp = app;
    const reply = lang === 'tanglish'
      ? `${app} open panren boss.`
      : (lang === 'tamil' ? `${app} அப்ளிகேஷன் திறக்கப்படுகிறது.` : `Launching ${app} on your device now, sir.`);
    triggerPhoneAction(`App Launched: ${app}`, 'Intent Package Dispatched', '🚀');
    speakResponse(reply, lang);
    return;
  }

  // 7. VOLUME CONTROLS
  if (lower.includes('kootu') || lower.includes('சத்தத்தை கூட்டு') || lower.includes('volume up') || lower.includes('increase volume')) {
    phoneState.volume = Math.min(100, phoneState.volume + 15);
    updatePhoneUI();
    const reply = lang === 'tanglish'
      ? `Sound ah ${phoneState.volume} percent ku kootiten boss.`
      : (lang === 'tamil' ? `சத்தம் அதிகரிக்கப்பட்டது. இப்போது ${phoneState.volume} சதவீதம்.` : `Audio level increased to ${phoneState.volume} percent, sir.`);
    triggerPhoneAction('Volume Control', `Stream set to ${phoneState.volume}%`, '🔊');
    speakResponse(reply, lang);
    return;
  }

  if (lower.includes('korai') || lower.includes('kurai') || lower.includes('சத்தத்தை குறை') || lower.includes('volume down') || lower.includes('decrease volume')) {
    phoneState.volume = Math.max(0, phoneState.volume - 15);
    updatePhoneUI();
    const reply = lang === 'tanglish'
      ? `Sound ah ${phoneState.volume} percent ku korachuten boss.`
      : (lang === 'tamil' ? `சத்தம் குறைக்கப்பட்டது: ${phoneState.volume} சதவீதம்.` : `Audio level reduced to ${phoneState.volume} percent, sir.`);
    triggerPhoneAction('Volume Control', `Stream set to ${phoneState.volume}%`, '🔉');
    speakResponse(reply, lang);
    return;
  }

  // 8. BATTERY & STATUS & TIME
  if (lower.includes('battery') || lower.includes('பேட்டரி') || lower.includes('charge') || lower.includes('evalo') || lower.includes('evvalavu')) {
    const reply = lang === 'tanglish'
      ? `Battery ippo ${phoneState.battery} percent irukku boss. Power super stable!`
      : (lang === 'tamil' ? `போன் பேட்டரி அளவு ${phoneState.battery} சதவீதம் உள்ளது பாஸ்.` : `Battery is standing at ${phoneState.battery} percent, sir.`);
    triggerPhoneAction('System Telemetry', `Battery: ${phoneState.battery}%`, '🔋');
    speakResponse(reply, lang);
    return;
  }

  // 9. PERSONAL AI BANTER: "How are you?" / "epdi iruka?"
  if (lower.includes('epdi iruka') || lower.includes('how are you') || lower.includes('எப்படி இருக்க')) {
    const reply = lang === 'tanglish'
      ? 'Pepper full energy la super-ah iruken boss! Low-end device layum lag illama smooth-ah run aaguren. Neenga epdi irukinga boss?'
      : (lang === 'tamil' ? 'முழு திறனில் சிறப்பாக இயங்குகிறேன் பாஸ்! நீங்கள் எப்படி இருக்கிறீர்கள்?' : 'Operating at peak efficiency, sir! Pepper Potts Rescue protocol active. How may I assist you today?');
    speakResponse(reply, lang);
    return;
  }

  // 10. PERSONAL AI JOKE
  if (lower.includes('joke') || lower.includes('ஜோக்')) {
    const reply = lang === 'tanglish'
      ? 'Boss, Stark Industries la Tony kitta keten: AI ku leave unda nu... Adhuku avar sonnaru: "Pepper irukum bothu Tony-ke leave theva illa!" haha!'
      : (lang === 'tamil' ? 'டோனி ஸ்டார்க் ஒரு முறை என்னிடம் கேட்டார், ஏஐக்கு தூக்கம் வருமா என்று. நான் சொன்னேன்: பாஸ், பெப்பர் தூங்கினால் ஸ்டார்க் இண்டஸ்ட்ரீஸ் என்ன ஆவது!' : 'Tony once asked if I ever sleep. I told him: "Mr. Stark, running Stark Industries and your suits is a 24/7 job!"');
    speakResponse(reply, lang);
    return;
  }

  // 11. IDENTITY: "ne yaaru?" / "who are you?" / "pepper"
  if (lower.includes('ne yaaru') || lower.includes('who are you') || lower.includes('நீ யாரு') || lower.includes('about pepper') || lower.includes('pepper')) {
    const reply = lang === 'tanglish'
      ? 'Naan unga personal AI assistant Pepper, boss! Low-end phone layum lag illama unga phone call, message, apps, torch, volume ellathayum voice commands la handle pannuven. Touchless unlock-um pannuven!'
      : (lang === 'tamil'
        ? 'நான் பெப்பர் (Pepper). இணையம் இல்லாமல் உங்கள் போனை முழுமையாகக் கட்டுப்படுத்தும் உங்கள் தனிப்பட்ட ஏஐ உதவியாளர்.'
        : 'I am PEPPER, your personal offline artificial intelligence and executive manager. I execute voice commands, control phone hardware, and manage tasks touchlessly.');
    speakResponse(reply, lang);
    return;
  }

  // DEFAULT PERSONAL ASSISTANT FALLBACK
  const defaultReply = lang === 'tanglish'
    ? `Neenga sonnadhu kettuchu boss: "${input}". Pepper offline-la execute panren!`
    : (lang === 'tamil' ? `உங்கள் கட்டளை "${input}" பெறப்பட்டது பாஸ்.` : `Directive "${input}" acknowledged, sir.`);
  triggerPhoneAction('Personal Command', input, '⚡');
  speakResponse(defaultReply, lang);
}

// ==========================================================================
// Speech Synthesis (TTS) - Native Bilingual & Tanglish Output
// ==========================================================================
function speakResponse(text, lang = 'english') {
  const aiResponseEl = document.getElementById('ai-response');
  const arcReactor = document.getElementById('arc-reactor');
  const speechState = document.getElementById('speech-state');

  if (aiResponseEl) aiResponseEl.innerText = text;
  if (arcReactor) arcReactor.classList.add('speaking');
  if (speechState) speechState.innerText = `AI SPEAKING (${lang.toUpperCase()})`;
  isSpeaking = true;

  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);

    utterance.rate = personas[currentPersona].rate;
    utterance.pitch = personas[currentPersona].pitch;

    const voices = window.speechSynthesis.getVoices();

    if (lang === 'tamil') {
      utterance.lang = 'ta-IN';
      const tamilVoice = voices.find(v => v.lang.includes('ta') || v.lang.includes('Tamil'));
      if (tamilVoice) utterance.voice = tamilVoice;
    } else if (lang === 'tanglish') {
      utterance.lang = 'en-IN';
      const femaleIndian = voices.find(v => (v.lang.includes('en-IN') || v.name.includes('India')) && (v.name.includes('Female') || v.name.includes('Heera') || v.name.includes('Veena')));
      const anyIndian = voices.find(v => v.lang.includes('en-IN') || v.name.includes('India'));
      if (femaleIndian) utterance.voice = femaleIndian;
      else if (anyIndian) utterance.voice = anyIndian;
    } else {
      utterance.lang = 'en-US';
      const femaleVoice = voices.find(v => v.name.includes('Female') || v.name.includes('Zira') || v.name.includes('Samantha') || v.name.includes('Victoria'));
      if (femaleVoice) utterance.voice = femaleVoice;
    }

    utterance.onend = () => {
      isSpeaking = false;
      if (arcReactor) arcReactor.classList.remove('speaking');
      if (speechState) speechState.innerText = isListening ? 'LISTENING (MIC ACTIVE)' : 'VOICE IDLE';
    };

    utterance.onerror = () => {
      isSpeaking = false;
      if (arcReactor) arcReactor.classList.remove('speaking');
      if (speechState) speechState.innerText = 'VOICE IDLE';
    };

    window.speechSynthesis.speak(utterance);
  } else {
    setTimeout(() => {
      isSpeaking = false;
      if (arcReactor) arcReactor.classList.remove('speaking');
      if (speechState) speechState.innerText = 'VOICE IDLE';
    }, 2000);
  }
}

// ==========================================================================
// Speech Recognition (Web Speech API)
// ==========================================================================
function initSpeechRecognition() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) {
    logTicker('Web Speech API not natively supported in this browser. Quick test buttons available.');
    return;
  }

  recognition = new SpeechRecognition();
  recognition.continuous = true;
  recognition.interimResults = false;
  recognition.lang = 'en-IN';

  recognition.onstart = () => {
    isListening = true;
    updateMicVisualState(true);
    logTicker('Microphone listening active. Speak to Pepper in Tanglish, Tamil or English.');
  };

  recognition.onresult = (event) => {
    const lastResultIndex = event.results.length - 1;
    const transcript = event.results[lastResultIndex][0].transcript.trim();
    if (transcript) {
      processCommand(transcript);
    }
  };

  recognition.onerror = () => {
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
  if (!recognition) {
    simulateVoice('torch podu');
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
      console.log('Already running');
    }
  }
}

function updateMicVisualState(active) {
  const arc = document.getElementById('arc-reactor');
  const speechState = document.getElementById('speech-state');
  const micFill = document.getElementById('mic-fill');
  const statAudio = document.getElementById('stat-audio-status');

  if (active) {
    if (arc) arc.classList.add('listening');
    if (speechState) speechState.innerText = 'LISTENING (MIC ACTIVE)';
    if (micFill) micFill.style.width = '85%';
    if (statAudio) statAudio.innerText = 'Listening...';
  } else {
    if (arc) arc.classList.remove('listening');
    if (speechState) speechState.innerText = 'VOICE IDLE';
    if (micFill) micFill.style.width = '20%';
    if (statAudio) statAudio.innerText = 'Mic Ready';
  }
}

// ==========================================================================
// Simulation Helpers
// ==========================================================================
function simulateVoice(commandText) {
  processCommand(commandText);
}

function submitManualText() {
  const inputEl = document.getElementById('manual-text-input');
  if (inputEl && inputEl.value.trim() !== '') {
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

  event.currentTarget.classList.add('active');

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
// Canvas Audio Visualizer
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

    const isExec = currentPersona === 'pepper_executive';
    const primaryColor = isExec ? '#00f0ff' : '#ff3366';
    const secondaryColor = isExec ? 'rgba(0, 240, 255, 0.15)' : 'rgba(255, 51, 102, 0.18)';

    let amplitude = 6;
    if (isSpeaking) amplitude = 28 + Math.sin(phase * 4) * 12;
    else if (isListening) amplitude = 18 + Math.cos(phase * 3) * 8;

    ctx.beginPath();
    ctx.moveTo(0, centerY);
    for (let x = 0; x < width; x += 4) {
      const y = centerY + Math.sin((x * 0.03) + phase) * amplitude * Math.sin(x / width * Math.PI);
      ctx.lineTo(x, y);
    }
    ctx.lineTo(width, centerY);
    ctx.fillStyle = secondaryColor;
    ctx.fill();

    ctx.beginPath();
    ctx.lineWidth = 2.5;
    ctx.strokeStyle = primaryColor;
    ctx.shadowBlur = 12;
    ctx.shadowColor = primaryColor;

    for (let x = 0; x < width; x += 2) {
      const y = centerY + Math.sin((x * 0.04) + phase) * amplitude * Math.sin(x / width * Math.PI);
      if (x === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
    ctx.shadowBlur = 0;

    ctx.beginPath();
    ctx.lineWidth = 1.2;
    ctx.strokeStyle = isExec ? '#ffd700' : '#00ffcc';
    for (let x = 0; x < width; x += 3) {
      const y = centerY + Math.cos((x * 0.05) - (phase * 1.5)) * (amplitude * 0.6) * Math.sin(x / width * Math.PI);
      if (x === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    phase += 0.07;
    animationFrameId = requestAnimationFrame(draw);
  }

  draw();
}
