package aman.catalogtest.ui.tracks

import aman.catalog.audio.CatalogEditor
import aman.catalog.audio.models.Track
import aman.catalogtest.CatalogViewModel
import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun TrackEditorDialog(
    track: Track,
    viewModel: CatalogViewModel,
    onDismiss: () -> Unit
) {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var editTitle  by remember { mutableStateOf(track.title) }
    var editArtist by remember { mutableStateOf(track.artist) }
    var editAlbum  by remember { mutableStateOf(track.album) }
    var isSaving   by remember { mutableStateOf(false) }
    var showSafChoiceDialog by remember { mutableStateOf(false) }
    var pendingRetryAction  by remember { mutableStateOf<(() -> Unit)?>(null) }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingRetryAction?.invoke()
        } else {
            Toast.makeText(context, "Permission denied.", Toast.LENGTH_SHORT).show()
            isSaving = false
        }
        pendingRetryAction = null
    }

    val safFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                pendingRetryAction?.invoke()
            }
        } else {
            isSaving = false
        }
        pendingRetryAction = null
    }

    fun executeSave(allowFallback: Boolean) {
        isSaving = true
        val tags = mapOf("TITLE" to editTitle, "ARTIST" to editArtist, "ALBUM" to editAlbum)
        coroutineScope.launch {
            val result = viewModel.updateTrackTags(context, track, tags, allowFallback)
            when (result) {
                is CatalogEditor.EditResult.Success -> {
                    Toast.makeText(context, "Saved successfully.", Toast.LENGTH_SHORT).show()
                    isSaving = false
                    onDismiss()
                }
                is CatalogEditor.EditResult.PermissionRequired -> {
                    pendingRetryAction = { executeSave(allowFallback = true) }
                    intentSenderLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())
                }
                is CatalogEditor.EditResult.SafPermissionMissing -> {
                    Toast.makeText(context, "Grant access to '${result.folderPath.substringAfterLast("/")}'.", Toast.LENGTH_LONG).show()
                    pendingRetryAction = { executeSave(allowFallback = false) }
                    safFolderLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                }
                is CatalogEditor.EditResult.InputError     -> { isSaving = false; Toast.makeText(context, "Input error: ${result.message}", Toast.LENGTH_LONG).show() }
                is CatalogEditor.EditResult.IOError        -> { isSaving = false; Toast.makeText(context, "IO error: ${result.message}", Toast.LENGTH_LONG).show() }
                is CatalogEditor.EditResult.TagWriteFailed -> { isSaving = false; Toast.makeText(context, "TagLib rejected the tags.", Toast.LENGTH_LONG).show() }
                is CatalogEditor.EditResult.ArtWriteFailed -> { isSaving = false; Toast.makeText(context, "Artwork write failed.", Toast.LENGTH_LONG).show() }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title            = { Text("Edit Tags") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = editTitle,  onValueChange = { editTitle  = it }, label = { Text("Title") },  singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = editArtist, onValueChange = { editArtist = it }, label = { Text("Artist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = editAlbum,  onValueChange = { editAlbum  = it }, label = { Text("Album") },  singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton    = {
            Button(
                enabled = !isSaving,
                onClick = {
                    if (CatalogEditor.hasSafPermission(context, track.path)) {
                        executeSave(false)
                    } else {
                        showSafChoiceDialog = true
                    }
                }
            ) { Text(if (isSaving) "Saving…" else "Save") }
        },
        dismissButton    = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !isSaving,
                    onClick = {
                        isSaving = true
                        val tags = mapOf("TITLE" to editTitle, "ARTIST" to editArtist, "ALBUM" to editAlbum)
                        coroutineScope.launch {
                            val result = viewModel.stressTestConcurrency(context, track, tags)
                            isSaving = false
                            when (result) {
                                is CatalogEditor.EditResult.Success -> {
                                    Toast.makeText(context, "Stress test passed.", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                is CatalogEditor.EditResult.PermissionRequired,
                                is CatalogEditor.EditResult.SafPermissionMissing -> {
                                    Toast.makeText(context, "Grant permission via normal Save first.", Toast.LENGTH_LONG).show()
                                }
                                else -> Toast.makeText(context, "Stress test result: $result", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) { Text("Stress Test") }
                TextButton(enabled = !isSaving, onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    if (showSafChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showSafChoiceDialog = false },
            title            = { Text("Write Permission") },
            text             = { Text("Grant access to the whole folder for silent future edits, or authorize just this one file?") },
            confirmButton    = {
                Button(onClick = {
                    showSafChoiceDialog = false
                    pendingRetryAction  = { executeSave(false) }
                    safFolderLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                }) { Text("Grant Folder Access") }
            },
            dismissButton    = {
                TextButton(onClick = {
                    showSafChoiceDialog = false
                    executeSave(true)
                }) { Text("Just This File") }
            }
        )
    }
}
