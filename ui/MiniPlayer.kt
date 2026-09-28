package com.mlib.albums

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.mlib.future.FutureTheme
import com.mlib.future.components.FutureAvatar
import com.mlib.future.components.FutureButton
import com.mlib.future.components.FutureSlider
import com.mlib.future.components.FutureText
import com.mlib.future.components.FutureTextMuted
import com.mlib.future.components.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
fun MiniPlayer(
	player: PlaybackController,
	seekAmount: Int,
	onTogglePlay: () -> Unit = { player.togglePlay() },
	onNext: () -> Unit = { player.next() },
	onPrev: () -> Unit = { player.prev() },
	onSeek: (Int) -> Unit = { player.seek(it) },
	onCycleRepeat: () -> Unit = { player.cycleRepeat() },
	onTitleClick: () -> Unit = {}
) {
	val song = player.currentSong
	val context = LocalContext.current
	val bitmap = remember(song?.albumArtFile) {
		val fileName = song?.albumArtFile
		if (fileName != null) {
			try {
				BitmapFactory.decodeFile(File(context.cacheDir, fileName).absolutePath)?.asImageBitmap()
			} catch (_: Exception) { null }
		} else null
	}
	var isDragging by remember { mutableStateOf(false) }
	var dragProgress by remember { mutableFloatStateOf(player.progress) }

	LaunchedEffect(player.progress) {
		if (!isDragging)
			dragProgress = player.progress
	}

	Column(
		modifier = Modifier
		.fillMaxWidth()
		.background(FutureTheme.colors.componentBg)
		.border(
			1.dp,
			FutureTheme.colors.primary,
			RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
		)
		.padding(12.dp)
	) {

		Spacer(modifier = Modifier.height(8.dp))

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.clickable { onTitleClick() },
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
			if (bitmap != null) {
				Image(
					bitmap = bitmap,
					contentDescription = null,
					modifier = Modifier.size(40.dp)
				)
			} else {
				FutureAvatar(song?.title ?: "", modifier = Modifier.size(40.dp))
			}
			FutureTextHeading(song?.title ?: "", maxLines = 1)
		}

		Spacer(modifier = Modifier.height(8.dp))

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceEvenly,
			verticalAlignment = Alignment.CenterVertically
		) {
			FutureButton(onClick = onPrev, icon = { FutureIconPreviousSong() })
			FutureButton(onClick = { onSeek(-seekAmount) }, icon = { FutureIconBack10() })
			FutureButton(
				onClick = onTogglePlay,
				icon = { if (player.isPlaying) FutureIconPause() else FutureIconPlay() },
			)
			FutureButton(onClick = { onSeek(seekAmount) }, icon = { FutureIconForward10() })
			FutureButton(onClick = onNext, icon = { FutureIconNextSong() })
			FutureButton(
				onClick = onCycleRepeat,
				icon = {
					when (player.repeatMode) {
						RepeatMode.NONE -> FutureIconRepeat(color = FutureTheme.colors.textMuted)
						RepeatMode.SINGLE -> FutureIconRepeatSingle()
						RepeatMode.ALL -> FutureIconRepeat()
					}
				}
			)
		}

		Spacer(modifier = Modifier.height(8.dp))

		FutureSlider(
			value = dragProgress,
			onValueChange = { fraction ->
				isDragging = true
				dragProgress = fraction
			},
			onValueChangeFinished = {
				isDragging = false
				val durationMs = player.getDuration()
				if (durationMs > 0) {
					player.seekTo((dragProgress * durationMs).toLong())
				}
			},
			modifier = Modifier.fillMaxWidth()
		)
	}
}
