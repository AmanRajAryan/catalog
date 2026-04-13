package aman.catalogtest.ui.home

import aman.catalog.audio.models.FavoritesInfo
import aman.catalogtest.CatalogViewModel
import aman.catalogtest.ui.tracks.TrackOptionsDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private fun formatDuration(totalMs: Long): String {
    val totalSeconds = totalMs / 1000
    val hours        = totalSeconds / 3600
    val minutes      = (totalSeconds % 3600) / 60
    return when {
        hours > 0   -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else        -> "<1m"
    }
}

@Composable
fun HomeScreen(
    viewModel: CatalogViewModel,
    onNavigateToFavorites: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (Long) -> Unit,
    onNavigateToAlbumArtist: (Long) -> Unit,
    onNavigateToCategory: (type: String, id: String, title: String) -> Unit,
    onNavigateToEditor: (Long) -> Unit = {}
) {
    val recentlyAdded  by viewModel.recentlyAdded.collectAsState()
    val recentlyAddedAlbums by viewModel.recentlyAddedAlbums.collectAsState()
    val mostPlayed     by viewModel.mostPlayed.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val favoritesInfo  by viewModel.favoritesInfo.collectAsState()

    val recentlyPlayedAlbums       by viewModel.recentlyPlayedAlbums.collectAsState()
    val recentlyPlayedArtists      by viewModel.recentlyPlayedArtists.collectAsState()
    val recentlyPlayedAlbumArtists by viewModel.recentlyPlayedAlbumArtists.collectAsState()
    val recentlyPlayedGenres       by viewModel.recentlyPlayedGenres.collectAsState()
    val recentlyPlayedComposers    by viewModel.recentlyPlayedComposers.collectAsState()
    val recentlyPlayedLyricists    by viewModel.recentlyPlayedLyricists.collectAsState()
    val recentlyPlayedYears        by viewModel.recentlyPlayedYears.collectAsState()
    val recentlyPlayedFolders      by viewModel.recentlyPlayedFolders.collectAsState()

    val mostPlayedAlbums       by viewModel.mostPlayedAlbums.collectAsState()
    val mostPlayedArtists      by viewModel.mostPlayedArtists.collectAsState()
    val mostPlayedAlbumArtists by viewModel.mostPlayedAlbumArtists.collectAsState()
    val mostPlayedGenres       by viewModel.mostPlayedGenres.collectAsState()
    val mostPlayedComposers    by viewModel.mostPlayedComposers.collectAsState()
    val mostPlayedLyricists    by viewModel.mostPlayedLyricists.collectAsState()
    val mostPlayedYears        by viewModel.mostPlayedYears.collectAsState()
    val mostPlayedFolders      by viewModel.mostPlayedFolders.collectAsState()

    val hasAnything = recentlyAdded.isNotEmpty() || recentlyAddedAlbums.isNotEmpty() ||
        mostPlayed.isNotEmpty() ||
        recentlyPlayed.isNotEmpty() || recentlyPlayedAlbums.isNotEmpty() ||
        mostPlayedAlbums.isNotEmpty() || recentlyPlayedArtists.isNotEmpty() ||
        mostPlayedArtists.isNotEmpty()

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {

        item {
            Button(
                onClick  = onNavigateToFavorites,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector        = Icons.Filled.Favorite,
                    contentDescription = null,
                    modifier           = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = when {
                        favoritesInfo.trackCount == 0 -> "Favourites"
                        favoritesInfo.totalDurationMs > 0L -> "Favourites · ${favoritesInfo.trackCount} tracks · ${formatDuration(favoritesInfo.totalDurationMs)}"
                        else -> "Favourites · ${favoritesInfo.trackCount} tracks"
                    }
                )
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            item { SectionHeader("Recently Added", "${recentlyAdded.size} tracks") }
            item { TrackCardRow(tracks = recentlyAdded, viewModel = viewModel, onNavigateToEditor = onNavigateToEditor, onNavigateToArtist = onNavigateToArtist, onNavigateToAlbumArtist = onNavigateToAlbumArtist, onNavigateToAlbum = onNavigateToAlbum, onNavigateToCategory = onNavigateToCategory) }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (recentlyAddedAlbums.isNotEmpty()) {
            item { SectionHeader("Recently Added Albums", "${recentlyAddedAlbums.size} albums") }
            item { AlbumCardRow(albums = recentlyAddedAlbums, onItemClick = onNavigateToAlbum) }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (mostPlayed.isNotEmpty()) {
            item { SectionHeader("Most Played Tracks", "${mostPlayed.size} tracks") }
            item { TrackCardRow(tracks = mostPlayed, viewModel = viewModel, onNavigateToEditor = onNavigateToEditor, onNavigateToArtist = onNavigateToArtist, onNavigateToAlbumArtist = onNavigateToAlbumArtist, onNavigateToAlbum = onNavigateToAlbum, onNavigateToCategory = onNavigateToCategory) }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (recentlyPlayed.isNotEmpty()) {
            item { SectionHeader("Recently Played Tracks", "${recentlyPlayed.size} tracks") }
            item { TrackCardRow(tracks = recentlyPlayed, viewModel = viewModel, onNavigateToEditor = onNavigateToEditor, onNavigateToArtist = onNavigateToArtist, onNavigateToAlbumArtist = onNavigateToAlbumArtist, onNavigateToAlbum = onNavigateToAlbum, onNavigateToCategory = onNavigateToCategory) }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // recently played — by category

        if (recentlyPlayedAlbums.isNotEmpty()) {
            item { SectionHeader("Recently Played Albums", "${recentlyPlayedAlbums.size} albums") }
            item { AlbumCardRow(albums = recentlyPlayedAlbums, onItemClick = onNavigateToAlbum) }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (recentlyPlayedArtists.isNotEmpty()) {
            item { SectionHeader("Recently Played Artists", "${recentlyPlayedArtists.size} artists") }
            item { ArtistCardRow(artists = recentlyPlayedArtists, viewModel = viewModel, onItemClick = onNavigateToArtist) }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (recentlyPlayedAlbumArtists.isNotEmpty()) {
            item { SectionHeader("Recently Played Album Artists", "${recentlyPlayedAlbumArtists.size} artists") }
            item { ArtistCardRow(artists = recentlyPlayedAlbumArtists, viewModel = viewModel, onItemClick = onNavigateToAlbumArtist) }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (recentlyPlayedGenres.isNotEmpty()) {
            item { SectionHeader("Recently Played Genres", "${recentlyPlayedGenres.size} genres") }
            item {
                GenreCardRow(genres = recentlyPlayedGenres, viewModel = viewModel,
                    onItemClick = { onNavigateToCategory("genre", it.id.toString(), it.name) })
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (recentlyPlayedComposers.isNotEmpty()) {
            item { SectionHeader("Recently Played Composers", "${recentlyPlayedComposers.size} composers") }
            item {
                ComposerCardRow(composers = recentlyPlayedComposers, viewModel = viewModel,
                    onItemClick = { onNavigateToCategory("composer", it.id.toString(), it.name) })
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (recentlyPlayedLyricists.isNotEmpty()) {
            item { SectionHeader("Recently Played Lyricists", "${recentlyPlayedLyricists.size} lyricists") }
            item {
                LyricistCardRow(lyricists = recentlyPlayedLyricists, viewModel = viewModel,
                    onItemClick = { onNavigateToCategory("lyricist", it.id.toString(), it.name) })
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (recentlyPlayedYears.isNotEmpty()) {
            item { SectionHeader("Recently Played Years", "${recentlyPlayedYears.size} years") }
            item {
                YearCardRow(years = recentlyPlayedYears, viewModel = viewModel,
                    onItemClick = { onNavigateToCategory("year", it.year.toString(), it.year.toString()) })
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (recentlyPlayedFolders.isNotEmpty()) {
            item { SectionHeader("Recently Played Folders", "${recentlyPlayedFolders.size} folders") }
            item {
                FolderCardRow(folders = recentlyPlayedFolders, viewModel = viewModel,
                    onItemClick = { onNavigateToCategory("folder", it.path, it.name) })
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // most played — by category

        if (mostPlayedAlbums.isNotEmpty()) {
            item { SectionHeader("Most Played Albums", "${mostPlayedAlbums.size} albums") }
            item { AlbumCardRow(albums = mostPlayedAlbums, onItemClick = onNavigateToAlbum) }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (mostPlayedArtists.isNotEmpty()) {
            item { SectionHeader("Most Played Artists", "${mostPlayedArtists.size} artists") }
            item { ArtistCardRow(artists = mostPlayedArtists, viewModel = viewModel, onItemClick = onNavigateToArtist) }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (mostPlayedAlbumArtists.isNotEmpty()) {
            item { SectionHeader("Most Played Album Artists", "${mostPlayedAlbumArtists.size} artists") }
            item { ArtistCardRow(artists = mostPlayedAlbumArtists, viewModel = viewModel, onItemClick = onNavigateToAlbumArtist) }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (mostPlayedGenres.isNotEmpty()) {
            item { SectionHeader("Most Played Genres", "${mostPlayedGenres.size} genres") }
            item {
                GenreCardRow(genres = mostPlayedGenres, viewModel = viewModel,
                    onItemClick = { onNavigateToCategory("genre", it.id.toString(), it.name) })
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (mostPlayedComposers.isNotEmpty()) {
            item { SectionHeader("Most Played Composers", "${mostPlayedComposers.size} composers") }
            item {
                ComposerCardRow(composers = mostPlayedComposers, viewModel = viewModel,
                    onItemClick = { onNavigateToCategory("composer", it.id.toString(), it.name) })
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (mostPlayedLyricists.isNotEmpty()) {
            item { SectionHeader("Most Played Lyricists", "${mostPlayedLyricists.size} lyricists") }
            item {
                LyricistCardRow(lyricists = mostPlayedLyricists, viewModel = viewModel,
                    onItemClick = { onNavigateToCategory("lyricist", it.id.toString(), it.name) })
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (mostPlayedYears.isNotEmpty()) {
            item { SectionHeader("Most Played Years", "${mostPlayedYears.size} years") }
            item {
                YearCardRow(years = mostPlayedYears, viewModel = viewModel,
                    onItemClick = { onNavigateToCategory("year", it.year.toString(), it.year.toString()) })
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (mostPlayedFolders.isNotEmpty()) {
            item { SectionHeader("Most Played Folders", "${mostPlayedFolders.size} folders") }
            item {
                FolderCardRow(folders = mostPlayedFolders, viewModel = viewModel,
                    onItemClick = { onNavigateToCategory("folder", it.path, it.name) })
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (!hasAnything) {
            item {
                Box(
                    modifier          = Modifier.fillMaxWidth().padding(top = 80.dp),
                    contentAlignment  = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Your library is empty.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Open the drawer and tap Scan to index your music.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
