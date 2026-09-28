package com.mlib.albums

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.io.File
import com.mlib.future.BackgroundAnimationType
import com.mlib.future.FutureColors
import com.mlib.future.FutureConfig
import com.mlib.future.hexColor

data class Song(
	val id: String,
	val title: String,
	val artist: String,
	val album: String,
	val uri: Uri,
	val duration: Long,
	val track: Int,
	val albumArtFile: String? = null
) {
	fun toJson(): JSONObject {
		return JSONObject().apply {
			put("id", id)
			put("title", title)
			put("artist", artist)
			put("album", album)
			put("uri", uri.toString())
			put("duration", duration)
			put("track", track)
			put("albumArtFile", albumArtFile ?: JSONObject.NULL)
		}
	}

	companion object {
		fun fromJson(json: JSONObject): Song {
			return Song(
				id = json.getString("id"),
				title = json.getString("title"),
				artist = json.getString("artist"),
				album = json.getString("album"),
				uri = Uri.parse(json.getString("uri")),
				duration = json.getLong("duration"),
				track = json.getInt("track"),
				albumArtFile = if (json.has("albumArtFile") && !json.isNull("albumArtFile")) {
					json.getString("albumArtFile")
				} else null
			)
		}
	}
}

data class Album(
	val name: String,
	val artist: String,
	val songs: List<Song>
)

data class Artist(val name: String, val albums: List<Album>)

enum class RepeatMode { NONE, SINGLE, ALL }

class MusicPrefs(context: Context) {
	private val p = context.getSharedPreferences("music", Context.MODE_PRIVATE)

	var folders: Set<String>
	get() = p.getStringSet("folders", emptySet()) ?: emptySet()
	set(value) = p.edit().putStringSet("folders", value).apply()

	var blacklist: Set<String>
	get() = p.getStringSet("blacklist", emptySet()) ?: emptySet()
	set(value) = p.edit().putStringSet("blacklist", value).apply()

	var themeIndex: Int
	get() = p.getInt("theme", 0)
	set(value) = p.edit().putInt("theme", value).apply()

	var seekAmount: String
	get() = p.getString("seek", "10") ?: "10"
	set(value) = p.edit().putString("seek", value).apply()

	fun saveArtists(artists: List<Artist>) {
		val json = JSONArray()
		artists.forEach { artist ->
			val artistObj = JSONObject()
			artistObj.put("name", artist.name)
			val albumsArr = JSONArray()
			artist.albums.forEach { album ->
				val albumObj = JSONObject()
				albumObj.put("name", album.name)
				albumObj.put("artist", album.artist)
				val songsArr = JSONArray()
				album.songs.forEach { song ->
					songsArr.put(song.toJson())
				}
				albumObj.put("songs", songsArr)
				albumsArr.put(albumObj)
			}
			artistObj.put("albums", albumsArr)
			json.put(artistObj)
		}
		p.edit().putString("library_cache", json.toString()).apply()
	}

	fun loadArtists(context: Context): List<Artist> {
    val str = p.getString("library_cache", null) ?: return emptyList()
    return try {
        val json = JSONArray(str)
        List(json.length()) { i ->
            val artistObj = json.getJSONObject(i)
            val artistName = artistObj.getString("name")
            val albumsArr = artistObj.getJSONArray("albums")

            val albums = List(albumsArr.length()) { j ->
                val albumObj = albumsArr.getJSONObject(j)
                val songsArr = albumObj.getJSONArray("songs")

                val rawSongs = List(songsArr.length()) { k ->
                    Song.fromJson(songsArr.getJSONObject(k))
                }

                val artFile = rawSongs.firstOrNull { it.albumArtFile != null }?.albumArtFile

                val songs = rawSongs.map { song ->
                    if (artFile != null) {
                        song.copy(albumArtFile = artFile)
                    } else {
                        song
                    }
                }

                Album(
                    name = albumObj.getString("name"),
                    artist = artistName,
                    songs = songs
                )
            }
            Artist(name = artistName, albums = albums)
        }
    } catch (e: Exception) {
        emptyList()
    }
}
}

