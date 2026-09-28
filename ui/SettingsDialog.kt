package com.mlib.albums

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mlib.future.FutureTheme
import com.mlib.future.components.ButtonType
import com.mlib.future.components.FutureButton
import com.mlib.future.components.FutureDialog
import com.mlib.future.components.FutureIconRemove
import com.mlib.future.components.FutureDropdown
import com.mlib.future.components.FutureLabeledToggle
import com.mlib.future.components.FutureNumberInput
import com.mlib.future.components.FutureSeparator
import com.mlib.future.components.FutureText
import com.mlib.future.components.FutureTextHeading
import com.mlib.future.components.FutureTextMuted
import com.mlib.future.components.FutureIconAddFolder
import com.mlib.future.components.FutureIconScan

@Composable
fun SettingsDialog(
	folders: List<Uri>,
	onAddFolder: () -> Unit,
	onRemoveFolder: (Uri) -> Unit,
	onScan: () -> Unit,
	isScanning: Boolean,
	themeIndex: Int,
	onThemeChange: (Int) -> Unit,
	seekAmount: String,
	onSeekAmountChange: (String) -> Unit,
	onDismiss: () -> Unit
) {
	var expanded by remember { mutableStateOf(false) }

	FutureDialog(title = "Settings", onDismissRequest = onDismiss) {
		Column(
			modifier = Modifier.fillMaxWidth(),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {

			FutureTextHeading("Music Folders:")
			if (folders.isEmpty()) {
				FutureTextMuted("No folders added")
			} else {
				folders.forEach { uri ->
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
					) {
						FutureText(
							uri.lastPathSegment ?: uri.toString(),
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.weight(1f).padding(end = 8.dp)
						)
						FutureButton(
							{ FutureIconRemove(color = FutureTheme.colors.error) },
							onClick = { onRemoveFolder(uri) },
							type = ButtonType.Destructive
						)
					}
				}
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
			) {
				FutureButton(
					"Add Folder",
					onClick = onAddFolder,
					modifier = Modifier.weight(1f),
				)

				FutureButton(
					if (isScanning) "Scanning..." else "Scan",
					onClick = onScan,
					enabled = !isScanning,
					modifier = Modifier.weight(1f),
				)
			}

			FutureSeparator(modifier = Modifier.fillMaxWidth())

			FutureText("Theme")
			FutureDropdown(
				expanded = expanded,
				onExpandedChange = { expanded = it },
				selectedText = themeSets[themeIndex].name,
				options = themeSets.map { it.name },
				onOptionSelected = { name ->
					onThemeChange(themeSets.indexOfFirst { it.name == name })
				}
			)

			FutureSeparator(modifier = Modifier.fillMaxWidth())

			FutureNumberInput(
				label = "Seek Amount (seconds)",
				value = seekAmount,
				onValueChange = onSeekAmountChange,
				placeholder = "10"
			)
		}
	}
}
