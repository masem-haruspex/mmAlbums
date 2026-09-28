package com.mlib.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mlib.future.FutureTheme
import com.mlib.future.components.FutureButton
import com.mlib.future.components.FutureSeparator
import com.mlib.future.components.FutureText
import com.mlib.future.components.FutureTextMuted

@Composable
fun SearchResultsScreen(
	results: List<Song>,
	onPlay: (Song) -> Unit,
) {
	if (results.isEmpty()) {
		Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			FutureText("No songs found.")
		}
	} else {
		LazyColumn(
			modifier = Modifier.fillMaxSize().padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
	        itemsIndexed(results) { index, song ->

				Row(
					modifier = Modifier
					.fillMaxWidth()
					.clickable { onPlay(song) }
					.padding(horizontal = 16.dp, vertical = 12.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp)
				) {
					Column(modifier = Modifier.weight(1f)) {
						FutureText(
							song.title,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
						FutureTextMuted(
							"${song.artist} • ${song.album} • ${formatDuration(song.duration)}",
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}

			if (index < results.size - 1) {

				Spacer(modifier = Modifier.height(8.dp))
				FutureSeparator(Modifier.fillMaxWidth())
			}

			}
		}
	}
}
