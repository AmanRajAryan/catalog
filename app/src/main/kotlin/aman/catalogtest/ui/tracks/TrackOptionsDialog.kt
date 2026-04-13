package aman.catalogtest.ui.tracks

import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.Artist
import aman.catalog.audio.models.Composer
import aman.catalog.audio.models.Genre
import aman.catalog.audio.models.IdName
import aman.catalog.audio.models.Lyricist
import aman.catalog.audio.models.Track
import aman.catalogtest.CatalogViewModel
import aman.catalogtest.util.toHumanDuration
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Bottom-sheet style dialog shown when a track row is tapped.
 *
 * @param track                The track the user tapped.
 * @param viewModel            ViewModel for actions.
 * @param onDismiss            Called when the dialog should close.
 * @param entryId              Non-null only inside a playlist detail screen — enables "Remove from Playlist".
 * @param onNavigateToArtist   Navigate to a track artist detail screen.
 * @param onNavigateToAlbumArtist Navigate to an album artist detail screen.
 * @param onNavigateToAlbum    Navigate to an album detail screen.
 * @param onNavigateToCategory Navigate to a generic category detail screen (genre, composer, lyricist, year, folder).
 */
@Composable
fun TrackOptionsDialog(
    track: Track,
    viewModel: CatalogViewModel,
    onDismiss: () -> Unit,
    entryId: Long? = null,
    onNavigateToEditor: (Long) -> Unit = {},
    onNavigateToArtist: (id: Long) -> Unit = {},
    onNavigateToAlbumArtist: (id: Long) -> Unit = {},
    onNavigateToAlbum: (id: Long) -> Unit = {},
    onNavigateToCategory: (type: String, id: String, title: String) -> Unit = { _, _, _ -> }
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showAddToPlaylist  by remember { mutableStateOf(false) }
    var showDebugViewer    by remember { mutableStateOf(false) }
    var showSimulateSlider by remember { mutableStateOf(false) }
    var showReadTags       by remember { mutableStateOf(false) }
    var showGoTo           by remember { mutableStateOf(false) }

    // Picker states — null = not showing, empty list = loading, non-empty = ready
    var artistPickerItems   by remember { mutableStateOf<List<Artist>?>(null) }
    var genrePickerItems    by remember { mutableStateOf<List<Genre>?>(null) }
    var composerPickerItems by remember { mutableStateOf<List<Composer>?>(null) }
    var lyricistPickerItems by remember { mutableStateOf<List<Lyricist>?>(null) }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            track     = track,
            viewModel = viewModel,
            onDismiss = { showAddToPlaylist = false; onDismiss() }
        )
        return
    }

    if (showDebugViewer) {
        TrackDebugDialog(
            track     = track,
            onDismiss = { showDebugViewer = false; onDismiss() }
        )
        return
    }

    if (showSimulateSlider) {
        SimulatePlayDialog(
            track     = track,
            onConfirm = { durationMs ->
                viewModel.simulatePlay(track, durationMs)
                Toast.makeText(context, "Play logged: ${durationMs.toHumanDuration()} of ${track.title}", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            onDismiss = { showSimulateSlider = false }
        )
        return
    }

    if (showReadTags) {
        RawTagsDialog(
            track     = track,
            viewModel = viewModel,
            onDismiss = { showReadTags = false }
        )
        return
    }

    if (showGoTo) {
        GoToDialog(
            track               = track,
            viewModel           = viewModel,
            onDismiss           = { showGoTo = false },
            onNavigateToArtist      = onNavigateToArtist,
            onNavigateToAlbumArtist = onNavigateToAlbumArtist,
            onNavigateToAlbum       = onNavigateToAlbum,
            onNavigateToCategory    = onNavigateToCategory,
            onNavigated             = onDismiss,
            artistPickerItems       = artistPickerItems,
            genrePickerItems        = genrePickerItems,
            composerPickerItems     = composerPickerItems,
            lyricistPickerItems     = lyricistPickerItems,
            onArtistPickerReady     = { artistPickerItems = it },
            onGenrePickerReady      = { genrePickerItems = it },
            onComposerPickerReady   = { composerPickerItems = it },
            onLyricistPickerReady   = { lyricistPickerItems = it }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column {
                OptionItem("▶  Simulate Play") { showSimulateSlider = true }
                OptionItem("＋  Add to Playlist") { showAddToPlaylist = true }

                if (entryId != null) {
                    OptionItem(
                        label = "✕  Remove from Playlist",
                        tint  = MaterialTheme.colorScheme.error
                    ) {
                        viewModel.removeEntryFromPlaylist(entryId)
                        onDismiss()
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                OptionItem("➡  Go to...") { showGoTo = true }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                OptionItem("✎  Catalog Editor") { onDismiss(); onNavigateToEditor(track.id) }
                OptionItem("🏷  Read Tags") { showReadTags = true }
                OptionItem("🔍  Debug Viewer") { showDebugViewer = true }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// === GoToDialog ================================

@Composable
private fun GoToDialog(
    track: Track,
    viewModel: CatalogViewModel,
    onDismiss: () -> Unit,
    onNavigated: () -> Unit,
    onNavigateToArtist: (id: Long) -> Unit,
    onNavigateToAlbumArtist: (id: Long) -> Unit,
    onNavigateToAlbum: (id: Long) -> Unit,
    onNavigateToCategory: (type: String, id: String, title: String) -> Unit,
    artistPickerItems: List<Artist>?,
    genrePickerItems: List<Genre>?,
    composerPickerItems: List<Composer>?,
    lyricistPickerItems: List<Lyricist>?,
    onArtistPickerReady: (List<Artist>) -> Unit,
    onGenrePickerReady: (List<Genre>) -> Unit,
    onComposerPickerReady: (List<Composer>) -> Unit,
    onLyricistPickerReady: (List<Lyricist>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val activeColor   = MaterialTheme.colorScheme.onSurface
    val disabledColor = MaterialTheme.colorScheme.onSurfaceVariant

    // pickers render on top — return early so the GoTo dialog is hidden behind them
    artistPickerItems?.let { items ->
        EntityPickerDialog(title = "Go to Artist", items = items, onDismiss = { onArtistPickerReady(emptyList()); onDismiss() }) { artist ->
            onNavigated(); onNavigateToArtist(artist.id)
        }
        return
    }
    genrePickerItems?.let { items ->
        EntityPickerDialog(title = "Go to Genre", items = items, onDismiss = { onGenrePickerReady(emptyList()); onDismiss() }) { genre ->
            onNavigated(); onNavigateToCategory("genre", genre.id.toString(), genre.name)
        }
        return
    }
    composerPickerItems?.let { items ->
        EntityPickerDialog(title = "Go to Composer", items = items, onDismiss = { onComposerPickerReady(emptyList()); onDismiss() }) { composer ->
            onNavigated(); onNavigateToCategory("composer", composer.id.toString(), composer.name)
        }
        return
    }
    lyricistPickerItems?.let { items ->
        EntityPickerDialog(title = "Go to Lyricist", items = items, onDismiss = { onLyricistPickerReady(emptyList()); onDismiss() }) { lyricist ->
            onNavigated(); onNavigateToCategory("lyricist", lyricist.id.toString(), lyricist.name)
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to", style = MaterialTheme.typography.titleMedium) },
        text = {
            LazyColumn {
                item {
                    OptionItem(
                        label = "💿  Album",
                        tint  = if (track.albumId != null) activeColor else disabledColor
                    ) { track.albumId?.let { onNavigated(); onNavigateToAlbum(it) } }
                }
                item {
                    OptionItem(
                        label = "🎤  Album Artist",
                        tint  = if (track.albumArtistId != null) activeColor else disabledColor
                    ) { track.albumArtistId?.let { onNavigated(); onNavigateToAlbumArtist(it) } }
                }
                item {
                    OptionItem(label = "👤  Artist", tint = activeColor) {
                        if (track.artists.size == 1) {
                            onNavigated(); onNavigateToArtist(track.artists.first().id)
                        } else {
                            onArtistPickerReady(emptyList())
                            scope.launch {
                                onArtistPickerReady(track.artists.mapNotNull {
                                    viewModel.getTrackArtistById(it.id).firstOrNull()
                                })
                            }
                        }
                    }
                }
                item {
                    OptionItem(label = "🎵  Genre", tint = activeColor) {
                        if (track.genres.size == 1) {
                            val g = track.genres.first()
                            onNavigated(); onNavigateToCategory("genre", g.id.toString(), g.name)
                        } else {
                            onGenrePickerReady(emptyList())
                            scope.launch {
                                onGenrePickerReady(track.genres.mapNotNull {
                                    viewModel.getGenreById(it.id).firstOrNull()
                                })
                            }
                        }
                    }
                }
                item {
                    OptionItem(label = "🎼  Composer", tint = activeColor) {
                        if (track.composers.size == 1) {
                            val c = track.composers.first()
                            onNavigated(); onNavigateToCategory("composer", c.id.toString(), c.name)
                        } else {
                            onComposerPickerReady(emptyList())
                            scope.launch {
                                onComposerPickerReady(track.composers.mapNotNull {
                                    viewModel.getComposerById(it.id).firstOrNull()
                                })
                            }
                        }
                    }
                }
                item {
                    OptionItem(label = "✍  Lyricist", tint = activeColor) {
                        if (track.lyricists.size == 1) {
                            val l = track.lyricists.first()
                            onNavigated(); onNavigateToCategory("lyricist", l.id.toString(), l.name)
                        } else {
                            onLyricistPickerReady(emptyList())
                            scope.launch {
                                onLyricistPickerReady(track.lyricists.mapNotNull {
                                    viewModel.getLyricistById(it.id).firstOrNull()
                                })
                            }
                        }
                    }
                }
                item {
                    OptionItem(label = "📅  Year", tint = activeColor) {
                        onNavigated()
                        onNavigateToCategory("year", track.year.toString(), track.year.toString())
                    }
                }
                item {
                    OptionItem(label = "📁  Folder", tint = activeColor) {
                        onNavigated()
                        onNavigateToCategory("folder", track.folderPath, track.folderName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Back") }
        }
    )
}


// === EntityPickerDialog ========================

private interface PickerEntity {
    val id: Long
    val name: String
    val coverArtPath: ArtPath?
}

private fun Artist.asPickerEntity()   = object : PickerEntity { override val id = this@asPickerEntity.id; override val name = this@asPickerEntity.name; override val coverArtPath = this@asPickerEntity.coverArtPath }
private fun Genre.asPickerEntity()    = object : PickerEntity { override val id = this@asPickerEntity.id; override val name = this@asPickerEntity.name; override val coverArtPath = this@asPickerEntity.coverArtPath }
private fun Composer.asPickerEntity() = object : PickerEntity { override val id = this@asPickerEntity.id; override val name = this@asPickerEntity.name; override val coverArtPath = this@asPickerEntity.coverArtPath }
private fun Lyricist.asPickerEntity() = object : PickerEntity { override val id = this@asPickerEntity.id; override val name = this@asPickerEntity.name; override val coverArtPath = this@asPickerEntity.coverArtPath }

@Composable
private fun <T> EntityPickerDialog(
    title: String,
    items: List<T>,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) where T : Any {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (items.isEmpty()) {
                CircularProgressIndicator()
            } else {
                LazyColumn {
                    items(items) { item ->
                        val entity = when (item) {
                            is Artist   -> item.asPickerEntity()
                            is Genre    -> item.asPickerEntity()
                            is Composer -> item.asPickerEntity()
                            is Lyricist -> item.asPickerEntity()
                            else        -> null
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AsyncImage(
                                model              = entity?.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                                contentDescription = null,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Text(
                                text     = entity?.name ?: "",
                                style    = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SimulatePlayDialog(
    track: Track,
    onConfirm: (durationMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    // Slider position 0f–1f representing fraction of total track duration
    var fraction by remember { mutableFloatStateOf(1f) }
    val selectedMs = (track.durationMs * fraction).toLong()

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Simulate Play") },
        text             = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Text(
                    text  = "${selectedMs.toHumanDuration()} / ${track.durationMs.toHumanDuration()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value         = fraction,
                    onValueChange = { fraction = it },
                    modifier      = Modifier.fillMaxWidth()
                )
                Text(
                    text  = track.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton    = {
            Button(onClick = { onConfirm(selectedMs) }) { Text("Log Play") }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun OptionItem(
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Text(
        text     = label,
        style    = MaterialTheme.typography.bodyLarge,
        color    = tint,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp)
    )
}

// === RawTagsDialog — keys red, values blue ============

@Composable
private fun RawTagsDialog(
    track: Track,
    viewModel: CatalogViewModel,
    onDismiss: () -> Unit
) {
    var tags      by remember { mutableStateOf<Map<String, String>?>(null) }
    val scope     = rememberCoroutineScope()

    LaunchedEffect(track.id) {
        scope.launch {
            tags = viewModel.readTags(track)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Raw Tags") },
        text             = {
            if (tags == null) {
                CircularProgressIndicator()
            } else {
                val entries = tags!!.entries.toList()
                LazyColumn {
                    items(entries) { (key, value) ->
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color(0xFFE53935), fontFamily = FontFamily.Monospace)) {
                                    append(key)
                                }
                                append(" = ")
                                withStyle(SpanStyle(color = Color(0xFF1E88E5), fontFamily = FontFamily.Monospace)) {
                                    append(value)
                                }
                            },
                            style    = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
