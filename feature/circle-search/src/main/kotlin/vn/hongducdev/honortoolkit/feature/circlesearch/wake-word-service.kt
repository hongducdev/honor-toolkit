package vn.hongducdev.honortoolkit.feature.circlesearch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.StorageService

/**
 * Always-on "OK Google" listener. This is the Vosk pipeline from Trợ lý VXM
 * (`WakeService` + `WakeAudio` + `WakeFilter`) combined with this app's Gemini launch path.
 *
 * The microphone is released before Gemini opens, then the service waits until Gemini stops
 * recording before it listens again, so the two never fight over the microphone.
 */
class WakeWordService : Service(), RecognitionListener {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var config: WakeConfig
    private var playback: PlaybackGate? = null

    private var speech: WakeAudio? = null
    private var recognizer: Recognizer? = null
    private var wakeFilter: WakeFilter? = null

    private var destroyed = false
    private var assistantOpen = false
    private var assistantStartedAt = 0L
    private var micIdleSince = 0L
    private var triggered = false
    private var audioClosing = false
    private var restartAfterClose = false
    private var lastRecognition = 0L
    private var lastWakeAt = 0L
    private var recognitionFailures = 0
    private var testPending = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        config = WakeConfig(this)
        playback = PlaybackGate(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.cs_wake_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            config.enabled = false
            config.status = getString(R.string.cs_wake_status_off)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!config.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            config.enabled = false
            config.status = getString(R.string.cs_wake_status_no_mic)
            stopSelf()
            return START_NOT_STICKY
        }

