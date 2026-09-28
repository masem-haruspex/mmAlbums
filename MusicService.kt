package com.mlib.albums

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat

class PlaybackController(private val context: Context) {
	private val player = MediaPlayer()
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	var currentSong by mutableStateOf<Song?>(null)
	var isPlaying by mutableStateOf(false)
	var progress by mutableFloatStateOf(0f)
	var repeatMode by mutableStateOf(RepeatMode.NONE)
	var playlist by mutableStateOf<List<Song>>(emptyList())
	var currentIndex by mutableIntStateOf(0)
	private var progressJob: Job? = null
	private var onStateChange: (() -> Unit)? = null

	init {
		player.setOnCompletionListener {
			when (repeatMode) {
				RepeatMode.SINGLE -> playCurrent()
				RepeatMode.ALL -> {
					currentIndex = (currentIndex + 1) % max(playlist.size, 1)
					playCurrent()
				}
				RepeatMode.NONE -> {
					if (currentIndex < playlist.size - 1) {
						currentIndex++
						playCurrent()
					} else {
						stop()
					}
				}
			}
			onStateChange?.invoke()
		}
	}

	fun setOnStateChangeListener(listener: () -> Unit) { onStateChange = listener }

	fun playAlbum(songs: List<Song>, startIndex: Int = 0) {
		if (songs.isEmpty()) return
		playlist = songs
		currentIndex = startIndex.coerceIn(0, songs.size - 1)
		playCurrent()
	}

	private fun playCurrent() {
		val song = playlist.getOrNull(currentIndex) ?: return
		try {
			player.reset()
			player.setDataSource(context, song.uri)
			player.prepare()
			player.start()
			currentSong = song
			isPlaying = true
			startProgress()
			onStateChange?.invoke()
		} catch (_: Exception) {
			isPlaying = false
			onStateChange?.invoke()
		}
	}

	fun togglePlay() {
		if (isPlaying) {
			player.pause()
			isPlaying = false
			progressJob?.cancel()
		} else {
			if (currentSong == null && playlist.isNotEmpty()) {
				playCurrent()
			} else if (currentSong != null) {
				player.start()
				isPlaying = true
				startProgress()
			}
		}
		onStateChange?.invoke()
	}

	fun next() {
		if (playlist.isEmpty()) return
		when (repeatMode) {
			RepeatMode.NONE -> if (currentIndex < playlist.size - 1) currentIndex++ else return
			else -> currentIndex = (currentIndex + 1) % playlist.size
		}
		playCurrent()
	}

	fun prev() {
		if (playlist.isEmpty()) return
		when (repeatMode) {
			RepeatMode.NONE -> if (currentIndex > 0) currentIndex-- else return
			else -> currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
		}
		playCurrent()
	}

	fun seek(seconds: Int) {
		if (currentSong == null) return
		val newPos = player.currentPosition + seconds * 1000
		player.seekTo(newPos.coerceIn(0, player.duration))
		progress = player.currentPosition / player.duration.toFloat().coerceAtLeast(1f)
	}

	fun cycleRepeat() {
		repeatMode = RepeatMode.entries[(repeatMode.ordinal + 1) % RepeatMode.entries.size]
		onStateChange?.invoke()
	}

	private fun startProgress() {
		progressJob?.cancel()
		progressJob = scope.launch {
			while (isPlaying && player.isPlaying) {
				progress = player.currentPosition / player.duration.toFloat().coerceAtLeast(1f)
				delay(500)
			}
		}
	}

		fun stop() {
		try {
			player.stop()
		} catch (e: IllegalStateException) {
			// Defensive catch: player might already be stopped or in an invalid state
		}

		player.reset()

		isPlaying = false
		progress = 0f

		currentSong = null
		playlist = emptyList()
		currentIndex = 0

		progressJob?.cancel()
		onStateChange?.invoke() 
	}

	fun release() {
		stop()
		player.release()
		scope.cancel()
	}

	fun getDuration(): Int = if (player.duration > 0) player.duration else 0

	fun getCurrentPosition(): Int = player.currentPosition

	fun seekTo(positionMs: Long) {
		if (currentSong == null) return
		player.seekTo(positionMs.coerceIn(0, player.duration.toLong()).toInt())
		progress = player.currentPosition / player.duration.toFloat().coerceAtLeast(1f)
		onStateChange?.invoke()
	}
}

