# Multimodal Triggers Implementation Plan
## Voice Intent + Sound Event (Yamnet) Triggers

---

## Overview

Two new trigger types extend gemma4good from text-only workflows to audio-powered workflows:

| Trigger | Type | Latency | LLM Involved | Power Cost |
|---------|------|---------|--------------|------------|
| `voice` | Intentional (user holds to speak) | ~1-3s | Yes — text transcript becomes LLM prompt | Medium |
| `sound_event` | Reactive (background listening) | ~1s after sound | No — pattern-matched to workflow | Low |

---

## Trigger 1: Voice Intent (`voice`)

### What it does

```
User holds mic button
    → Android RecognizerIntent (on-device STT)
    → Text transcript
    → Feed to existing LLM workflow generator
    → Generated workflow executes
```

User experience: "OK Gemma, set volume to 70%" → Gemma records, transcribes, generates and runs the volume workflow.

### Files to create

#### 1. `platform/trigger/VoiceIntentTrigger.kt`
```
app/src/main/java/com/iris/platform/trigger/
```

- Uses `android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH`
- No permissions needed ( microphone permission required — `RECORD_AUDIO` already declared in AndroidManifest for P2/P3)
- Returns `Result<String>` where String = transcript text
- Supports `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM`
- Falls back gracefully if speech recognition unavailable

```kotlin
class VoiceIntentTrigger(private val context: Context) {

    fun recognize(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }

    fun parseResult(resultCode: Int, data: Intent?): Result<String> {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return Result.failure(Exception("Speech recognition failed or cancelled"))
        }
        val matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val transcript = matches?.firstOrNull() ?: ""
        return if (transcript.isNotBlank()) Result.success(transcript)
               else Result.failure(Exception("No speech detected"))
    }
}
```

#### 2. `domain/trigger/VoiceTriggerHandler.kt`

Bridges `VoiceIntentTrigger` into the existing `WorkflowCoordinator` / LLM pipeline.

```kotlin
class VoiceTriggerHandler(
    private val context: Context,
    private val llmWorkflowGenerator: LlmWorkflowGenerator  // existing
) {
    fun onVoiceResult(transcript: String) {
        // Feed transcript to LLM generator
        val workflow = llmWorkflowGenerator.generateFromPrompt(transcript)
        workflowExecutor.execute(workflow)
    }
}
```

#### 3. `ui/trigger/VoiceTriggerButton.kt` (Compose)

Floating action button in home screen that:
- Shows mic icon, animates while recording
- Launches `RecognizerIntent` on press
- Handles `onActivityResult` with `VoiceIntentTrigger.parseResult()`
- Shows transcript preview before LLM generation
- Shows loading state while LLM generates workflow
- Confirms with user before executing (new workflows from voice need review)

States:
```
Idle → Pressed (recording) → Processing transcript → LLM generating → Confirm → Executing
     ↘ Error (no speech / recognition failed) → Idle
```

### AndroidManifest.xml changes

Add:
```xml
<service android:name=".platform.trigger.SoundEventTriggerService"
    android:exported="false" />
```

### Integration points

| File | Change |
|------|--------|
| `WorkflowCoordinator.kt` | Add `startVoiceRecognition()` method |
| `HomeScreen.kt` / `MainActivity.kt` | Add FAB that calls `workflowCoordinator.startVoiceRecognition()` |
| `ActionSpecRegistry.kt` | Add `triggerCompatible = setOf("manual", "voice")` to relevant actions |

### UX flow

1. User taps mic FAB on home screen
2. Android speech recognizer starts (bottom sheet shows "Listening…")
3. User speaks: "set volume to 70%"
4. Transcript shown: "set volume to 70%"
5. "Generating workflow…" spinner
6. Workflow preview shown (e.g., `volume.set(level=70)`)
7. User taps "Run" → workflow executes

---

## Trigger 2: Sound Event (Yamnet)

### What it does

```
Background service starts on device boot (or workflow enable)
    → AudioRecord captures 0.96s windows at 16 kHz
    → YamnetClassifier.classify() → top AudioSet class
    → If class matches configured trigger → fire workflow
```

No LLM involved. Pattern-matched against user-configured sound → action mapping.

### Files to create

#### 1. `platform/trigger/SoundEventTriggerService.kt`

Foreground service with notification. Core loop:

```kotlin
class SoundEventTriggerService : Service {

    private val audioBuffer = ShortArray(YamnetClassifier.MIN_SAMPLES)
    private val yamnet = YamnetClassifier(this)
    private var isListening = false

    private val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        YamnetClassifier.SAMPLE_RATE,   // 16000
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
    )

    override fun onCreate() {
        yamnet.load()
    }

    fun startListening() {
        isListening = true
        audioRecord.startRecording()
        Thread {
            while (isListening) {
                audioRecord.read(audioBuffer, 0, audioBuffer.size)
                val results = yamnet.classify(audioBuffer, audioBuffer.size)
                val top = results.firstOrNull()
                if (top != null && top.confidence >= threshold) {
                    onSoundDetected(top.className, top.confidence)
                }
            }
        }.start()
    }

    private fun onSoundDetected(className: String, confidence: Float) {
        // Look up workflow by sound class
        val workflow = soundClassToWorkflow[className] ?: return
        WorkflowExecutor.execute(workflow)
    }
}
```

**Permissions required:**
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

**Notification channel:** Required for foreground service.

#### 2. `domain/trigger/SoundEventTriggerRegistry.kt`

Manages the mapping of YAMNet class names → workflow IDs, persisted in DataStore.