        return try {
            startForegroundCompat()
            if (intent?.action == ACTION_TEST) testPending = true
            when {
                model == null -> loadModel()
                speech == null && !assistantOpen -> {
                    startListening()
                    runPendingTest()
                }
                else -> runPendingTest()
            }
            START_STICKY
        } catch (error: Exception) {
            config.status = getString(R.string.cs_wake_status_start_failed, error.javaClass.simpleName)
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        destroyed = true
        isRunning = false
        main.removeCallbacksAndMessages(null)
        stopListening()
        releaseWakeLock()
        // Do not overwrite the reason the service stopped (no microphone, start failure, …);
        // the caller already wrote the matching status. A normal "off" is written by the toggle.
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    private fun buildNotification(): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))
        val open = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.cs_wake_notification_title, WakePhrase.DISPLAY))
            .setContentText(getString(R.string.cs_wake_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(Notification.Action.Builder(null, getString(R.string.cs_wake_notification_stop), stop).build())
            .build()
    }

    private fun loadModel() {
        if (destroyed || loading) return
        loading = true
        setStatus(getString(R.string.cs_wake_status_loading))
        StorageService.unpack(
            this,
            MODEL_ASSET,
            MODEL_ASSET,
            { loaded ->
                loading = false
                if (destroyed) {
                    loaded.close()
                } else {
                    model = loaded
                    startListening()
                    runPendingTest()
                }
            },
            { error ->
                loading = false
                setStatus(getString(R.string.cs_wake_status_model_error, error.message.orEmpty()))
                main.postDelayed({ if (!destroyed && config.enabled && model == null) loadModel() }, RETRY_MODEL_MS)
            },
        )
    }

    private fun startListening() {
        if (destroyed || assistantOpen || triggered) return
        if (audioClosing) {
            restartAfterClose = true
            return
        }
        if (speech != null) {
            stopListening { startListening() }
            return
        }
        val loaded = model
        if (loaded == null) {
            loadModel()
            return
        }
        stopListening()
        triggered = false
        try {
            val newRecognizer = Recognizer(loaded, SAMPLE_RATE, WakePhrase.grammarJson).apply { setWords(true) }
            val filter = WakeFilter(
                canAccept = ::canAcceptWake,
                levelAllowed = ::acceptsWakeLevel,
                onWake = { onWakePhrase(it) },
            )
            val audio = WakeAudio(newRecognizer, SAMPLE_RATE, playback, filter)
            recognizer = newRecognizer
            wakeFilter = filter
            speech = audio
            lastRecognition = SystemClock.elapsedRealtime()
            audio.startListening(this)
            setStatus(getString(R.string.cs_wake_status_listening, WakePhrase.DISPLAY))
            main.removeCallbacks(healthCheck)
            main.postDelayed(healthCheck, HEALTH_MS)
        } catch (error: Exception) {
            setStatus(getString(R.string.cs_wake_status_mic_error, error.message.orEmpty()))
            recoverRecognition(error.toString())
        }
    }

    private fun stopListening(afterStop: (() -> Unit)? = null) {
        main.removeCallbacks(healthCheck)
        val oldAudio = speech
        val oldRecognizer = recognizer
        val oldFilter = wakeFilter
        speech = null
        recognizer = null
        wakeFilter = null
        oldFilter?.cancel()

        if (oldAudio == null && oldRecognizer == null) {
            afterStop?.invoke()
            return
        }
        audioClosing = true
        Thread({
            runCatching { oldAudio?.cancel() }
            runCatching { oldAudio?.shutdown() }
            runCatching { oldRecognizer?.close() }
            main.post {
                audioClosing = false
                if (destroyed) return@post
                afterStop?.invoke()
                if (restartAfterClose) {
                    restartAfterClose = false
                    startListening()
                }
            }
        }, "wake-audio-close").start()
    }

    private fun canAcceptWake(): Boolean =
        !destroyed &&
            !triggered &&
            !assistantOpen &&
            speech != null &&
            config.enabled &&
            playback?.blocks() != true &&
            (lastWakeAt == 0L || SystemClock.elapsedRealtime() - lastWakeAt >= WAKE_COOLDOWN_MS)

    private fun acceptsWakeLevel(): Boolean {
        val audio = speech ?: return false
        return audio.voicedEnough() && audio.loudEnough(config.sensitivity())
    }

    private fun onWakePhrase(text: String) {
        if (!canAcceptWake()) return
        triggered = true
        lastWakeAt = SystemClock.elapsedRealtime()
        config.lastHeard = text.ifBlank { WakePhrase.DISPLAY }
        config.lastHeardAt = System.currentTimeMillis()
        setStatus(getString(R.string.cs_wake_status_heard, WakePhrase.DISPLAY))
        stopListening { launchGemini() }
    }

    private fun launchGemini() {
        if (destroyed) return
        assistantOpen = true
        assistantStartedAt = SystemClock.elapsedRealtime()
        micIdleSince = 0L
        setStatus(getString(R.string.cs_wake_status_launching))
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "honor-toolkit:wake")
            .apply {
                setReferenceCounted(false)
                runCatching { acquire(WAKE_LOCK_MS) }
            }
        TriggerRunner.runGemini(applicationContext, 0, TriggerSource.Wake)
        main.removeCallbacks(assistantCheck)
        main.postDelayed(assistantCheck, ASSISTANT_POLL_MS)
    }

    private val assistantCheck = object : Runnable {
        override fun run() {
            if (destroyed || !assistantOpen) return
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - assistantStartedAt
            val recording = getSystemService(AudioManager::class.java).activeRecordingConfigurations.isNotEmpty()
            if (recording) {
                micIdleSince = 0L
            } else if (micIdleSince == 0L) {
                micIdleSince = now
            }
            val idle = micIdleSince != 0L && now - micIdleSince >= MIC_IDLE_MS
            val done = elapsed >= MAX_ASSISTANT_MS ||
                (elapsed >= MIN_ASSISTANT_MS && idle && playback?.blocks() != true)
            if (done) {
                finishAssistant()
            } else {
                main.postDelayed(this, ASSISTANT_POLL_MS)
            }
        }
    }

    private fun finishAssistant() {
        main.removeCallbacks(assistantCheck)
        assistantOpen = false
        triggered = false
        micIdleSince = 0L
        releaseWakeLock()
        if (destroyed) return
        setStatus(getString(R.string.cs_wake_status_rearm))
        main.postDelayed({ if (!destroyed && !assistantOpen) startListening() }, REARM_DELAY_MS)
    }

    private fun recoverRecognition(reason: String) {
        if (destroyed) return
        stopListening {
            if (destroyed || assistantOpen) {
                return@stopListening
            }
            recognitionFailures++
            if (recognitionFailures >= MAX_FAILURES) {
                runCatching { model?.close() }
                model = null
                recognitionFailures = 0
            }
            setStatus(getString(R.string.cs_wake_status_recovering))
            main.postDelayed({
                if (destroyed || assistantOpen) return@postDelayed
                if (model == null) loadModel() else startListening()
            }, RETRY_RECOGNIZER_MS)
        }
        config.status = getString(R.string.cs_wake_status_recovering)
    }

    private val healthCheck = object : Runnable {
        override fun run() {
            if (destroyed) return
            if (!assistantOpen && speech != null && SystemClock.elapsedRealtime() - lastRecognition > SILENCE_MS) {
                recoverRecognition("no callbacks for 10s")
                return
            }
            main.postDelayed(this, if (assistantOpen) ASSISTANT_POLL_MS else HEALTH_MS)
        }
    }

    private fun heartbeat() {
        lastRecognition = SystemClock.elapsedRealtime()
        recognitionFailures = 0
    }

    private fun runPendingTest() {
        if (!testPending) return
        testPending = false
        if (speech == null) {
            main.postDelayed({ if (speech != null && canAcceptWake()) onWakePhrase(TEST_PHRASE) }, TEST_DELAY_MS)
        } else {
            onWakePhrase(TEST_PHRASE)
        }
    }

    private fun setStatus(value: String) {
        config.status = value
    }

    private fun releaseWakeLock() {
        wakeLock?.let { runCatching { if (it.isHeld) it.release() } }
        wakeLock = null
    }

    override fun onPartialResult(hypothesis: String) {
        heartbeat()
        wakeFilter?.candidate(hypothesis, true)
    }

    override fun onResult(hypothesis: String) {
        heartbeat()
        wakeFilter?.candidate(hypothesis, false)
    }

    override fun onFinalResult(hypothesis: String) {
        heartbeat()
        wakeFilter?.candidate(hypothesis, false)
    }

    override fun onTimeout() {
        startListening()
    }

    override fun onError(e: Exception) {
        recoverRecognition(e.toString())
    }

    companion object {
        private const val CHANNEL_ID = "wake_word"
        private const val NOTIFICATION_ID = 3
        private const val SAMPLE_RATE = 16000f
        private const val MODEL_ASSET = "model-en"
        private const val ACTION_STOP = "vn.hongducdev.honortoolkit.feature.circlesearch.STOP_WAKE"
        private const val ACTION_TEST = "vn.hongducdev.honortoolkit.feature.circlesearch.TEST_WAKE"
        private const val TEST_PHRASE = "(thử trong ứng dụng)"
        private const val HEALTH_MS = 2_000L
        private const val SILENCE_MS = 10_000L
        private const val RETRY_MODEL_MS = 5_000L
        private const val RETRY_RECOGNIZER_MS = 1_000L
        private const val REARM_DELAY_MS = 500L
        private const val TEST_DELAY_MS = 500L
        private const val ASSISTANT_POLL_MS = 500L
        private const val MIN_ASSISTANT_MS = 5_000L
        private const val MAX_ASSISTANT_MS = 30_000L
        private const val MIC_IDLE_MS = 1_500L
        private const val WAKE_LOCK_MS = 5_000L
        private const val WAKE_COOLDOWN_MS = 5_000L
        private const val MAX_FAILURES = 4

        @Volatile
        private var model: Model? = null

        @Volatile
        private var loading = false

        /** True between [onCreate] and [onDestroy], so the screen can restart a killed service. */
        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WakeWordService::class.java)) }
        }

        fun test(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).setAction(ACTION_TEST)
            runCatching { context.startForegroundService(intent) }
        }

        /** Starts or stops the listener so it matches the saved switch, e.g. after boot. */
        fun sync(context: Context) {
            if (WakeConfig(context).enabled) start(context) else stop(context)
        }
    }
}