class MusicService : Service() {
	companion object {
		const val CHANNEL_ID = "music_playback"
		const val NOTIFICATION_ID = 1
		const val ACTION_TOGGLE = "com.mlib.albums.TOGGLE"
		const val ACTION_NEXT = "com.mlib.albums.NEXT"
		const val ACTION_PREV = "com.mlib.albums.PREV"
		const val ACTION_SEEK_FWD = "com.mlib.albums.SEEK_FWD"
		const val ACTION_SEEK_BACK = "com.mlib.albums.SEEK_BACK"
		const val ACTION_CYCLE_REPEAT = "com.mlib.albums.CYCLE_REPEAT"
		const val ACTION_STOP = "com.mlib.albums.STOP"
		const val ACTION_UPDATE_WIDGET = "com.mlib.albums.UPDATE_WIDGET"
	}

	private val binder = LocalBinder()
	lateinit var controller: PlaybackController
	private set
	private lateinit var audioManager: AudioManager
	private var audioFocusRequest: AudioFocusRequest? = null
	private var resumeOnFocusGain = false
	private var becomingNoisyReceiver: BecomingNoisyReceiver? = null
	private lateinit var mediaSession: MediaSessionCompat
	private var isForeground = false

	inner class LocalBinder : Binder() {
		fun getService(): MusicService = this@MusicService
	}

	override fun onCreate() {
		super.onCreate()
		controller = PlaybackController(this)
		audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
		createNotificationChannel()

		mediaSession = MediaSessionCompat(this, "AlbumsAppMediaSession").apply {
			setCallback(object : MediaSessionCompat.Callback() {
				override fun onPlay() { this@MusicService.controller.togglePlay() }
				override fun onPause() { this@MusicService.controller.togglePlay() }
				override fun onSkipToNext() { this@MusicService.controller.next() }
				override fun onSkipToPrevious() { this@MusicService.controller.prev() }
				override fun onSeekTo(pos: Long) { this@MusicService.controller.seekTo(pos) }

				override fun onRewind() {
					this@MusicService.controller.seek(-10)
				}

				override fun onFastForward() {
					this@MusicService.controller.seek(10)
				}

				override fun onSetRepeatMode(repeatMode: Int) {
					val appMode = when (repeatMode) {
						PlaybackStateCompat.REPEAT_MODE_ONE -> RepeatMode.SINGLE
						PlaybackStateCompat.REPEAT_MODE_ALL -> RepeatMode.ALL
						else -> RepeatMode.NONE
					}
					if (this@MusicService.controller.repeatMode != appMode) {
						this@MusicService.controller.cycleRepeat()
					}
				}

				override fun onCustomAction(action: String, extras: Bundle?) {
					when (action) {
						ACTION_SEEK_BACK -> this@MusicService.controller.seek(-10)
						ACTION_SEEK_FWD -> this@MusicService.controller.seek(10)
						ACTION_CYCLE_REPEAT -> this@MusicService.controller.cycleRepeat()
					}
					updateNotificationAndWidget()
				}

			})
			isActive = true

			setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
		}

		controller.setOnStateChangeListener { updateNotificationAndWidget() }
		registerBecomingNoisyReceiver()
	}

