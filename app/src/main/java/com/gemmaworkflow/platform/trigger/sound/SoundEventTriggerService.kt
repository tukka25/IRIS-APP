package com.gemmaworkflow.platform.trigger.sound

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gemmaworkflow.platform.sound.YamnetClassifier
import com.gemmaworkflow.platform.trigger.TriggerRegistry
import com.gemmaworkflow.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that continuously listens for audio events using YAMNet.
 *
 * Runs as a foreground service with a persistent notification so it is not
 * killed by the system in doze mode. Started only when at least one sound
 * trigger is registered via [SoundEventTriggerRegistry].
 *
 * ## Lifecycle
 * ```
 * start() → creates notification channel, starts foreground, loads YAMNet
 *   → AudioRecord loop: reads 0.96s windows → classify() → check registry
 *   → on sound match: fire associated workflow via TriggerRegistry
 * stop()  → releases AudioRecord, stops foreground, cleans up
 * ```
 *
 * ## Permissions required
 * - `RECORD_AUDIO`
 * - `FOREGROUND_SERVICE`
 * - `FOREGROUND_SERVICE_MICROPHONE` (Android 14+)
 * - `POST_NOTIFICATIONS` (Android 13+)
 */
class SoundEventTriggerService : Service() {

    private val tag = "SoundEventSvc"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var audioRecord: AudioRecord? = null
    private var yamnet: YamnetClassifier? = null
    private var isListening = false

    // Minimum confidence threshold to fire a trigger (0.0 – 1.0)
    private val confidenceThreshold = 0.3f

    // Cooldown per sound class: prevent rapid re-firing (millis)
    private val cooldownPerClass = 30_000L  // 30 seconds
    private val lastFired = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "SoundEventTriggerService created")
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startListening()
            ACTION_STOP  -> stopListening()
            else         -> { /* ignore unknown intents */ }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopListening()
        scope.cancel()
        super.onDestroy()
    }

    // ── public start/stop ─────────────────────────────────────────────────────

    fun startListening() {
        if (isListening) return

        val notification = buildNotification("Listening for sounds…")
        startForeground(NOTIFICATION_ID, notification)

        val loaded = loadYamnet()
        if (!loaded) {
            Log.w(tag, "YAMNet failed to load — sound triggers unavailable")
            stopListening()
            return
        }

        isListening = true
        startAudioLoop()
        Log.i(tag, "Sound event listening started")
    }

    fun stopListening() {
        isListening = false

        try { audioRecord?.stop() } catch (e: Exception) { /* ignore */ }
        try { audioRecord?.release() } catch (e: Exception) { /* ignore */ }
        audioRecord = null

        yamnet?.close()
        yamnet = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(tag, "Sound event listening stopped")
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private fun loadYamnet(): Boolean {
        if (yamnet != null) return true
        yamnet = YamnetClassifier(this)
        return yamnet!!.load()
    }

    private fun startAudioLoop() {
        val sampleRate = YamnetClassifier.SAMPLE_RATE          // 16000
        val minSamples = YamnetClassifier.MIN_SAMPLES          // 15600
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(minSamples * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord failed to initialize")
                return
            }
            audioRecord?.startRecording()
        } catch (e: SecurityException) {
            Log.e(tag, "RECORD_AUDIO permission denied", e)
            return
        } catch (e: Exception) {
            Log.e(tag, "AudioRecord setup failed", e)
            return
        }

        val buffer = ShortArray(minSamples)
        val ctx = this

        scope.launch {
            while (isListening) {
                val audio = audioRecord ?: break
                val read = audio.read(buffer, 0, buffer.size)
                if (read < minSamples) continue

                val results = yamnet?.classify(buffer, read) ?: continue
                val top = results.firstOrNull() ?: continue

                if (top.confidence < confidenceThreshold) continue

                val className = top.className
                val now = System.currentTimeMillis()
                val last = lastFired[className] ?: 0L

                if (now - last < cooldownPerClass) continue  // still in cooldown

                // Check if this sound class has a registered workflow
                val workflowName = SoundEventTriggerRegistry.getWorkflow(ctx, className)
                if (workflowName != null) {
                    lastFired[className] = now
                    Log.i(tag, "Sound detected: '$className' " +
                        "(confidence=${String.format("%.2f", top.confidence)}) → firing '$workflowName'")
                    fireWorkflow(workflowName)
                }
            }
        }
    }

    private fun fireWorkflow(workflowName: String) {
        TriggerRegistry.fire(this, workflowName)
    }

    // ── notification ──────────────────────────────────────────────────────────

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sound Event Triggers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when GemmaWorkflow is listening for sound events"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Sound Detection Active")
            .setContentText(contentText)
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "SoundEventSvc"
        private const val CHANNEL_ID = "sound_event_detection"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.gemmaworkflow.action.START_SOUND_DETECTION"
        const val ACTION_STOP  = "com.gemmaworkflow.action.STOP_SOUND_DETECTION"

        /**
         * Start the sound detection service.
         * Idempotent — calling while already running is a no-op.
         */
        fun start(context: Context) {
            val intent = Intent(context, SoundEventTriggerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the sound detection service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, SoundEventTriggerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /**
         * Returns true if the service is currently listening.
         * Note: This is an approximation — the service may have been killed by the system.
         */
        fun isRunning(): Boolean = false  // override with process check if needed
    }
}
