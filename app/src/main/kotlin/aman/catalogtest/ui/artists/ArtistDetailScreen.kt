package aman.catalogtest.ui.artists

import aman.catalog.audio.models.Album
import aman.catalog.audio.models.MatchedAlbum
import aman.catalog.audio.models.Track
import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.MatchedGenre
import aman.catalog.audio.models.ContextualSortOption
import aman.catalogtest.CatalogViewModel
import aman.catalogtest.ui.components.MatchedAlbumRow
import aman.catalogtest.ui.tracks.TrackItemRow
import aman.catalogtest.ui.tracks.TrackOptionsDialog
import aman.catalogtest.util.toHumanDuration
import aman.catalogtest.util.toPlayCountString
import aman.catalogtest.util.toRelativeTimeString
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import aman.catalogtest.ui.components.MatchedGenreChip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import aman.catalogtest.ui.components.MosaicArt
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    viewModel: CatalogViewModel,
    artistId: Long,
    isAlbumArtist: Boolean,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (id: Long) -> Unit = {},
    onNavigateToAlbumArtist: (id: Long) -> Unit = {},
    onNavigateToCategory: (type: String, id: String, title: String) -> Unit = { _, _, _ -> },
    onNavigateToEditor: (Long) -> Unit = {}
) {
    val artistFlow = remember(artistId, isAlbumArtist) {
        if (isAlbumArtist) viewModel.getAlbumArtistById(artistId)
        else viewModel.getTrackArtistById(artistId)
    }
    val artist by artistFlow.collectAsState(initial = null)
    val artistName = artist?.name ?: "Artist"
    var trackSort by remember { mutableStateOf(ContextualSortOption.Track.TITLE_ASC) }
    var albumSort by remember { mutableStateOf(ContextualSortOption.Album.YEAR_DESC) }
    var genreSort by remember { mutableStateOf(ContextualSortOption.Genre.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }

    val category   = if (isAlbumArtist) "album_artist" else "artist"
    val tracksFlow = remember(artistId, isAlbumArtist, trackSort) {
        viewModel.getTracksForCategory(category, artistId, sort = trackSort)
    }
    val tracks by tracksFlow.collectAsState(initial = emptyList())

    val ownAlbumsFlow = remember(artistId, albumSort) { viewModel.getAlbumsForAlbumArtist(artistId, albumSort) }
    val ownAlbums by ownAlbumsFlow.collectAsState(initial = emptyList())

    val appearsOnFlow = remember(artistId, isAlbumArtist, albumSort) {
        if (isAlbumArtist) flowOf(emptyList<MatchedAlbum>())
        else viewModel.getAppearsOnAlbums(artistId, albumSort)
    }
    val appearsOn by appearsOnFlow.collectAsState(initial = emptyList())

    val genresFlow = remember(artistId, isAlbumArtist, genreSort) {
        if (isAlbumArtist) viewModel.getGenresForAlbumArtist(artistId, genreSort)
        else viewModel.getGenresForTrackArtist(artistId, genreSort)
    }
    val genres by genresFlow.collectAsState(initial = emptyList())

    val mosaicFlow = remember(artistId, isAlbumArtist) {
        if (isAlbumArtist) viewModel.getMosaicForAlbumArtistFlow(artistId)
        else viewModel.getMosaicForTrackArtistFlow(artistId)
    }
    val mosaicPaths by mosaicFlow.collectAsState(initial = emptyList())

    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(artistName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.List, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded         = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text    = { Text("— Tracks —", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) },
                                onClick = {},
                                enabled = false
                            )
                            ContextualSortOption.Track.entries.forEach { option ->
                                DropdownMenuItem(
                                    text         = { Text(option.name) },
                                    onClick      = { trackSort = option; showSortMenu = false },
                                    trailingIcon = if (option == trackSort) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text    = { Text("— Albums —", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) },
                                onClick = {},
                                enabled = false
                            )
                            ContextualSortOption.Album.entries.forEach { option ->
                                DropdownMenuItem(
                                    text         = { Text(option.name) },
                                    onClick      = { albumSort = option; showSortMenu = false },
                                    trailingIcon = if (option == albumSort) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text    = { Text("— Genres —", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) },
                                onClick = {},
                                enabled = false
                            )
                            ContextualSortOption.Genre.entries.forEach { option ->
                                DropdownMenuItem(
                                    text         = { Text(option.name) },
                                    onClick      = { genreSort = option; showSortMenu = false },
                                    trailingIcon = if (option == genreSort) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            item {
                ArtistSummaryHeader(
                    coverArtPath    = artist?.coverArtPath,
                    coverArtPaths   = mosaicPaths,
                    albumCount      = ownAlbums.size,
                    trackCount      = tracks.size,
                    totalDurationMs = tracks.sumOf { it.durationMs },
                    playCount       = artist?.playCount ?: 0,
                    lastPlayed      = artist?.lastPlayed ?: 0,
                    totalPlayTimeMs = artist?.totalPlayTimeMs ?: 0,
                    genres          = genres,
                    onGetMosaicForGenre = { viewModel.getMosaicForGenre(it) },
                    onGenreClick        = { id, name -> onNavigateToCategory("genre", id.toString(), name) }
                )
            }

            if (ownAlbums.isNotEmpty()) {
                item { SectionLabel(title = "Albums", count = ownAlbums.size) }
                items(items = ownAlbums, key = { "album_${it.id}" }) { album ->
                    ArtistAlbumRow(album = album, onClick = { onNavigateToAlbum(album.id) })
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            }

            if (appearsOn.isNotEmpty()) {
                item { SectionLabel(title = "Appears On", count = appearsOn.size) }
                items(items = appearsOn, key = { "appears_${it.album.id}" }) { matchedAlbum ->
                    MatchedAlbumRow(
                        matchedAlbum = matchedAlbum,
                        onClick      = { onNavigateToAlbum(matchedAlbum.album.id) }
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            }

            if (tracks.isNotEmpty()) {
                item { SectionLabel(title = "All Tracks", count = tracks.size) }
                items(items = tracks, key = { "track_${it.id}" }) { track ->
                    TrackItemRow(
                        track           = track,
                        onFavoriteClick = { viewModel.toggleFavorite(track.id) },
                        onClick         = { selectedTrack = track }
                    )
                }
            }

            if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = "No tracks found for this artist.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    selectedTrack?.let { track ->
        TrackOptionsDialog(
            track                   = track,
            viewModel               = viewModel,
            onDismiss               = { selectedTrack = null },
            onNavigateToEditor      = onNavigateToEditor,
            onNavigateToArtist      = onNavigateToArtist,
            onNavigateToAlbumArtist = onNavigateToAlbumArtist,
            onNavigateToAlbum       = onNavigateToAlbum,
            onNavigateToCategory    = onNavigateToCategory
        )
    }
}

// === ArtistSummaryHeader ====================

@Composable
private fun ArtistSummaryHeader(
    coverArtPath:    ArtPath?,
    coverArtPaths:   List<ArtPath>,
    albumCount:      Int,
    trackCount:      Int,
    totalDurationMs: Long,
    playCount:       Int,
    lastPlayed:      Long,
    totalPlayTimeMs: Long,
    genres:          List<MatchedGenre>,
    onGetMosaicForGenre: suspend (Long) -> List<ArtPath>,
    onGenreClick:    (Long, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            AsyncImage(
                model              = coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                contentDescription = "Single cover",
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))
            )
            MosaicArt(
                paths    = coverArtPaths,
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatChip(label = "Albums",   value = albumCount.toString())
            StatChip(label = "Tracks",   value = trackCount.toString())
            StatChip(label = "Duration", value = totalDurationMs.toHumanDuration())
        }
        if (genres.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            LazyRow(
                contentPadding        = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(genres, key = { "genre_${it.genre.id}" }) { matchedGenre ->
                    MatchedGenreChip(
                        matchedGenre = matchedGenre,
                        getMosaic    = { onGetMosaicForGenre(matchedGenre.genre.id) },
                        onClick      = { onGenreClick(matchedGenre.genre.id, matchedGenre.genre.name.ifBlank { "Unknown Genre" }) }
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatChip(label = "Plays",       value = playCount.toPlayCountString() ?: "Never")
            StatChip(label = "Listened",    value = totalPlayTimeMs.toHumanDuration())
            StatChip(label = "Last Played", value = lastPlayed.toRelativeTimeString())
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// === SectionLabel ===========================

@Composable
private fun SectionLabel(title: String, count: Int) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = count.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// === ArtistAlbumRow =========================

@Composable
private fun ArtistAlbumRow(album: Album, onClick: () -> Unit) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model              = album.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
            contentDescription = "Album art",
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = album.title, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (album.year.isNotBlank()) {
                    Text(text = album.year, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "·", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text  = "${album.trackCount} tracks · ${album.totalDurationMs.toHumanDuration()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            album.playCount.toPlayCountString()?.let {
                Text(
                    text     = "$it · ${album.totalPlayTimeMs.toHumanDuration()} listened · ${album.lastPlayed.toRelativeTimeString()}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 80.dp, end = 16.dp))
}

