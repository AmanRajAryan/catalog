package aman.catalogtest.ui.albums

import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.MatchedGenre
import aman.catalog.audio.models.Track
import aman.catalog.audio.models.ContextualSortOption
import aman.catalogtest.CatalogViewModel
import aman.catalogtest.ui.tracks.TrackOptionsDialog
import aman.catalogtest.util.toHumanDuration
import aman.catalogtest.util.toPlayCountString
import aman.catalogtest.util.toRelativeTimeString
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import aman.catalogtest.ui.components.MatchedGenreChip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    viewModel: CatalogViewModel,
    albumId: Long,
    onBack: () -> Unit,
    onNavigateToArtist: (artistId: Long, isAlbumArtist: Boolean) -> Unit = { _, _ -> },
    onNavigateToAlbum: (id: Long) -> Unit = {},
    onNavigateToCategory: (type: String, id: String, title: String) -> Unit = { _, _, _ -> },
    onNavigateToEditor: (Long) -> Unit = {}
) {
    val albumFlow = remember(albumId) { viewModel.getAlbumById(albumId) }
    val album by albumFlow.collectAsState(initial = null)

    var trackSort by remember { mutableStateOf(ContextualSortOption.Track.TRACK_NUMBER_ASC) }
    var genreSort by remember { mutableStateOf(ContextualSortOption.Genre.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }

    val tracksFlow = remember(albumId, trackSort) {
        viewModel.getTracksForCategory("album", albumId, sort = trackSort)
    }
    val tracks by tracksFlow.collectAsState(initial = emptyList())

    val genresFlow = remember(albumId, genreSort) { viewModel.getGenresForAlbum(albumId, genreSort) }
    val genres by genresFlow.collectAsState(initial = emptyList())

    val artistsFlow = remember(albumId) { viewModel.getArtistsForAlbum(albumId) }
    val artists by artistsFlow.collectAsState(initial = emptyList())

    // Group by disc only when sorted by track number — other sorts flatten the list
    val grouped = remember(tracks, trackSort) {
        if (trackSort == ContextualSortOption.Track.TRACK_NUMBER_ASC)
            tracks.groupBy { it.discNumber.takeIf { n -> n > 0 } ?: 1 }
        else
            mapOf(0 to tracks)
    }

    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(album?.title ?: "Album", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.List, contentDescription = "Sort tracks")
                        }
                        DropdownMenu(
                            expanded         = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
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
                AlbumHeader(
                    coverArtPath    = album?.coverArtPath,
                    title           = album?.title ?: "",
                    albumArtist     = album?.albumArtist ?: "",
                    albumArtistCoverArtPath = album?.albumArtistCoverArtPath,
                    year            = album?.year ?: "",
                    trackCount      = album?.trackCount ?: tracks.size,
                    totalDurationMs = album?.totalDurationMs ?: tracks.sumOf { it.durationMs },
                    playCount       = album?.playCount ?: 0,
                    lastPlayed      = album?.lastPlayed ?: 0,
                    totalPlayTimeMs = album?.totalPlayTimeMs ?: 0,
                    genres          = genres,
                    onGetMosaicForGenre = { viewModel.getMosaicForGenre(it) },
                    onGenreClick        = { id, name -> onNavigateToCategory("genre", id.toString(), name) },
                    onAlbumArtistClick  = album?.albumArtistId?.let {
                        { onNavigateToArtist(it, true) }
                    }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // grouped by disc when sorted by track number, flat otherwise
            grouped.keys.sorted().forEach { discNumber ->
                val discTracks = grouped[discNumber] ?: return@forEach

                if (trackSort == ContextualSortOption.Track.TRACK_NUMBER_ASC) {
                    item(key = "disc_$discNumber") {
                        Text(
                            text     = "Disc $discNumber",
                            style    = MaterialTheme.typography.labelMedium,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                items(items = discTracks, key = { it.id }) { track ->
                    AlbumTrackRow(
                        track           = track,
                        onFavoriteClick = { viewModel.toggleFavorite(track.id) },
                        onClick         = { selectedTrack = track }
                    )
                }
            }

            if (artists.isNotEmpty()) {
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item {
                    Text(
                        text     = "Artists",
                        style    = MaterialTheme.typography.titleSmall,
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(items = artists, key = { "artist_${it.id}" }) { artist ->
                    ParticipatingArtistRow(
                        artist  = artist,
                        onClick = { onNavigateToArtist(artist.id, false) }
                    )
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
            onNavigateToArtist      = { id -> onNavigateToArtist(id, false) },
            onNavigateToAlbumArtist = { id -> onNavigateToArtist(id, true) },
            onNavigateToAlbum       = onNavigateToAlbum,
            onNavigateToCategory    = onNavigateToCategory
        )
    }
}

// === AlbumHeader ==============================

@Composable
private fun AlbumHeader(
    coverArtPath: ArtPath?,
    title: String,
    albumArtist: String,
    albumArtistCoverArtPath: ArtPath?,
    year: String,
    trackCount: Int,
    totalDurationMs: Long,
    playCount: Int,
    lastPlayed: Long,
    totalPlayTimeMs: Long,
    genres: List<MatchedGenre>,
    onGetMosaicForGenre: suspend (Long) -> List<ArtPath>,
    onGenreClick: (Long, String) -> Unit,
    onAlbumArtistClick: (() -> Unit)?
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment     = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AsyncImage(
            model              = coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
            contentDescription = "Album art",
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text     = title,
                style    = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (albumArtist.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = if (onAlbumArtistClick != null)
                        Modifier.clickable { onAlbumArtistClick() }
                    else Modifier
                ) {
                    AsyncImage(
                        model              = albumArtistCoverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        text     = albumArtist,
                        style    = MaterialTheme.typography.titleMedium,
                        color    = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (year.isNotBlank()) {
                Text(
                    text  = year,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text  = "$trackCount tracks · ${totalDurationMs.toHumanDuration()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (genres.isNotEmpty()) {
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text  = playCount.toPlayCountString() ?: "Never played",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text  = "${totalPlayTimeMs.toHumanDuration()} listened",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text  = "Last: ${lastPlayed.toRelativeTimeString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// === ParticipatingArtistRow ===================

@Composable
private fun ParticipatingArtistRow(
    artist: aman.catalog.audio.models.Artist,
    onClick: () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model              = artist.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = artist.name,
                style    = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text  = "${artist.trackCount} tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = 16.dp))
}

// === AlbumTrackRow ================================

@Composable
private fun AlbumTrackRow(
    track: Track,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model              = "${track.path}?t=${track.dateModified}",
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(4.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Track number — fixed width so titles align neatly regardless of digit count
        Text(
            text     = if (track.trackNumber > 0) track.trackNumber.toString() else "·",
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = track.title,
                style    = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text     = track.artist,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text     = track.artists.joinToString(" · ") { it.name },
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text     = track.durationMs.toHumanDuration(),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        IconButton(onClick = onFavoriteClick) {
            Icon(
                imageVector        = if (track.dateFavorited > 0) Icons.Filled.Favorite
                                    else Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint               = if (track.dateFavorited > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(20.dp)
            )
        }
    }

    HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = 16.dp))
}
