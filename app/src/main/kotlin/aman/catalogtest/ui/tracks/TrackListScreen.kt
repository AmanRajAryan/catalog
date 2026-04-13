package aman.catalogtest.ui.tracks

import aman.catalog.audio.models.Track
import aman.catalogtest.CatalogViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A reusable track list. Handles empty state, row rendering, favourite toggling,
 * and opening the track options dialog. Contains no business logic itself.
 *
 * @param tracks          The list of tracks to display.
 * @param viewModel       ViewModel for actions (favourite, simulate play, etc.).
 * @param emptyMessage    Message shown when the list is empty.
 * @param entryIdFor      Optional — maps a track to its PlaylistItem.entryId.
 *                        Non-null only inside a playlist detail screen. When provided,
 *                        the options dialog shows "Remove from Playlist".
 */
@Composable
fun TrackListScreen(
    tracks: List<Track>,
    viewModel: CatalogViewModel,
    emptyMessage: String = "No tracks found.",
    entryIdFor: ((Track) -> Long?)? = null,
    onNavigateToEditor: (Long) -> Unit = {},
    onNavigateToArtist: (id: Long) -> Unit = {},
    onNavigateToAlbumArtist: (id: Long) -> Unit = {},
    onNavigateToAlbum: (id: Long) -> Unit = {},
    onNavigateToCategory: (type: String, id: String, title: String) -> Unit = { _, _, _ -> }
) {
    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    if (tracks.isEmpty()) {
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = emptyMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // When entryIdFor is provided (playlist context), key by entryId so duplicate
        // tracks in a playlist each get a unique stable key.
        // Otherwise key by track.id — correct and stable for all other screens.
        itemsIndexed(
            items = tracks,
            key   = { index, track ->
                val entryId = entryIdFor?.invoke(track)
                if (entryId != null) "entry_$entryId" else "track_${track.id}_$index"
            }
        ) { _, track ->
            TrackItemRow(
                track           = track,
                onFavoriteClick = { viewModel.toggleFavorite(track.id) },
                onClick         = { selectedTrack = it }
            )
        }
    }

    selectedTrack?.let { track ->
        TrackOptionsDialog(
            track                   = track,
            viewModel               = viewModel,
            onDismiss               = { selectedTrack = null },
            entryId                 = entryIdFor?.invoke(track),
            onNavigateToEditor      = onNavigateToEditor,
            onNavigateToArtist      = onNavigateToArtist,
            onNavigateToAlbumArtist = onNavigateToAlbumArtist,
            onNavigateToAlbum       = onNavigateToAlbum,
            onNavigateToCategory    = onNavigateToCategory
        )
    }
}