	override fun onBind(intent: Intent): IBinder = binder

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		when (intent?.action) {
			ACTION_TOGGLE -> controller.togglePlay()
			ACTION_NEXT -> controller.next()
			ACTION_PREV -> controller.prev()
			ACTION_SEEK_FWD -> controller.seek(10)
			ACTION_SEEK_BACK -> controller.seek(-10)
			ACTION_CYCLE_REPEAT -> controller.cycleRepeat()
			ACTION_STOP -> {
    controller.stop()
    if (isForeground) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
    }
    stopSelf()
}
			ACTION_UPDATE_WIDGET -> {
				// Triggered by widget provider to force an update
			}
		}
		updateNotificationAndWidget()
		return START_STICKY
	}

	fun requestAudioFocus(): Boolean {
		val attributes = AudioAttributes.Builder()
		.setUsage(AudioAttributes.USAGE_MEDIA)
		.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
		.build()
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
			.setAudioAttributes(attributes)
			.setWillPauseWhenDucked(false)
			.setOnAudioFocusChangeListener(audioFocusChangeListener)
			.build()
			audioFocusRequest = request
			audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
		} else {
			@Suppress("DEPRECATION")
			audioManager.requestAudioFocus(
				audioFocusChangeListener,
				AudioManager.STREAM_MUSIC,
				AudioManager.AUDIOFOCUS_GAIN
			) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
		}
	}

	fun abandonAudioFocus() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
		} else {
			@Suppress("DEPRECATION")
			audioManager.abandonAudioFocus(audioFocusChangeListener)
		}
	}

	private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
		when (focusChange) {
			AudioManager.AUDIOFOCUS_GAIN -> {
				if (resumeOnFocusGain) {
					resumeOnFocusGain = false
					controller.togglePlay()
				}
			}
			AudioManager.AUDIOFOCUS_LOSS -> {
				resumeOnFocusGain = false
				if (controller.isPlaying) controller.togglePlay()
			}
			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
				if (controller.isPlaying) {
					resumeOnFocusGain = true
					controller.togglePlay()
				}
			}
			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
				resumeOnFocusGain = false
			}
		}
		updateNotificationAndWidget()
	}

	private fun registerBecomingNoisyReceiver() {
		becomingNoisyReceiver = BecomingNoisyReceiver { 
			if (controller.isPlaying)
			controller.togglePlay()
		}
		registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				CHANNEL_ID,
				"Music Playback",
				NotificationManager.IMPORTANCE_LOW
			).apply {
				description = "Shows current playing song"
				setShowBadge(false)
			}
			val nm = getSystemService(NotificationManager::class.java)
			nm.createNotificationChannel(channel)
		}
	}

	private fun buildNotification(): Notification {
		val song = controller.currentSong
		val isPlaying = controller.isPlaying
		val repeatMode = controller.repeatMode

		var largeIcon: android.graphics.Bitmap? = null
		val artFile = song?.albumArtFile
		if (artFile != null) {
			try {
				val file = File(cacheDir, artFile)
				if (file.exists()) {
					largeIcon = BitmapFactory.decodeFile(file.absolutePath)
				}
			} catch (_: Exception) {}
		}

		val metadataBuilder = MediaMetadataCompat.Builder()
		.putString(MediaMetadataCompat.METADATA_KEY_TITLE, song?.title ?: "Unknown")
		.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song?.artist ?: "Unknown")
		.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song?.album ?: "Unknown")
		.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, controller.getDuration().toLong())

		if (largeIcon != null) {
			metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, largeIcon)
		}
		mediaSession.setMetadata(metadataBuilder.build())

		val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
		val playbackState = PlaybackStateCompat.Builder()
		.setState(state, controller.getCurrentPosition().toLong(), 1f)
		.setActions(
			PlaybackStateCompat.ACTION_PLAY_PAUSE or
			PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
			PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
			PlaybackStateCompat.ACTION_SEEK_TO
		)
		.addCustomAction(
			PlaybackStateCompat.CustomAction.Builder(
				ACTION_SEEK_BACK,
				"Back 10s",
				android.R.drawable.ic_media_rew
			).build()
		)
		.addCustomAction(
			PlaybackStateCompat.CustomAction.Builder(
				ACTION_SEEK_FWD,
				"Forward 10s",
				android.R.drawable.ic_media_ff
			).build()
		)
		.addCustomAction(
			PlaybackStateCompat.CustomAction.Builder(
				ACTION_CYCLE_REPEAT,
				controller.repeatMode.name,
				when (controller.repeatMode) {
					RepeatMode.NONE -> R.drawable.ic_repeat_none
					RepeatMode.SINGLE -> R.drawable.ic_repeat_one
					RepeatMode.ALL -> R.drawable.ic_repeat_all
				}
			).build()
		)
		.build()
		mediaSession.setPlaybackState(playbackState)

		val openAppIntent = Intent(this, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
		}
		val openAppPending = PendingIntent.getActivity(
			this, 0, openAppIntent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		val playIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
		val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

		return NotificationCompat.Builder(this, CHANNEL_ID)
		.setSmallIcon(android.R.drawable.ic_media_play)
		.setContentTitle(song?.title ?: "Unknown")
		.setContentText("${song?.artist ?: ""} • ${song?.album ?: ""}")
		.setLargeIcon(largeIcon)
		.setContentIntent(openAppPending)
		.setOngoing(isPlaying)
		.setOnlyAlertOnce(true)
		.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
		.setPriority(NotificationCompat.PRIORITY_LOW)
		.addAction(android.R.drawable.ic_media_previous, "Previous", pendingIntent(ACTION_PREV))
		.addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", pendingIntent(ACTION_TOGGLE))
		.addAction(android.R.drawable.ic_media_next, "Next", pendingIntent(ACTION_NEXT))
		.setStyle(
			androidx.media.app.NotificationCompat.MediaStyle()
			.setMediaSession(mediaSession.sessionToken)
			.setShowActionsInCompactView(0, 1, 2)
		)
		.build()
	}

	private fun pendingIntent(action: String): PendingIntent {
		val intent = Intent(this, MusicService::class.java).setAction(action)
		return PendingIntent.getService(
			this, action.hashCode(), intent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)
	}

	fun updateNotificationAndWidget() {
    val song = controller.currentSong
    if (song != null) {
        val notification = buildNotification()
        if (!isForeground) {
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
        } else {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, notification)
        }
    } else {
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIFICATION_ID)
    }
    updateWidget()
}

	private fun updateWidget() {
		val appWidgetManager = AppWidgetManager.getInstance(this)
		val componentName = ComponentName(this, MusicWidgetProvider::class.java)
		val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
		if (appWidgetIds.isEmpty()) return

		val song = controller.currentSong
		val isPlaying = controller.isPlaying
		val artFile = song?.albumArtFile
		var artBitmap: android.graphics.Bitmap? = null
		if (artFile != null) {
			try {
				val file = File(cacheDir, artFile)
				if (file.exists()) {
					artBitmap = BitmapFactory.decodeFile(file.absolutePath)
				}
			} catch (_: Exception) {}
		}

		for (appWidgetId in appWidgetIds) {
			val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
			val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
			val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

			val views = RemoteViews(packageName, R.layout.music_widget)

			val isCompact = minHeight <= 60
			val isVertical = minWidth <= 120 && minHeight > 60

			views.setViewVisibility(R.id.layout_compact, if (isCompact) View.VISIBLE else View.GONE)
			views.setViewVisibility(R.id.layout_vertical, if (isVertical) View.VISIBLE else View.GONE)
			views.setViewVisibility(R.id.layout_standard, if (!isCompact && !isVertical) View.VISIBLE else View.GONE)

			if (artBitmap != null) {
				views.setImageViewBitmap(R.id.widget_album_art, artBitmap)
			} else {
				views.setImageViewResource(R.id.widget_album_art, android.R.drawable.ic_media_play)
			}

			val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
			views.setImageViewResource(R.id.compact_btn_play_pause, playPauseIcon)
			views.setImageViewResource(R.id.vertical_btn_play_pause, playPauseIcon)
			views.setImageViewResource(R.id.standard_btn_play_pause, playPauseIcon)

			views.setOnClickPendingIntent(R.id.compact_btn_prev, pendingIntent(ACTION_PREV))
			views.setOnClickPendingIntent(R.id.compact_btn_seek_back, pendingIntent(ACTION_SEEK_BACK))
			views.setOnClickPendingIntent(R.id.compact_btn_play_pause, pendingIntent(ACTION_TOGGLE))
			views.setOnClickPendingIntent(R.id.compact_btn_seek_fwd, pendingIntent(ACTION_SEEK_FWD))
			views.setOnClickPendingIntent(R.id.compact_btn_next, pendingIntent(ACTION_NEXT))

			views.setOnClickPendingIntent(R.id.vertical_btn_prev, pendingIntent(ACTION_PREV))
			views.setOnClickPendingIntent(R.id.vertical_btn_next, pendingIntent(ACTION_NEXT))
			views.setOnClickPendingIntent(R.id.vertical_btn_play_pause, pendingIntent(ACTION_TOGGLE))

			views.setOnClickPendingIntent(R.id.standard_btn_prev, pendingIntent(ACTION_PREV))
			views.setOnClickPendingIntent(R.id.standard_btn_seek_back, pendingIntent(ACTION_SEEK_BACK))
			views.setOnClickPendingIntent(R.id.standard_btn_play_pause, pendingIntent(ACTION_TOGGLE))
			views.setOnClickPendingIntent(R.id.standard_btn_seek_fwd, pendingIntent(ACTION_SEEK_FWD))
			views.setOnClickPendingIntent(R.id.standard_btn_next, pendingIntent(ACTION_NEXT))

			val openAppIntent = Intent(this, MainActivity::class.java).apply {
				flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
			}
			val openAppPending = PendingIntent.getActivity(
				this, 0, openAppIntent,
				PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
			)
			views.setOnClickPendingIntent(R.id.widget_root, openAppPending)

			appWidgetManager.updateAppWidget(appWidgetId, views)
		}
	}

	override fun onDestroy() {
    becomingNoisyReceiver?.let { unregisterReceiver(it) }
    abandonAudioFocus()
    controller.release()
    mediaSession.isActive = false
    mediaSession.release()
    if (isForeground) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
    }
    super.onDestroy()
}
}

class BecomingNoisyReceiver(private val onPause: () -> Unit) : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
			onPause()
		}
	}
}
