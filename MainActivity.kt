package com.mlib.albums

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.core.view.WindowCompat
import android.net.Uri
import android.os.IBinder
import android.os.Bundle
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.BackHandler
import com.mlib.future.FutureTheme
import com.mlib.future.components.FutureButton
import com.mlib.future.components.FutureIconSettings
import com.mlib.future.components.FutureHeader
import com.mlib.future.components.FutureSearch
import com.mlib.future.components.FutureSeparator
import com.mlib.future.components.FutureText
import com.mlib.future.modifiers.FutureScreenBackground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		WindowCompat.setDecorFitsSystemWindows(window, false)
		setContent {
			AlbumsApp()
		}
	}
}

sealed class Screen {
	data object Artists : Screen()
	data class Albums(val artist: String) : Screen()
	data class Songs(val artist: String, val album: String) : Screen()
}

@Composable
fun AlbumsApp() {
	val context = LocalContext.current
	val notificationPermissionLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.RequestPermission()
	) { isGranted ->
	}

	val prefs = remember { MusicPrefs(context) }
	var service by remember { mutableStateOf<MusicService?>(null) }

	val serviceConnection = remember {
		object : ServiceConnection {
			override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
				service = (binder as MusicService.LocalBinder).getService()
			}
			override fun onServiceDisconnected(name: ComponentName?) {
				service = null
			}
		}
	}

	var scrollToSongId by remember { mutableStateOf<String?>(null) }
	var scrollNonce by remember { mutableIntStateOf(0) }
	var screen by remember { mutableStateOf<Screen>(Screen.Artists) }
	var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
	var isLoading by remember { mutableStateOf(false) }
	var searchQuery by remember { mutableStateOf("") }
	var showSettings by remember { mutableStateOf(false) }

	var themeIndex by remember { mutableIntStateOf(prefs.themeIndex.coerceIn(0, themeSets.size - 1)) }
	var seekAmount by remember { mutableStateOf(prefs.seekAmount) }
	var folders by remember { mutableStateOf(prefs.folders.map { Uri.parse(it) }) }
	var blacklist by remember { mutableStateOf(prefs.blacklist) }

	val player = service?.controller
	val isDarkMode = isSystemInDarkTheme()
	val currentThemeSet = themeSets[themeIndex % themeSets.size]
	val currentConfig = remember(isDarkMode, currentThemeSet) {
		if (isDarkMode) currentThemeSet.dark else currentThemeSet.light
	}

	val allSongs by remember(artists) {
		derivedStateOf { artists.flatMap { it.albums }.flatMap { it.songs } }
	}

	val searchResults by remember(searchQuery, allSongs) {
		derivedStateOf {
			if (searchQuery.isBlank()) emptyList()
			else allSongs.filter {
				it.title.contains(searchQuery, ignoreCase = true) ||
				it.artist.contains(searchQuery, ignoreCase = true) ||
				it.album.contains(searchQuery, ignoreCase = true)
			}
		}
	}

	val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
		uri?.let {
			context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
			val new = folders + it
			folders = new
			prefs.folders = new.map { u -> u.toString() }.toSet()
		}
	}

	fun scan() {
		if (folders.isEmpty() || isLoading) return
		isLoading = true
		CoroutineScope(Dispatchers.IO).launch {
			val all = folders.flatMap { scanFolder(context, it) }
			artists = all.groupToArtists()
			prefs.saveArtists(artists)
			isLoading = false
		}
	}

	fun navigateBack() {
		screen = when (screen) {
			is Screen.Artists -> screen
			is Screen.Albums -> Screen.Artists
			is Screen.Songs -> Screen.Albums((screen as Screen.Songs).artist)
		}
	}

	fun buildQueueFromAlbum(
		album: Album,
		blacklist: Set<String>
	): List<Song> {
		return album.songs.filterNot { blacklist.contains(it.id) }
	}

	DisposableEffect(Unit) {
		val intent = Intent(context, MusicService::class.java)
		context.startService(intent)
		context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
		onDispose{ context.unbindService(serviceConnection) }
	}

	LaunchedEffect(Unit) {
		if (artists.isEmpty()) {
			val saved = prefs.loadArtists(context)
			if (saved.isNotEmpty()) {
				artists = saved
			} else if (folders.isNotEmpty()) {
				scan()
			}
		}
	}

	LaunchedEffect(Unit) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
		}
	}

	BackHandler(enabled = screen != Screen.Artists || searchQuery.isNotBlank()) {
		if (searchQuery.isNotBlank()) searchQuery = ""
		else navigateBack()
	}

	FutureTheme(config = currentConfig) {
		Surface(modifier = Modifier.fillMaxSize().imePadding(), color = FutureTheme.colors.globalBg) {
			Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
				FutureScreenBackground(modifier = Modifier.fillMaxSize())

				val bottomPadding = if (player?.currentSong != null && searchQuery.isBlank()) 256.dp else 64.dp

				Column(modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
					val crossfadeKey = when {
						searchQuery.isNotBlank() -> "search"
						screen is Screen.Artists -> "artists"
						screen is Screen.Albums -> "albums:${(screen as Screen.Albums).artist}"
						screen is Screen.Songs -> "songs:${(screen as Screen.Songs).artist}:${(screen as Screen.Songs).album}"
						else -> "artists"
					}

					Box(modifier = Modifier.weight(1f)) {
						Crossfade(targetState = crossfadeKey, modifier = Modifier.fillMaxSize(), label = "screen_crossfade") { key ->
							when {
								key == "search" -> SearchResultsScreen(
									results = searchResults,
									onPlay = { song ->
										val artist = artists.find { it.name == song.artist }
										val album = artist?.albums?.find { it.name == song.album }
										album?.let {
											val queue = buildQueueFromAlbum(it, blacklist)
											val startIndex = queue.indexOfFirst { s -> s.id == song.id }
											if (startIndex >= 0) {
												player?.let { ctrl ->
													ctrl.playAlbum(queue, startIndex)
													service?.requestAudioFocus()
													service?.updateNotificationAndWidget()
												}
											}
										}
									}
								)
								key == "artists" -> ArtistsScreen(
									artists = artists,
									isLoading = isLoading,
									onArtistClick = { screen = Screen.Albums(it.name) }
								)
								key.startsWith("albums:") -> {
									val artistName = key.removePrefix("albums:")
									val a = artists.find { it.name == artistName }
									AlbumsScreen(
										artist = a,
										onAlbumClick = { album -> screen = Screen.Songs(a?.name ?: "", album.name) },
										onPlayAlbum = { album ->
											val queue = buildQueueFromAlbum(album, blacklist)
											if (queue.isNotEmpty()) {
												player?.let { ctrl ->
													ctrl.playAlbum(queue, 0)
													service?.requestAudioFocus()
													service?.updateNotificationAndWidget()
												}
											}
										}
									)
								}
								key.startsWith("songs:") -> {
									val parts = key.split(":")
									val artistName = parts[1]
									val albumName = parts[2]
									val a = artists.find { it.name == artistName }
									val alb = a?.albums?.find { it.name == albumName }
									SongsScreen(
										album = alb,
										blacklist = blacklist,
										onToggleBlacklist = { id ->
											val new = if (blacklist.contains(id)) blacklist - id else blacklist + id
											blacklist = new
											prefs.blacklist = new
										},
										onPlayFrom = { index ->
											alb?.let { album ->
												val clickedSong = album.songs[index]
												val queue = buildQueueFromAlbum(album, blacklist)
												val startIndex = queue.indexOfFirst { it.id == clickedSong.id }
												if (startIndex >= 0) {
													player?.let { ctrl ->
														ctrl.playAlbum(queue, startIndex)
														service?.requestAudioFocus()
														service?.updateNotificationAndWidget()
													}
												}
											}
										},
										scrollToSongId = scrollToSongId,
										scrollNonce = scrollNonce,
										onScrollHandled = { scrollToSongId = null }
									)
								}
							}
						}
					}
				}

				Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
					if (player?.currentSong != null && searchQuery.isBlank()) {
						MiniPlayer(
							player = player,
							seekAmount = seekAmount.toIntOrNull() ?: 10,
							onTogglePlay = {
								player.togglePlay()
								service?.updateNotificationAndWidget()
							},
							onNext = {
								player.next()
								service?.updateNotificationAndWidget()
							},
							onPrev = {
								player.prev()
								service?.updateNotificationAndWidget()
							},
							onSeek = { secs ->
								player.seek(secs)
								service?.updateNotificationAndWidget()
							},
							onCycleRepeat = {
								player.cycleRepeat()
								service?.updateNotificationAndWidget()
							},
							onTitleClick = {
								val cur = player?.currentSong
								if (cur != null) {
									val owner = artists.find { it.name == cur.artist }
									val targetAlbum = owner?.albums?.find { it.name == cur.album }
									if (targetAlbum != null) {
										scrollToSongId = cur.id
										scrollNonce++
										searchQuery = ""
										screen = Screen.Songs(cur.artist, cur.album)
									}
								}
							}
						)
					}

					FutureSeparator(modifier = Modifier.fillMaxWidth())

					Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
						FutureSearch(
							value = searchQuery,
							onValueChange = { searchQuery = it },
							placeholder = "Search songs...",
							modifier = Modifier.weight(1f),
							onSearch = {}
						)
						FutureButton(
							onClick = { showSettings = true },
							icon = { FutureIconSettings() }
						)
					}
				}

				if (showSettings) {
					SettingsDialog(
						folders = folders,
						onAddFolder = { folderPicker.launch(null) },
						onRemoveFolder = { uri ->
							val new = folders - uri
							folders = new
							prefs.folders = new.map { it.toString() }.toSet()
						},
						onScan = ::scan,
						isScanning = isLoading,
						themeIndex = themeIndex,
						onThemeChange = {
							themeIndex = it
							prefs.themeIndex = it
						},
						seekAmount = seekAmount,
						onSeekAmountChange = {
							seekAmount = it
							prefs.seekAmount = it
						},
						onDismiss = { showSettings = false }
					)
				}
			}
		}
	}
}
