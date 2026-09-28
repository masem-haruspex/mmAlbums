package com.mlib.albums

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import com.mlib.future.components.FutureAvatar
import com.mlib.future.components.FutureCard
import com.mlib.future.components.FutureLoading
import com.mlib.future.components.FutureText
import com.mlib.future.components.FutureTextMuted
import com.mlib.future.components.FutureSeparator

@Composable
fun ArtistsScreen(
	artists: List<Artist>,
	isLoading: Boolean,
	onArtistClick: (Artist) -> Unit
) {
	if (isLoading) {
		Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			FutureLoading("Scanning")
		}
	} else if (artists.isEmpty()) {
		Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			FutureTextMuted("No music found. Add folders in settings.")
		}
	} else {
		LazyColumn(
			modifier = Modifier.fillMaxSize().padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			itemsIndexed(artists) { index, artist ->
				Row(
					modifier = Modifier.fillMaxWidth().clickable { onArtistClick(artist) },
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(16.dp),
				) {
					FutureAvatar(artist.name)
                    Column(modifier = Modifier.weight(1f)) {
                        FutureText(artist.name, maxLines = 1, style = TextStyle(fontSize = 24.sp))
                        FutureTextMuted("${artist.albums.size} ${if (artist.albums.size == 1) "Album" else "Albums"}")
                    }
				}

				if (index != artists.lastIndex) {
					Spacer(modifier = Modifier.height(8.dp))
					FutureSeparator(Modifier.fillMaxWidth())
				}
			}
		}
	}
}