suspend fun scanFolder(context: Context, uri: Uri): List<Song> = withContext(Dispatchers.IO) {
	val root = DocumentFile.fromTreeUri(context, uri) ?: return@withContext emptyList()

	val queue = ArrayDeque<DocumentFile>()
	val files = mutableListOf<DocumentFile>()
	queue.add(root)

	val musicRegex = Regex(".*\\.(mp3|flac|ogg|m4a|wav|aac|opus)$", RegexOption.IGNORE_CASE)

	while (queue.isNotEmpty()) {
		val doc = queue.removeFirst()
		if (doc.isDirectory) {
			queue.addAll(doc.listFiles())
		} else if (doc.isFile) {
			val name = doc.name ?: continue
			if (name.matches(musicRegex)) {
				files.add(doc)
			}
		}
	}

	if (files.isEmpty()) return@withContext emptyList()

	val semaphore = Semaphore(8)

	val songsWithoutArt = files.map { doc ->
		async {
			semaphore.withPermit {
				val retriever = MediaMetadataRetriever()
				try {
					retriever.setDataSource(context, doc.uri)
					val n = doc.name!!
					val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: n.substringBeforeLast(".")
					val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
					val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
					val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
					val trackStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
					val track = trackStr?.substringBefore("/")?.toIntOrNull() ?: 0

					Song(
						id = doc.uri.toString(), title = title, artist = artist, album = album,
						uri = doc.uri, duration = duration, track = track,
						albumArtFile = null
					)
				} catch (_: Exception) { null } finally { retriever.release() }
			}
		}
	}.awaitAll().filterNotNull()

	val artists = songsWithoutArt.groupToArtists()

	val artResults = artists.flatMap { it.albums }.map { album ->
		async {
			val firstSong = album.songs.firstOrNull() ?: return@async null
			semaphore.withPermit {
				val retriever = MediaMetadataRetriever()
				try {
					retriever.setDataSource(context, firstSong.uri)
					val art = retriever.embeddedPicture
					if (art != null) {
						val fileName = "art_${album.name.hashCode()}_${album.artist.hashCode()}.jpg"
						File(context.cacheDir, fileName).writeBytes(art)

						Triple((album.artist to album.name), fileName, art)
					} else null
				} catch (_: Exception) { null } finally { retriever.release() }
			}
		}
	}.awaitAll().filterNotNull()

	val albumArtFileMap = artResults.associate { it.first to it.second }
	val albumArtBytesMap = artResults.associate { it.first to it.third }

	songsWithoutArt.map { song ->
		val key = song.artist to song.album
		val file = albumArtFileMap[key]
		if (file != null) {
			song.copy(albumArtFile = file)
		} else {
			song
		}
	}
}

fun List<Song>.groupToArtists(): List<Artist> {
	return groupBy { it.artist }.map { (artistName, songs) ->
		val albums = songs.groupBy { it.album }.map { (albumName, albumSongs) ->
			Album(albumName, artistName, albumSongs.sortedBy { it.track })
		}.sortedBy { it.name }
		Artist(artistName, albums)
	}.sortedBy { it.name }
}

fun formatDuration(ms: Long): String {
	val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
	val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
	return "%d:%02d".format(minutes, seconds)
}

data class ThemeSet(val name: String, val light: FutureConfig, val dark: FutureConfig)

val themeSets = listOf(
	ThemeSet("Masem", FutureConfig.MasemLight, FutureConfig.MasemDark),
	ThemeSet("Aurora", FutureConfig.AuroraLight, FutureConfig.AuroraDark),
	ThemeSet("Julee", FutureConfig.JuleeLight, FutureConfig.JuleeDark),
	ThemeSet("Autumn", FutureConfig.AutumnLight, FutureConfig.AutumnDark),
	ThemeSet("Sunrise", FutureConfig.SunriseLight, FutureConfig.SunriseDark),
	ThemeSet("Sunset", FutureConfig.SunsetLight, FutureConfig.SunsetDark),
	ThemeSet("Painting", FutureConfig.PaintingLight, FutureConfig.PaintingDark),
	ThemeSet("Charcoal", FutureConfig.CharcoalLight, FutureConfig.CharcoalDark),
	ThemeSet("Celeste", FutureConfig.CelesteLight, FutureConfig.CelesteDark),
	ThemeSet(
		"Custom",
		FutureConfig(
			colors = FutureColors(primary = hexColor("#00FF88"), globalBg = hexColor("#001122")),
			backgroundAnimation = BackgroundAnimationType.CYBER_GRID
		),
		FutureConfig(
			colors = FutureColors(primary = hexColor("#00FF88"), globalBg = hexColor("#001122")),
			backgroundAnimation = BackgroundAnimationType.CYBER_GRID
		)
	)
)

