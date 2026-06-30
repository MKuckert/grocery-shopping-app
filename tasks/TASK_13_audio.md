# Task 13: Audio Feedback (`audio/`)

> **Depends on:** Task 5 (Hilt DI)
> **Status:** [x]

---

## Description

Create `audio/AudioFeedback.kt` using `android.media.SoundPool`.

SoundPool loads audio asynchronously; a play call before loading completes is silently ignored — guard with loaded flags.

```kotlin
@Singleton
class AudioFeedback @Inject constructor(@ApplicationContext context: Context) {
  private val soundPool = SoundPool.Builder()
    .setMaxStreams(3)
    .setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    )
    .build()

  private var successId = 0
  private var failureId = 0
  private var successLoaded = false
  private var failureLoaded = false

  init {
    soundPool.setOnLoadCompleteListener { _, sampleId, status ->
      if (status == 0) {
        when (sampleId) {
          successId -> successLoaded = true
          failureId -> failureLoaded = true
        }
      }
    }
    successId = soundPool.load(context, R.raw.beep_success, 1)
    failureId = soundPool.load(context, R.raw.beep_failure, 1)
  }

  fun playSuccess() { if (successLoaded) soundPool.play(successId, 1f, 1f, 1, 0, 1f) }
  fun playFailure() { if (failureLoaded) soundPool.play(failureId, 0.7f, 0.7f, 1, 0, 0.8f) }
  fun release()     = soundPool.release()
}
```

### Audio files

Add two short audio files to `src/main/res/raw/`:

- `beep_success.mp3` — high-pitched short beep (~200 ms). Source a royalty-free file.
- `beep_failure.mp3` — lower-pitched short boop (~200 ms).

### Release lifecycle

`AudioFeedback` must be released via `DisposableEffect` in the root `GroceryApp` composable:

```kotlin
DisposableEffect(Unit) {
  onDispose { audioFeedback.release() }
}
```

**Do not** use `Application.onTerminate()` — it is emulator-only and never fires on production Android devices.

---

## Review Criteria

- `SoundPool` loaded asynchronously; guard against playing before load completes using `SoundPool.OnLoadCompleteListener`.
- `AudioFeedback` released via `DisposableEffect` in the root `GroceryApp` composable.
