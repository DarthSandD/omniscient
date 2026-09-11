# Omniscient

Voice-first Android AI assistant with a JARVIS-style HUD. Talks to any
OpenAI-compatible `/chat/completions` endpoint (default: OpenAI `gpt-4o-mini`).

## Features (v1.0.0)

- Voice input via `SpeechRecognizer` (with partial results + graceful text fallback)
- Voice output via `TextToSpeech` (toggleable, reads replies aloud)
- Text chat fallback on both the home screen and the full session screen
- Settings: endpoint URL, API key, model, voice-output toggle (persisted)
- Conversation list: saved as JSON in internal storage, tap to reopen, long-press to delete
- Custom launcher icon (reactor-core adaptive icon), dark HUD theme throughout
- `minSdk 26`, package `com.darrenai.omniscient`

## Reference architectures studied

- `yuga-hashimoto/openclaw-assistant` — Kotlin voice-first activity pattern,
  settings-driven backend URL/key, system-assistant feel. Closest stack reference.
- `DicioTeam/dicio-android` — mature offline voice pipeline; borrowed the
  `SpeechRecognizer` error-mapping + `UtteranceProgressListener` TTS lifecycle pattern.
- `sannabotdev/sannabotapp` — voice-first assistant with phone tool-use;
  closest concept reference for the conversation/session model.
- `ferranpons/Llamatik` — on-device LLM/STT via llama.cpp/whisper.cpp; evaluated
  and deliberately left out (APK size + minSdk 26 risk) in favour of a server endpoint.

## Deliberately left out of v1 (reasons)

- Wake-word / hotword detection: needs a Porcupine-style model + foreground
  service; high battery/permission risk for v1. The orb tap is the wake action.
- On-device LLM/STT: 100MB+ native binaries, device-compat risk; server endpoint instead.
- Phone tool-use (calls, apps, settings toggles): privileged APIs + runtime-permission
  minefield; stubbed as future work, chat answers still speak.
- Wear OS / widgets: out of scope for a first shippable APK.

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
