package aman.catalogtest.ui.search

import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.SearchResult
import aman.catalog.audio.models.Track
import aman.catalogtest.CatalogViewModel
import aman.catalogtest.ui.tracks.TrackOptionsDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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

@Composable
fun SearchResultsScreen(
    result: SearchResult,
    viewModel: CatalogViewModel,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (Long) -> Unit,
    onNavigateToAlbumArtist: (Long) -> Unit,
    onNavigateToCategory: (type: String, id: String, title: String) -> Unit,
    onNavigateToPlaylist: (Long) -> Unit
) {
    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    if (result.isEmpty) {
        Column(
            modifier              = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text(
                text  = "No results for \"${result.query}\"",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        if (result.tracks.isNotEmpty()) {
            item { SearchSectionHeader("Tracks", result.tracks.size) }
            items(result.tracks, key = { "t_${it.id}" }) { track ->
                SearchTrackRow(track = track, onClick = { selectedTrack = track })
            }
        }

        if (result.albums.isNotEmpty()) {
            item { SearchSectionHeader("Albums", result.albums.size) }
            items(result.albums, key = { "al_${it.id}" }) { album ->
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToAlbum(album.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(
                        model              = album.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Column {
                        Text(album.title, style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(album.albumArtist, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = 16.dp))
            }
        }

        if (result.artists.isNotEmpty()) {
            item { SearchSectionHeader("Artists", result.artists.size) }
            items(result.artists, key = { "ar_${it.id}" }) { artist ->
                val mosaicPaths by produceState(emptyList<ArtPath>(), artist.id) {
                    value = viewModel.getMosaicForTrackArtist(artist.id)
                }
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToArtist(artist.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(model = artist.coverArtPath?.let { "${it.path}?t=${it.dateModified}" }, contentDescription = "Single cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    MosaicArt(paths = mosaicPaths,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text("${artist.trackCount} tracks", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 116.dp, end = 16.dp))
            }
        }

        if (result.albumArtists.isNotEmpty()) {
            item { SearchSectionHeader("Album Artists", result.albumArtists.size) }
            items(result.albumArtists, key = { "aa_${it.id}" }) { artist ->
                val mosaicPaths by produceState(emptyList<ArtPath>(), artist.id) {
                    value = viewModel.getMosaicForAlbumArtist(artist.id)
                }
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToAlbumArtist(artist.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(model = artist.coverArtPath?.let { "${it.path}?t=${it.dateModified}" }, contentDescription = "Single cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    MosaicArt(paths = mosaicPaths,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(
                            buildString {
                                append("${artist.trackCount} tracks")
                                if (artist.albumCount > 0) append(" · ${artist.albumCount} albums")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 116.dp, end = 16.dp))
            }
        }

        if (result.genres.isNotEmpty()) {
            item { SearchSectionHeader("Genres", result.genres.size) }
            items(result.genres, key = { "g_${it.id}" }) { genre ->
                val mosaicPaths by produceState(emptyList<ArtPath>(), genre.id) {
                    value = viewModel.getMosaicForGenre(genre.id)
                }
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCategory("genre", genre.id.toString(), genre.name) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(model = genre.coverArtPath?.let { "${it.path}?t=${it.dateModified}" }, contentDescription = "Single cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    MosaicArt(paths = mosaicPaths,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(genre.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text("${genre.trackCount} tracks", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 116.dp, end = 16.dp))
            }
        }

        if (result.playlists.isNotEmpty()) {
            item { SearchSectionHeader("Playlists", result.playlists.size) }
            items(result.playlists, key = { "pl_${it.id}" }) { playlist ->
                val mosaicPaths by produceState(emptyList<ArtPath>(), playlist.id) {
                    value = viewModel.getMosaicForPlaylist(playlist.id)
                }
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPlaylist(playlist.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(model = playlist.coverArtPath?.let { "${it.path}?t=${it.dateModified}" }, contentDescription = "Single cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    MosaicArt(paths = mosaicPaths,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text("${playlist.trackCount} tracks", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 116.dp, end = 16.dp))
            }
        }

        if (result.composers.isNotEmpty()) {
            item { SearchSectionHeader("Composers", result.composers.size) }
            items(result.composers, key = { "c_${it.id}" }) { composer ->
                val mosaicPaths by produceState(emptyList<ArtPath>(), composer.id) {
                    value = viewModel.getMosaicForComposer(composer.id)
                }
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCategory("composer", composer.id.toString(), composer.name) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(model = composer.coverArtPath?.let { "${it.path}?t=${it.dateModified}" }, contentDescription = "Single cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    MosaicArt(paths = mosaicPaths,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(composer.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text("${composer.trackCount} tracks", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 116.dp, end = 16.dp))
            }
        }

        if (result.lyricists.isNotEmpty()) {
            item { SearchSectionHeader("Lyricists", result.lyricists.size) }
            items(result.lyricists, key = { "ly_${it.id}" }) { lyricist ->
                val mosaicPaths by produceState(emptyList<ArtPath>(), lyricist.id) {
                    value = viewModel.getMosaicForLyricist(lyricist.id)
                }
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCategory("lyricist", lyricist.id.toString(), lyricist.name) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(model = lyricist.coverArtPath?.let { "${it.path}?t=${it.dateModified}" }, contentDescription = "Single cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    MosaicArt(paths = mosaicPaths,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lyricist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text("${lyricist.trackCount} tracks", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 116.dp, end = 16.dp))
            }
        }

        if (result.folders.isNotEmpty()) {
            item { SearchSectionHeader("Folders", result.folders.size) }
            items(result.folders, key = { "f_${it.path}" }) { folder ->
                val mosaicPaths by produceState(emptyList<ArtPath>(), folder.path) {
                    value = viewModel.getMosaicForFolder(folder.path)
                }
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCategory("folder", folder.path, folder.name) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(model = folder.coverArtPath?.let { "${it.path}?t=${it.dateModified}" }, contentDescription = "Single cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    MosaicArt(paths = mosaicPaths,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(folder.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(folder.path, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 116.dp, end = 16.dp))
            }
        }

        if (result.years.isNotEmpty()) {
            item { SearchSectionHeader("Years", result.years.size) }
            items(result.years, key = { "y_${it.year}" }) { year ->
                val mosaicPaths by produceState(emptyList<ArtPath>(), year.year) {
                    value = viewModel.getMosaicForYear(year.year)
                }
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCategory("year", year.year.toString(), year.year.toString()) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(model = year.coverArtPath?.let { "${it.path}?t=${it.dateModified}" }, contentDescription = "Single cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    MosaicArt(paths = mosaicPaths,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(year.year.toString(), style = MaterialTheme.typography.bodyLarge)
                        Text("${year.trackCount} tracks", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 116.dp, end = 16.dp))
            }
        }
    }

    selectedTrack?.let { track ->
        TrackOptionsDialog(
            track     = track,
            viewModel = viewModel,
            onDismiss = { selectedTrack = null }
        )
    }
}




@Composable
private fun SearchTrackRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model              = "${track.path}?t=${track.dateModified}",
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.artist} · ${track.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = 16.dp))
}


@Composable
private fun SearchSectionHeader(title: String, count: Int) {
    Text(
        text     = "$title ($count)",
        style    = MaterialTheme.typography.titleSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}
