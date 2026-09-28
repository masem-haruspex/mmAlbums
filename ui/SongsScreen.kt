package com.mlib.albums

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlib.future.FutureTheme
import com.mlib.future.components.ButtonType
import com.mlib.future.components.FutureButton
import com.mlib.future.components.FutureIconBlacklist
import com.mlib.future.components.FutureIconWhitelist
import com.mlib.future.components.FutureSeparator
import com.mlib.future.components.FutureText
import com.mlib.future.components.FutureTextMuted
import kotlinx.coroutines.launch

@Composable
fun SongsScreen(
	album: Album?,
	blacklist: Set<String>,
	onToggleBlacklist: (String) -> Unit,
	onPlayFrom: (Int) -> Unit,
	scrollToSongId: String? = null,
	scrollNonce: Int = 0,
	onScrollHandled: () -> Unit = {}
) {
	if (album == null) return

	val listState = rememberLazyListState()
	val highlight = remember { Animatable(0f) }
	var highlightedId by remember { mutableStateOf<String?>(null) }

	val pulseColor = FutureTheme.colors.primary

	LaunchedEffect(scrollToSongId, scrollNonce) {
		val targetId = scrollToSongId ?: return@LaunchedEffect
		val idx = album.songs.indexOfFirst { it.id == targetId }
		if (idx >= 0) {
			highlightedId = targetId

			launch { listState.animateScrollToItem(idx) }

			repeat(4) {
				highlight.animateTo(1f, animationSpec = tween(260))
				highlight.animateTo(0f, animationSpec = tween(260))
			}

			listState.animateScrollToItem(idx)
		}
		highlightedId = null
		highlight.snapTo(0f)
		onScrollHandled()
	}

	LazyColumn(
		state = listState,
		modifier = Modifier.fillMaxSize()
	) {
		itemsIndexed(album.songs) { index, song ->
			val isBlacklisted = blacklist.contains(song.id)
			val isHighlighted = song.id == highlightedId
			val a = if (isHighlighted) highlight.value else 0f

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.background(
						if (a > 0f) pulseColor.copy(alpha = a * 0.25f)
						else Color.Transparent
					)
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.clickable { onPlayFrom(index) }
						.padding(horizontal = 16.dp, vertical = 12.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column(modifier = Modifier.weight(1f)) {
						FutureText(song.title, maxLines = 1, style = TextStyle(fontSize = 24.sp))
						FutureTextMuted(formatDuration(song.duration))
					}

					FutureButton(
						onClick = { onToggleBlacklist(song.id) },
						icon = {
							if (!isBlacklisted)
								FutureIconBlacklist(color = FutureTheme.colors.error)
							else
								FutureIconWhitelist()
						},
						type = if (!isBlacklisted) ButtonType.Destructive else ButtonType.Primary
					)
				}

				if (index < album.songs.size - 1) {
					FutureSeparator(Modifier.fillMaxWidth())
				}
			}
		}
	}
}
