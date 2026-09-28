package com.mlib.albums

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.mlib.future.components.FutureAvatar
import com.mlib.future.components.FutureButton
import com.mlib.future.components.FutureText
import com.mlib.future.components.FutureTextMuted
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import com.mlib.future.components.FutureText
import com.mlib.future.components.FutureTextMuted
import com.mlib.future.components.FutureSeparator
import com.mlib.future.components.FutureIconPlay
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.runtime.remember

@Composable
fun AlbumsScreen(
	artist: Artist?,
	onAlbumClick: (Album) -> Unit,
	onPlayAlbum: (Album) -> Unit
) {
	if (artist == null) return
	LazyColumn(
		modifier = Modifier.fillMaxSize().padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		itemsIndexed(artist.albums) { index, album ->

			Row(
				modifier = Modifier.fillMaxWidth().clickable { onAlbumClick(album) },
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(16.dp),
			) {
				val context = LocalContext.current
				val bitmap = remember(album.songs.firstOrNull()?.albumArtFile) {
					val fileName = album.songs.firstOrNull()?.albumArtFile
					if (fileName != null) {
						try {
							BitmapFactory.decodeFile(File(context.cacheDir, fileName).absolutePath)?.asImageBitmap()
						} catch (_: Exception) { null }
					} else null
				}
				if (bitmap != null) {
					Image(
						bitmap = bitmap,
						contentDescription = null,
						modifier = Modifier.size(46.dp)
					)
				} else {
					FutureAvatar(album.name, modifier = Modifier.size(56.dp))
				}

				Column(modifier = Modifier.weight(1f)) {
					FutureText(album.name, maxLines = 1, style = TextStyle(fontSize = 24.sp))
					FutureTextMuted("${album.songs.size} ${if (album.songs.size == 1) "Song" else "Songs"}")
				}
				FutureButton(
					onClick = { onPlayAlbum(album) },
					icon = { FutureIconPlay() }
				)
			}
			if (index != artist.albums.lastIndex) {
				Spacer(modifier = Modifier.height(8.dp))
				FutureSeparator(Modifier.fillMaxWidth())
			}
		}
	}
}