```kotlin
object SoundEventTriggerRegistry {
    // e.g. "Dog bark" → workflowId_123
    private val soundClassToWorkflow = mutableMapOf<String, String>()

    fun register(soundClass: String, workflowId: String)
    fun unregister(soundClass: String)
    fun getRegisteredClasses(): Set<String>
}
```

#### 3. `ui/trigger/SoundEventTriggerSetupScreen.kt`

Screen in the app to:
- Show list of YAMNet classes (filtered by "Speech", "Alarm", "Animal", etc.)
- Pick a sound class → assign a workflow from saved workflows
- Show currently registered sound → workflow mappings
- Toggle listening on/off

#### 4. `domain/trigger/SoundEventWorkflowGenerator.kt`

Generates a `PlannedWorkflow` from a sound event, without LLM:

```kotlin
class SoundEventWorkflowGenerator {
    // Template-based: user pre-configures "when dog bark → send notification"
    // This reads the registry and builds the workflow directly
    fun buildWorkflowForSound(soundClass: String): PlannedWorkflow?
}
```

### Files to modify

| File | Change |
|------|--------|
| `AndroidManifest.xml` | Add `SoundEventTriggerService`, permissions |
| `ActionSpecRegistry.kt` | Add `sound_event` to `triggerCompatible` for applicable actions (e.g. `notification.show`) |
| `BootReceiver.kt` | Start `SoundEventTriggerService` on `BOOT_COMPLETED` if any sound triggers are registered |
| `WorkflowCoordinator.kt` | Add methods to start/stop sound listening |

### YAMNet class categories (subset, most useful)

| Category | Classes |
|----------|---------|
| Human sounds | `Speech`, `Whisper`, `Shout`, `Scream`, `Crying, baby` |
| Animals | `Dog bark`, `Cat`, `Bird`, `Crowing`, `Meow`, `Bird vocalization` |
| Vehicle | `Car horn`, `Engine`, `Train whistle`, `Siren`, `Vehicle` |
| Alarm | `Alarm`, `Bell`, `Telephone bell`, `Fire alarm`, `Whistle` |
| Domestic | `Glass breaking`, `Knock`, `Doorbell`, `Footsteps`, `Applause` |
| Music | `Music`, `Singing`, `Choir` |

Full 521 classes available from `YamnetClassifier.getClassLabels()`.

---

## Shared Architecture

```
                    ┌─────────────────────┐
                    │  TriggerCoordinator  │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                 ▼
     ┌─────────────┐  ┌─────────────────┐  ┌──────────┐
     │ VoiceIntent │  │ SoundEvent      │  │ Existing │
     │ Trigger     │  │ (Yamnet)        │  │ manual   │
     └──────┬──────┘  └────────┬────────┘  │ time     │
            │                   │           │ nfc      │
            ▼                   ▼           │ share    │
     ┌────────────┐     ┌────────────┐     └──────────┘
     │ Android STT│     │ Pattern    │
     │ (on-device)│     │ Match      │
     └─────┬──────┘     └──────┬─────┘
           │                   │
           ▼                   ▼
     ┌─────────────────────────────┐
     │     LLM Workflow Generator  │ ← only for `voice`
     │     (Gemma 4 on-device)      │
     └──────────────┬──────────────┘
                    │ PlannedWorkflow
                    ▼
            ┌───────────────────┐
            │ WorkflowExecutor │
            │ (existing)        │
            └───────────────────┘
```

---

## Implementation Order

### Phase A: Voice Intent (user-facing, high value)
1. `VoiceIntentTrigger.kt` — Android speech intent wrapper
2. `VoiceTriggerHandler.kt` — bridge to LLM pipeline
3. `VoiceTriggerButton.kt` — FAB UI with states
4. Integrate into `MainActivity` / `HomeScreen`
5. Add `voice` to `triggerCompatible` for all manual actions

### Phase B: Sound Event (background, reactive)
1. `SoundEventTriggerService.kt` — foreground service + AudioRecord loop
2. `SoundEventTriggerRegistry.kt` — class → workflow mapping, DataStore persistence
3. `BootReceiver.kt` integration — auto-start on boot
4. `SoundEventTriggerSetupScreen.kt` — configuration UI
5. Add `sound_event` to `triggerCompatible` for notification/alert actions

### Phase C: Integration polish
- Yamnet model file (`yamnet.tflite`) — download and place in `assets/`
- Sound class labels file (`yamnet_class_map.csv`)
- Error handling: mic permission denied, speech recognizer unavailable, yamnet load failure
- Battery consideration: sound event service uses `PRIORITY_LOW` AudioSource to reduce power

---

## Verification

| Feature | Test |
|---------|------|
| Voice trigger | Hold mic → say "set volume to 80" → volume sets to 80 |
| Sound event | Configure "dog bark" → trigger → notification fires |
| Boot persistence | Reboot → service auto-starts → sound triggers still active |
| Permission flow | Mic permission denied → graceful fallback with explanation |
| Parallel triggers | Voice trigger while sound service running → no conflict |

---

## Permissions Summary

| Permission | Used By | When |
|-----------|---------|------|
| `RECORD_AUDIO` | VoiceIntentTrigger, SoundEventTriggerService | Always |
| `FOREGROUND_SERVICE` | SoundEventTriggerService | Android 10+ |
| `FOREGROUND_SERVICE_MICROPHONE` | SoundEventTriggerService | Android 14+ |
| `POST_NOTIFICATIONS` | SoundEventTriggerService foreground notification | Android 13+ |
| `INTERNET` | LLM download (if not on-device) | On-demand |
| `BOOT_COMPLETED` | Auto-start SoundEventTriggerService | On boot |
