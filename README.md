# Omniscient v3 — the butler in the phone

Voice-first agentic assistant for Android. Speaks and types like a butler ("boss"),
acts through 15 real device tools, shows its work live in a command terminal.

## Architecture (clean layers, no God-files)

```
ui/        screens — one Activity + one ViewModel each, orb view, voice wrappers
  home/        HomeActivity + HomeViewModel (holographic core, quick orders)
  chat/        ChatActivity + ChatViewModel (full session, glass cards)
  history/     HistoryActivity + HistoryViewModel (dossier of sessions)
  terminal/    TerminalActivity + TerminalViewModel (live tool-call log)
  settings/    SettingsActivity + SettingsViewModel (uplink config)
  onboarding/  OnboardingActivity + OnboardingViewModel (first run)
  orb/         HoloOrbView (Canvas hologram: halo, scan sweep, particles)
  voice/       VoiceManager (SpeechRecognizer), TtsManager
domain/      models, Persona (butler lines), AgentLog (event bus),
             repository interfaces, SendMessageUseCase (offline → agent loop)
data/        SettingsStore, FileConversations, FileMemory, OpenAiService,
             tools/ (DeviceTools ×15, ToolCatalog, OfflineIntents, PermissionGate),
             notify/ (notification-listener capture)
```

`OmniApp` is the composition root; `OmniViewModels` factory injects collaborators.
The agent loop lives in `domain/SendMessageUseCase` (max 5 rounds); the wire
lives in `data/OpenAiService`. No org.json in domain.

## Features

- Agent loop with function tools (OpenAI-compatible `/chat/completions`)
- 15 device tools: time, battery, location, open_app, alarm, timer, flashlight,
  wifi, bluetooth, call, SMS, notifications, remember, forget, web_search
- Offline intents (flashlight/time/battery/apps/memory) — no key, no network
- Memory: durable user facts injected into the system prompt
- Endpoint settings + one-tap OmniRoute preset (house LAN, keyless)
- Onboarding screen on first run (settings preserved across upgrades)
- Command terminal: every user turn + tool call logged live via AgentLog
- Butler persona: deterministic rotating acknowledgments, "boss" address,
  voice (TTS) + text fallback on every screen

## Build

Real Gradle only (wrapper is corrupt — never use `gradlew`):

```
C:/Users/USER/gradle-8.5/bin/gradle assembleDebug
C:/Users/USER/gradle-8.5/bin/gradle assembleRelease   # signed; needs RELEASE_* in
                                                      # UNTRACKED local gradle.properties
```

Verify: `aapt dump badging app/build/outputs/apk/...apk` (+ `apksigner verify`
for release). Secrets (`*.keystore`, `.keystore-pass`, local `gradle.properties`)
are git-ignored and never committed.

## References studied

- VarsshanCoder/JARVIS-OS (Kotlin+FastAPI agent, memory, tool calling)
- patil-shubham-dev/Jarvis-Ai (voice + automation, multi-agent)
- lucadeg/opendroid (self-planning agent, glassmorphic design)
- prashantshukla01/Jarvis_Ironman (holographic orb interface)

Influence, not vendored code: on-device agent loop + memory (JARVIS-OS),
terminal transparency + glass cards (opendroid), orb hero (Ironman).
Accessibility-driven screen control (opendroid/Jarvis-Ai) was deliberately
left out — high Play-policy risk, low payoff vs the tool API.
