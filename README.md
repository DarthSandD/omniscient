# Omniscient v4 — the butler in the phone

Voice-first agentic assistant for Android. Speaks and types like a butler ("boss"),
acts through 15 real device tools, shows its work live in a command terminal.

> v4 is a from-zero rebuild (by Claude Code CLI, finished + verified by Hermes)
> after v3 crashed on launch. Crash-safety rules: light `onCreate`, standard
> `ViewModelProvider` (no reified/lazy delegates), null-safe views, permissions
> only when the needing tool runs, no work in `Application.onCreate`. The
> holographic orb view was deliberately left out of v4 — prime crash suspect,
> returns once the foundation proves stable on-device.

## Architecture (clean layers)

```
ui/        screens — one Activity + one ViewModel each, plain factory injection
  home/        HomeActivity + HomeViewModel (status, quick order → chat, voice, nav)
  chat/        ChatActivity + ChatViewModel (session, glass cards, voice, TTS)
  history/     HistoryActivity + HistoryViewModel (dossier of sessions)
  terminal/    TerminalActivity + TerminalViewModel (live tool-call log)
  settings/    SettingsActivity + SettingsViewModel (uplink config)
  onboarding/  OnboardingActivity + OnboardingViewModel (first run)
  voice/       VoiceManager (SpeechRecognizer), TtsManager
domain/      models, Persona (butler lines), AgentLog (event bus),
             repository interfaces, SendMessageUseCase (offline → agent loop)
data/        SettingsStore, FileConversations, FileMemory, OpenAiService,
             tools/ (DeviceTools ×15, ToolCatalog, OfflineIntents, PermissionGate),
             notify/ (notification-listener capture)
```

`OmniViewModelFactory` is the composition root (lazy deps, no casts).
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
- Butler persona: acknowledgments, "boss" address, voice (TTS) + text fallback

## Build

Real Gradle only (wrapper is corrupt — never use `gradlew`):

```
C:/Users/USER/gradle-8.5/bin/gradle assembleDebug
C:/Users/USER/gradle-8.5/bin/gradle assembleRelease   # signed; needs RELEASE_* in
                                                      # UNTRACKED local gradle.properties
```

Do NOT set JAVA_HOME (system JDK is already correct; overriding breaks the build).
Verify: `aapt dump badging app/build/outputs/apk/...apk` (+ `apksigner verify`
for release). Secrets (`*.keystore`, `.keystore-pass`, local `gradle.properties`)
are git-ignored and never committed.

## References studied (v3)

- VarsshanCoder/JARVIS-OS (Kotlin+FastAPI agent, memory, tool calling)
- patil-shubham-dev/Jarvis-Ai (voice + automation, multi-agent)
- lucadeg/opendroid (self-planning agent, glassmorphic design)
- prashantshukla01/Jarvis_Ironman (holographic orb interface)

Influence, not vendored code. Accessibility-driven screen control was
deliberately left out — high Play-policy risk, low payoff vs the tool API.
