package aman.catalogtest.ui.categories

import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.Composer
import aman.catalog.audio.models.Folder
import aman.catalog.audio.models.Genre
import aman.catalog.audio.models.Lyricist
import aman.catalog.audio.models.Year
import aman.catalogtest.CatalogViewModel
import aman.catalogtest.util.toHumanDuration
import aman.catalogtest.util.toPlayCountString
import aman.catalogtest.util.toRelativeTimeString
import androidx.compose.runtime.produceState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.runtime.getValue
import aman.catalogtest.ui.components.MosaicArt

// === Genre ==============================

@Composable
fun GenreListScreen(
    genres: List<Genre>,
    viewModel: CatalogViewModel,
    onItemClick: (id: Long, name: String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = genres, key = { it.id }) { genre ->
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(genre.id, genre.name) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val mosaicPaths by produceState(emptyList<ArtPath>(), genre.id) {
                    value = viewModel.getMosaicForGenre(genre.id)
                }
                AsyncImage(
                    model              = genre.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                    contentDescription = "Single cover",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp))
                )
                MosaicArt(
                    paths    = mosaicPaths,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = genre.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text  = "${genre.trackCount} tracks · ${genre.albumCount} albums · ${genre.totalDurationMs.toHumanDuration()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    genre.playCount.toPlayCountString()?.let {
                        Text(
                            text  = "$it · ${genre.totalPlayTimeMs.toHumanDuration()} listened · ${genre.lastPlayed.toRelativeTimeString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(start = 80.dp, end = 16.dp))
        }
    }
}

// === Composer ==========================

@Composable
fun ComposerListScreen(
    composers: List<Composer>,
    viewModel: CatalogViewModel,
    onItemClick: (id: Long, name: String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = composers, key = { it.id }) { composer ->
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(composer.id, composer.name) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val mosaicPaths by produceState(emptyList<ArtPath>(), composer.id) {
                    value = viewModel.getMosaicForComposer(composer.id)
                }
                AsyncImage(
                    model              = composer.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                    contentDescription = "Single cover",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp))
                )
                MosaicArt(
                    paths    = mosaicPaths,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = composer.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text  = "${composer.trackCount} tracks · ${composer.albumCount} albums · ${composer.totalDurationMs.toHumanDuration()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    composer.playCount.toPlayCountString()?.let {
                        Text(
                            text  = "$it · ${composer.totalPlayTimeMs.toHumanDuration()} listened · ${composer.lastPlayed.toRelativeTimeString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(start = 80.dp, end = 16.dp))
        }
    }
}

// === Lyricist ================================

@Composable
fun LyricistListScreen(
    lyricists: List<Lyricist>,
    viewModel: CatalogViewModel,
    onItemClick: (id: Long, name: String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = lyricists, key = { it.id }) { lyricist ->
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(lyricist.id, lyricist.name) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val mosaicPaths by produceState(emptyList<ArtPath>(), lyricist.id) {
                    value = viewModel.getMosaicForLyricist(lyricist.id)
                }
                AsyncImage(
                    model              = lyricist.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                    contentDescription = "Single cover",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp))
                )
                MosaicArt(
                    paths    = mosaicPaths,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = lyricist.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text  = "${lyricist.trackCount} tracks · ${lyricist.albumCount} albums · ${lyricist.totalDurationMs.toHumanDuration()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    lyricist.playCount.toPlayCountString()?.let {
                        Text(
                            text  = "$it · ${lyricist.totalPlayTimeMs.toHumanDuration()} listened · ${lyricist.lastPlayed.toRelativeTimeString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(start = 80.dp, end = 16.dp))
        }
    }
}

// === Folder ====================

@Composable
fun FolderListScreen(
    folders: List<Folder>,
    viewModel: CatalogViewModel,
    onItemClick: (path: String, name: String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = folders, key = { it.path }) { folder ->
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(folder.path, folder.name) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val mosaicPaths by produceState(emptyList<ArtPath>(), folder.path) {
                    value = viewModel.getMosaicForFolder(folder.path)
                }
                AsyncImage(
                    model              = folder.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                    contentDescription = "Single cover",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp))
                )
                MosaicArt(
                    paths    = mosaicPaths,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = folder.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = folder.path, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text  = "${folder.trackCount} tracks · ${folder.albumCount} albums · ${folder.totalDurationMs.toHumanDuration()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    folder.playCount.toPlayCountString()?.let {
                        Text(
                            text  = "$it · ${folder.totalPlayTimeMs.toHumanDuration()} listened · ${folder.lastPlayed.toRelativeTimeString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(start = 80.dp, end = 16.dp))
        }
    }
}

// === Year ===========================

@Composable
fun YearListScreen(
    years: List<Year>,
    viewModel: CatalogViewModel,
    onItemClick: (year: Year) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = years, key = { it.year }) { year ->
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(year) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val mosaicPaths by produceState(emptyList<ArtPath>(), year.year) {
                    value = viewModel.getMosaicForYear(year.year)
                }
                AsyncImage(
                    model              = year.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                    contentDescription = "Single cover",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp))
                )
                MosaicArt(
                    paths    = mosaicPaths,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = if (year.year > 0) year.year.toString() else "Unknown Year",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text  = "${year.trackCount} tracks · ${year.albumCount} albums · ${year.totalDurationMs.toHumanDuration()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    year.playCount.toPlayCountString()?.let {
                        Text(
                            text  = "$it · ${year.totalPlayTimeMs.toHumanDuration()} listened · ${year.lastPlayed.toRelativeTimeString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(start = 80.dp, end = 16.dp))
        }
    }
}
