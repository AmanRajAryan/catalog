package aman.catalogtest.ui.home

import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.Album
import aman.catalog.audio.models.Artist
import aman.catalog.audio.models.Composer
import aman.catalog.audio.models.Folder
import aman.catalog.audio.models.Genre
import aman.catalog.audio.models.Lyricist
import aman.catalog.audio.models.Track
import aman.catalog.audio.models.Year
import aman.catalogtest.CatalogViewModel
import aman.catalogtest.ui.tracks.TrackOptionsDialog
import aman.catalogtest.util.toHumanDuration
import aman.catalogtest.util.toPlayCountString
import aman.catalogtest.util.toRelativeTimeString
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import aman.catalogtest.ui.components.MosaicArt

// === Section header =====================

@Composable
internal fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title,    style = MaterialTheme.typography.titleMedium)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// === Shared stats block =================================

@Composable
internal fun StatsBlock(playCount: Int, lastPlayed: Long, totalPlayTimeMs: Long) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
    playCount.toPlayCountString()?.let {
        StatLine(label = "Plays", value = it)
    }
    if (totalPlayTimeMs > 0) {
        StatLine(label = "Total time", value = totalPlayTimeMs.toHumanDuration())
    }
    if (lastPlayed > 0) {
        StatLine(label = "Last played", value = lastPlayed.toRelativeTimeString())
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

// === Content rating badge  E=Explicit  C=Clean ==============

@Composable
internal fun ContentRatingBadge(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.padding(top = 4.dp)) {
        androidx.compose.material3.Surface(
            shape          = RoundedCornerShape(4.dp),
            color          = color.copy(alpha = 0.15f),
            tonalElevation = 0.dp
        ) {
            Text(
                text     = label,
                style    = MaterialTheme.typography.labelSmall,
                color    = color,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// === Track card row ==========================

@Composable
internal fun TrackCardRow(
    tracks: List<Track>,
    viewModel: CatalogViewModel,
    onNavigateToEditor: (Long) -> Unit = {},
    onNavigateToArtist: (id: Long) -> Unit = {},
    onNavigateToAlbumArtist: (id: Long) -> Unit = {},
    onNavigateToAlbum: (id: Long) -> Unit = {},
    onNavigateToCategory: (type: String, id: String, title: String) -> Unit = { _, _, _ -> }
) {
    var selectedTrack by remember { mutableStateOf<Track?>(null) }

    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = tracks, key = { it.id }) { track ->
            TrackCard(
                track           = track,
                onFavoriteClick = { viewModel.toggleFavorite(track.id) },
                onClick         = { selectedTrack = track }
            )
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

@Composable
private fun TrackCard(track: Track, onFavoriteClick: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.width(190.dp).clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            AsyncImage(
                model              = "${track.path}?t=${track.dateModified}",
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxWidth().height(120.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            )
            Column(modifier = Modifier.padding(10.dp)) {

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    Icon(
                        imageVector        = if (track.dateFavorited > 0) Icons.Filled.Favorite
                                            else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint               = if (track.dateFavorited > 0) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.wrapContentWidth()
                            .clickable { onFavoriteClick() }.padding(start = 4.dp)
                    )
                }

                Text(track.artist, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.album, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)

                Spacer(Modifier.height(4.dp))

                val metaLine1 = buildList {
                    val formatLabel = buildString {
                        if (track.metadata.bitsPerSample > 0) append("${track.metadata.bitsPerSample}-bit ")
                        append(track.metadata.codec.ifBlank { track.mimeType })
                    }
                    if (formatLabel.isNotBlank()) add(formatLabel)
                    if (track.metadata.bitrate > 0) add("${track.metadata.bitrate}kbps")
                }
                val metaLine2 = buildList {
                    if (track.metadata.sampleRate > 0) add("${track.metadata.sampleRate}Hz")
                    if (track.metadata.channels > 0) add(
                        when (track.metadata.channels) {
                            1    -> "Mono"
                            2    -> "Stereo"
                            else -> "${track.metadata.channels}ch"
                        }
                    )
                }
                if (metaLine1.isNotEmpty()) {
                    Text(metaLine1.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (metaLine2.isNotEmpty()) {
                    Text(metaLine2.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                val dateParts = buildList {
                    if (track.year > 0) add(track.year.toString())
                    if (track.releaseDate.isNotBlank() && track.releaseDate != track.year.toString())
                        add(track.releaseDate)
                }
                val physicalParts = buildList {
                    val tn = buildString {
                        if (track.discNumber > 0) append("D${track.discNumber}")
                        if (track.trackNumber > 0) {
                            if (isNotEmpty()) append("-")
                            append("T${track.trackNumber}")
                        }
                    }
                    if (tn.isNotEmpty()) add(tn)
                    add(track.durationMs.toHumanDuration())
                    add("%.1f MB".format(track.sizeBytes / 1_048_576.0))
                }
                if (dateParts.isNotEmpty()) {
                    Text(dateParts.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(physicalParts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)

                when (track.metadata.contentRating) {
                    1 -> ContentRatingBadge("E", MaterialTheme.colorScheme.error)
                    2 -> ContentRatingBadge("C", MaterialTheme.colorScheme.tertiary)
                }

                StatsBlock(track.playCount, track.lastPlayed, track.totalPlayTimeMs)
            }
        }
    }
}

// === Album card row ==============================

@Composable
internal fun AlbumCardRow(albums: List<Album>, onItemClick: (Long) -> Unit) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = albums, key = { it.id }) { album ->
            Card(
                modifier  = Modifier.width(170.dp).clickable { onItemClick(album.id) },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    AsyncImage(
                        model              = album.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                        contentDescription = "Album art",
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxWidth().height(140.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    )
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(album.title, style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(album.albumArtist.ifBlank { "Unknown Artist" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${album.trackCount} tracks · ${album.totalDurationMs.toHumanDuration()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (album.year.isNotBlank()) {
                            Text(album.year, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatsBlock(album.playCount, album.lastPlayed, album.totalPlayTimeMs)
                    }
                }
            }
        }
    }
}

// === Artist card row ================

@Composable
internal fun ArtistCardRow(artists: List<Artist>, viewModel: CatalogViewModel, onItemClick: (Long) -> Unit) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = artists, key = { it.id }) { artist ->
            val mosaicPaths by produceState(emptyList<ArtPath>(), artist.id) {
                value = viewModel.getMosaicForTrackArtist(artist.id)
            }
            Card(
                modifier  = Modifier.width(170.dp).clickable { onItemClick(artist.id) },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().height(140.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))) {
                        AsyncImage(model = artist.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                            contentDescription = "Single cover",
                            contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        MosaicArt(paths = mosaicPaths,
                            modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(artist.name, style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${artist.trackCount} tracks in library",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (artist.albumCount > 0) {
                            Text("${artist.albumCount} albums",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(artist.totalDurationMs.toHumanDuration(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatsBlock(artist.playCount, artist.lastPlayed, artist.totalPlayTimeMs)
                    }
                }
            }
        }
    }
}

// === Genre card row =============================

@Composable
internal fun GenreCardRow(genres: List<Genre>, viewModel: CatalogViewModel, onItemClick: (Genre) -> Unit) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = genres, key = { it.id }) { genre ->
            val mosaicPaths by produceState(emptyList<ArtPath>(), genre.id) {
                value = viewModel.getMosaicForGenre(genre.id)
            }
            Card(
                modifier  = Modifier.width(170.dp).clickable { onItemClick(genre) },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().height(140.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))) {
                        AsyncImage(model = genre.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                            contentDescription = "Single cover",
                            contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        MosaicArt(paths = mosaicPaths,
                            modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(genre.name, style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${genre.trackCount} tracks · ${genre.albumCount} albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(genre.totalDurationMs.toHumanDuration(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatsBlock(genre.playCount, genre.lastPlayed, genre.totalPlayTimeMs)
                    }
                }
            }
        }
    }
}

// === Composer card row ====================

@Composable
internal fun ComposerCardRow(composers: List<Composer>, viewModel: CatalogViewModel, onItemClick: (Composer) -> Unit) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = composers, key = { it.id }) { composer ->
            val mosaicPaths by produceState(emptyList<ArtPath>(), composer.id) {
                value = viewModel.getMosaicForComposer(composer.id)
            }
            Card(
                modifier  = Modifier.width(170.dp).clickable { onItemClick(composer) },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().height(140.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))) {
                        AsyncImage(model = composer.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                            contentDescription = "Single cover",
                            contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        MosaicArt(paths = mosaicPaths,
                            modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(composer.name, style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${composer.trackCount} tracks · ${composer.albumCount} albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(composer.totalDurationMs.toHumanDuration(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatsBlock(composer.playCount, composer.lastPlayed, composer.totalPlayTimeMs)
                    }
                }
            }
        }
    }
}

// === Lyricist card row ================================

@Composable
internal fun LyricistCardRow(lyricists: List<Lyricist>, viewModel: CatalogViewModel, onItemClick: (Lyricist) -> Unit) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = lyricists, key = { it.id }) { lyricist ->
            val mosaicPaths by produceState(emptyList<ArtPath>(), lyricist.id) {
                value = viewModel.getMosaicForLyricist(lyricist.id)
            }
            Card(
                modifier  = Modifier.width(170.dp).clickable { onItemClick(lyricist) },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().height(140.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))) {
                        AsyncImage(model = lyricist.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                            contentDescription = "Single cover",
                            contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        MosaicArt(paths = mosaicPaths,
                            modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(lyricist.name, style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${lyricist.trackCount} tracks · ${lyricist.albumCount} albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(lyricist.totalDurationMs.toHumanDuration(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatsBlock(lyricist.playCount, lyricist.lastPlayed, lyricist.totalPlayTimeMs)
                    }
                }
            }
        }
    }
}

// === Year card row ====================

@Composable
internal fun YearCardRow(years: List<Year>, viewModel: CatalogViewModel, onItemClick: (Year) -> Unit) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = years, key = { it.year }) { year ->
            val mosaicPaths by produceState(emptyList<ArtPath>(), year.year) {
                value = viewModel.getMosaicForYear(year.year)
            }
            Card(
                modifier  = Modifier.width(170.dp).clickable { onItemClick(year) },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().height(140.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))) {
                        AsyncImage(model = year.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                            contentDescription = "Single cover",
                            contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        MosaicArt(paths = mosaicPaths,
                            modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(year.year.toString(), style = MaterialTheme.typography.titleSmall)
                        Text("${year.trackCount} tracks · ${year.albumCount} albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(year.totalDurationMs.toHumanDuration(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatsBlock(year.playCount, year.lastPlayed, year.totalPlayTimeMs)
                    }
                }
            }
        }
    }
}

// === Folder card row ==================================

@Composable
internal fun FolderCardRow(folders: List<Folder>, viewModel: CatalogViewModel, onItemClick: (Folder) -> Unit) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = folders, key = { it.path }) { folder ->
            val mosaicPaths by produceState(emptyList<ArtPath>(), folder.path) {
                value = viewModel.getMosaicForFolder(folder.path)
            }
            Card(
                modifier  = Modifier.width(170.dp).clickable { onItemClick(folder) },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().height(140.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))) {
                        AsyncImage(model = folder.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                            contentDescription = "Single cover",
                            contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        MosaicArt(paths = mosaicPaths,
                            modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(folder.name, style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(folder.path, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${folder.trackCount} tracks · ${folder.albumCount} albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(folder.totalDurationMs.toHumanDuration(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatsBlock(folder.playCount, folder.lastPlayed, folder.totalPlayTimeMs)
                    }
                }
            }
        }
    }
}
