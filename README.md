# Omniscient

Voice-first Android AI assistant with a JARVIS-style HUD. Talks to any
OpenAI-compatible `/chat/completions` endpoint (default: OpenAI `gpt-4o-mini`
with your own key). Works on ANY phone — no PC, Termux, ADB, or same-WiFi needed.

## Features (v2.0.0)

- Voice input via `SpeechRecognizer` (partial results + text fallback), TTS replies
- **Agentic tool-calling loop**: OpenAI-compatible `tools`/`tool_calls`, up to 5
  rounds, with automatic fallback to plain chat if the model rejects tools
- **Real device tools**: open app by name, alarm, timer, flashlight, Wi-Fi,
  Bluetooth, phone call, SMS, notifications, time, battery, coarse location,
  remember/forget memory, web search (optional, only if configured)
- **Offline rule-based intents** (no key, no network): flashlight, time/date,
  battery, open-app, remember/forget — parsed on-device before the LLM is tried
- **Local memory** (`memory.json`): durable user facts, injected into every system prompt
- **Settings**: endpoint + key + model, one-tap **OmniRoute preset**
  (optional extra for Darren's LAN `http://10.212.104.124:20128/v1`, key `x`,
  model `default`), optional SEARCH API base + key, voice toggle
- **First-launch onboarding**: one screen explains key setup; offline intents + TTS
  work before any key is entered
- Conversation list persisted as JSON; custom launcher icon; dark HUD theme
- `minSdk 26`, package `com.darrenai.omniscient`

### Tool reality table

| Tool | Status |
|---|---|
| get_time, get_battery | REAL, no permission |
| open_app | REAL (launcher fuzzy-match; no special permission) |
| set_alarm, set_timer | REAL (AlarmClock intents; needs a clock app) |
| toggle_flashlight | REAL (CameraManager; CAMERA runtime perm) |
| make_call | REAL (ACTION_CALL; CALL_PHONE perm + chat confirm + Yes/No dialog) |
| send_sms | REAL (SmsManager; SEND_SMS perm + chat confirm + Yes/No dialog) |
| read_notifications | REAL via NotificationListenerService; returns setup steps until enabled |
| get_location | REAL, permission-gated coarse last-known fix (may be stale/empty) |
| toggle_bluetooth | REAL attempt (enable = system prompt; disable may be refused on Android 13+) |
| toggle_wifi | OS-LIMITED: Android 10+ forbids app toggling → opens Wi-Fi panel instead |
| remember / forget | REAL (local `memory.json`) |
| web_search | GUARDED: disabled message unless Settings → SEARCH API is configured |

### No-API-key behaviour

- Default (OpenAI endpoint): guided error — open Settings and add a key.
- Custom endpoint (e.g. OmniRoute preset): request is sent **without** an
  Authorization header, so keyless local proxies just work.
- Either way, offline intents (flashlight/time/battery/open-app/memory) always work.

### Permissions declared vs used

Declared AND used at runtime (each asked only when its tool runs):
`CALL_PHONE`, `SEND_SMS`, `CAMERA`, `ACCESS_COARSE_LOCATION`,
`ACCESS_WIFI_STATE` (read state pre-Android 10), `CHANGE_WIFI_STATE` (pre-10 toggle),
`BLUETOOTH`/`BLUETOOTH_ADMIN` (maxSdk 30), `BLUETOOTH_CONNECT` (Android 12+ disable).
Notification access is a system Settings toggle, not a manifest permission.
No `QUERY_ALL_PACKAGES` — app discovery uses a `<queries>` launcher-intent block.

### Deliberately left out of v2 (reasons)

- Wake-word / hotword: still needs a Porcupine-style model + foreground service.
- On-device LLM/STT: 100MB+ natives, device-compat risk; server endpoint instead.
- Direct Wi-Fi toggle on Android 10+: blocked by the OS itself; panel fallback.
- Exact GPS fix: coarse last-known only; live fix needs a foreground service.
- Wear OS / widgets: out of scope.

## Build

Real Gradle 8.5 (the `gradlew` wrapper is not used):

```sh
export ANDROID_HOME="C:/Users/USER/AppData/Local/Android/Sdk"
C:/Users/USER/gradle-8.5/bin/gradle :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Verify:

```sh
C:/Users/USER/AppData/Local/Android/Sdk/build-tools/34.0.0/aapt dump badging app/build/outputs/apk/debug/app-debug.apk
```
