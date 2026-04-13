package aman.catalogtest.ui.tracks

import aman.catalog.audio.CatalogEditor
import aman.catalog.audio.models.Track
import aman.catalog.audio.models.TrackPicture
import aman.catalogtest.CatalogViewModel
import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackEditorScreen(
    viewModel: CatalogViewModel,
    trackId: Long,
    onBack: () -> Unit
) {
    val tracks by viewModel.tracks.collectAsState()
    val track = remember(tracks, trackId) { tracks.find { it.id == trackId } }

    if (track == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    TrackEditorContent(track = track, viewModel = viewModel, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackEditorContent(
    track: Track,
    viewModel: CatalogViewModel,
    onBack: () -> Unit
) {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // tag state — loaded dynamically from the file
    // List of (key, value) preserves order and allows duplicate keys
    var tagEntries        by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoadingTags     by remember { mutableStateOf(true) }
    var isLoadingPictures by remember { mutableStateOf(true) }
    var pictures          by remember { mutableStateOf<List<TrackPicture>>(emptyList()) }

    // screen state
    var isSaving            by remember { mutableStateOf(false) }
    var showSafChoiceDialog by remember { mutableStateOf(false) }
    var pendingRetryAction  by remember { mutableStateOf<(() -> Unit)?>(null) }

    val busy = isSaving || isLoadingTags

    // load tags and pictures on entry
    LaunchedEffect(track.id) {
        isLoadingTags     = true
        isLoadingPictures = true
        tagEntries        = viewModel.readTags(track).entries.map { it.key to it.value }
        isLoadingTags     = false
        pictures          = viewModel.getPicturesForTrack(track)
        isLoadingPictures = false
    }

    // permission launchers
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

    // save logic
    fun executeSave(allowFallback: Boolean) {
        isSaving = true
        coroutineScope.launch {
            val result = viewModel.updateTrackTags(context, track, tagEntries.toMap(), allowFallback)
            when (result) {
                is CatalogEditor.EditResult.Success -> {
                    Toast.makeText(context, "Saved successfully.", Toast.LENGTH_SHORT).show()
                    isSaving = false
                    onBack()
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
                is CatalogEditor.EditResult.IOError        -> { isSaving = false; Toast.makeText(context, "IO error: ${result.message}",     Toast.LENGTH_LONG).show() }
                is CatalogEditor.EditResult.TagWriteFailed -> { isSaving = false; Toast.makeText(context, "TagLib rejected the tags.",        Toast.LENGTH_LONG).show() }
                is CatalogEditor.EditResult.ArtWriteFailed -> { isSaving = false; Toast.makeText(context, "Artwork write failed.",            Toast.LENGTH_LONG).show() }
            }
        }
    }

    // UI
    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(track.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    OutlinedButton(
                        enabled  = !busy,
                        onClick  = {
                            isSaving = true
                            coroutineScope.launch {
                                val result = viewModel.stressTestConcurrency(context, track, tagEntries.toMap())
                                isSaving = false
                                when (result) {
                                    is CatalogEditor.EditResult.Success -> {
                                        Toast.makeText(context, "Stress test passed.", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }
                                    is CatalogEditor.EditResult.PermissionRequired,
                                    is CatalogEditor.EditResult.SafPermissionMissing -> {
                                        Toast.makeText(context, "Grant permission via normal Save first.", Toast.LENGTH_LONG).show()
                                    }
                                    else -> Toast.makeText(context, "Stress test result: $result", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    ) { Text("Stress Test") }

                    Button(
                        enabled  = !busy,
                        onClick  = {
                            if (CatalogEditor.hasSafPermission(context, track.path)) {
                                executeSave(false)
                            } else {
                                showSafChoiceDialog = true
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->

        if (isLoadingTags) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            if (isLoadingPictures) {
                Box(
                    modifier         = Modifier.fillMaxWidth().height(140.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else if (pictures.isNotEmpty()) {
                Text(
                    text  = "Artwork (${pictures.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pictures.forEach { picture ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val context = LocalContext.current
                            AndroidView(
                                factory = { ctx ->
                                    ImageView(ctx).apply {
                                        scaleType = ImageView.ScaleType.CENTER_CROP
                                    }
                                },
                                update = { imageView ->
                                    val source = ImageDecoder.createSource(ByteBuffer.wrap(picture.data))
                                    val drawable = ImageDecoder.decodeDrawable(source)
                                    imageView.setImageDrawable(drawable)
                                    (drawable as? AnimatedImageDrawable)?.start()
                                },
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Text(
                                text     = picture.description.ifBlank { "No description" },
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                text  = picture.mimeType,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                HorizontalDivider()
            }

            Text(
                text  = "Tags (${tagEntries.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            tagEntries.forEachIndexed { index, (key, value) ->
                OutlinedTextField(
                    value         = value,
                    onValueChange = { newValue ->
                        tagEntries = tagEntries.toMutableList().also { it[index] = key to newValue }
                    },
                    label      = { Text(key) },
                    singleLine = true,
                    modifier   = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            Text(
                text  = "File",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text  = track.path,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

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
