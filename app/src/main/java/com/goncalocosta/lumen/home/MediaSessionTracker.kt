package com.goncalocosta.lumen.home

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MediaTrackInfo(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val packageName: String? = null,
    val artwork: ImageBitmap? = null
)

object MediaSessionTracker {
    private const val TAG = "MediaSessionTracker"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _mediaState = MutableStateFlow(MediaTrackInfo())
    val mediaState: StateFlow<MediaTrackInfo> = _mediaState.asStateFlow()

    private var activeController: MediaController? = null
    private var syncJob: Job? = null

    init {
        // Fallback for audio without a MediaSession (e.g. system/web audio)
        scope.launch {
            PlaybackKeepAwakeManager.isPlaybackActive.collect { isPlaying ->
                // ONLY use PlaybackKeepAwakeManager if there is NO active MediaController
                if (activeController == null) {
                    if (isPlaying) {
                        _mediaState.value = MediaTrackInfo(
                            isActive = true,
                            isPlaying = true,
                            title = "Now Playing",
                            artist = "Audio Active",
                            packageName = null
                        )
                    } else {
                        _mediaState.value = MediaTrackInfo(isActive = false, isPlaying = false)
                    }
                }
            }
        }
    }

    /**
     * Active state sync loop: ensures UI is 100% in sync with controller state.
     * Only runs while a controller is attached, so an idle launcher doesn't wake every 800ms.
     */
    private fun setActiveController(controller: MediaController?) {
        activeController = controller
        if (controller == null) {
            syncJob?.cancel()
            syncJob = null
            return
        }
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            while (isActive) {
                delay(800L)
                val c = activeController ?: break
                val s = c.playbackState?.state
                val isPlayingNow = s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
                val isSessionActive = s != null && s != PlaybackState.STATE_NONE && s != PlaybackState.STATE_STOPPED
                val current = _mediaState.value
                if (current.isPlaying != isPlayingNow || current.isActive != isSessionActive) {
                    _mediaState.value = current.copy(
                        isActive = isSessionActive,
                        isPlaying = isPlayingNow
                    )
                }
            }
        }
    }

    fun refresh(context: Context) {
        MediaNotificationListener.instance?.refreshSessions()
    }

    fun updateFromListener(
        isActive: Boolean,
        isPlaying: Boolean,
        title: String?,
        artist: String?,
        packageName: String?,
        artwork: ImageBitmap?,
        controller: MediaController?
    ) {
        setActiveController(controller)
        _mediaState.value = MediaTrackInfo(
            isActive = isActive,
            isPlaying = isPlaying,
            title = title ?: if (isActive) "Now Playing" else null,
            artist = artist ?: if (isActive) "Audio Playing" else null,
            packageName = packageName,
            artwork = artwork
        )
    }

    fun onNotificationRemoved(packageName: String) {
        val current = _mediaState.value
        if (current.packageName == packageName || packageName.isEmpty()) {
            setActiveController(null)
            if (PlaybackKeepAwakeManager.isPlaybackActive.value) {
                _mediaState.value = MediaTrackInfo(
                    isActive = true,
                    isPlaying = true,
                    title = "Now Playing",
                    artist = "Audio Active",
                    packageName = null
                )
            } else {
                _mediaState.value = MediaTrackInfo(isActive = false, isPlaying = false)
            }
        }
    }

    fun togglePlayPause(context: Context) {
        val controller = activeController
        if (controller != null) {
            val state = controller.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) {
                // Optimistically update UI immediately for zero-latency feel
                _mediaState.value = _mediaState.value.copy(isPlaying = false)
                controller.transportControls.pause()
            } else {
                _mediaState.value = _mediaState.value.copy(isPlaying = true)
                controller.transportControls.play()
            }
        } else {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    fun skipNext(context: Context) {
        val controller = activeController
        if (controller != null) {
            controller.transportControls.skipToNext()
        } else {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    fun skipPrevious(context: Context) {
        val controller = activeController
        if (controller != null) {
            controller.transportControls.skipToPrevious()
        } else {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    fun openCurrentApp(context: Context) {
        val pkg = _mediaState.value.packageName ?: return
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            runCatching { context.startActivity(intent) }
        }
    }

    private fun dispatchMediaKey(context: Context, keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        am.dispatchMediaKeyEvent(down)
        am.dispatchMediaKeyEvent(up)
    }
}

private const val MAX_ARTWORK_PX = 192

/** Album art is shown at <= 36dp; apps often attach 1000px+ bitmaps, which cost a big GPU upload per track. */
private fun downscaleArtwork(src: Bitmap): Bitmap {
    val longest = maxOf(src.width, src.height)
    if (longest <= MAX_ARTWORK_PX) return src
    val scale = MAX_ARTWORK_PX.toFloat() / longest
    return runCatching {
        Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }.getOrDefault(src)
}

class MediaNotificationListener : NotificationListenerService() {
    private var sessionManager: MediaSessionManager? = null
    private var currentController: MediaController? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        var instance: MediaNotificationListener? = null
            private set
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            mainHandler.post {
                updateFromController(currentController, state)
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            mainHandler.post {
                updateFromController(currentController)
            }
        }

        override fun onSessionDestroyed() {
            mainHandler.post {
                refreshSessions()
            }
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        mainHandler.post {
            evaluateControllers(controllers)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        val sm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        sessionManager = sm
        val comp = ComponentName(this, MediaNotificationListener::class.java)
        runCatching {
            sm.removeOnActiveSessionsChangedListener(sessionsListener)
            sm.addOnActiveSessionsChangedListener(sessionsListener, comp, mainHandler)
            refreshSessions()
        }.onFailure {
            Log.e("MediaNotification", "Failed to register active sessions listener", it)
        }
    }

    fun refreshSessions() {
        val sm = sessionManager ?: (getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager) ?: return
        sessionManager = sm
        val comp = ComponentName(this, MediaNotificationListener::class.java)
        runCatching {
            val sessions = sm.getActiveSessions(comp)
            evaluateControllers(sessions)
        }.onFailure {
            Log.e("MediaNotification", "Failed to query active sessions", it)
        }
    }

    private fun evaluateControllers(controllers: List<MediaController>?) {
        Log.d("MediaTracker", "evaluateControllers: size=${controllers?.size}, pkgs=${controllers?.map { it.packageName }}")
        if (controllers.isNullOrEmpty()) {
            if (!PlaybackKeepAwakeManager.isPlaybackActive.value) {
                MediaSessionTracker.onNotificationRemoved("")
            }
            return
        }

        // Prefer active playing session (e.g. Spotify), then any session
        val active = controllers.firstOrNull { c ->
            val s = c.playbackState?.state
            s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
        } ?: controllers.first()

        attachToController(active)
    }

    private fun attachToController(controller: MediaController) {
        if (currentController?.sessionToken != controller.sessionToken) {
            currentController?.unregisterCallback(callback)
            currentController = controller
            controller.registerCallback(callback, mainHandler)
        }
        updateFromController(controller)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        evaluateNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        refreshSessions()
    }

    private fun evaluateNotification(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        @Suppress("DEPRECATION")
        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }

        if (token != null) {
            runCatching {
                val controller = MediaController(this, token)
                attachToController(controller)
            }
        } else {
            refreshSessions()
        }
    }

    private fun updateFromController(controller: MediaController?, givenPbState: PlaybackState? = null) {
        if (controller == null) return
        val pkg = controller.packageName
        val pbState = givenPbState ?: controller.playbackState
        val isPlaying = pbState?.state == PlaybackState.STATE_PLAYING ||
                pbState?.state == PlaybackState.STATE_BUFFERING ||
                pbState?.state == PlaybackState.STATE_CONNECTING

        val meta = controller.metadata
        val desc = meta?.description
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: desc?.title?.toString()
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: desc?.subtitle?.toString()

        val rawBmp: Bitmap? = meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: desc?.iconBitmap
        val artwork = rawBmp?.let(::downscaleArtwork)?.asImageBitmap()

        val isSessionActive = pbState != null && pbState.state != PlaybackState.STATE_NONE &&
                pbState.state != PlaybackState.STATE_STOPPED

        Log.d("MediaTracker", "updateFromController: pkg=$pkg, title=$title, artist=$artist, isPlaying=$isPlaying, isSessionActive=$isSessionActive")

        MediaSessionTracker.updateFromListener(
            isActive = isSessionActive,
            isPlaying = isPlaying,
            title = title,
            artist = artist,
            packageName = pkg,
            artwork = artwork,
            controller = controller
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        currentController?.unregisterCallback(callback)
        currentController = null
        if (instance == this) instance = null
    }
}
