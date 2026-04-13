package aman.catalogtest.ui.artists

import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.Artist
import aman.catalogtest.CatalogViewModel
import aman.catalogtest.util.toHumanDuration
import aman.catalogtest.util.toPlayCountString
import aman.catalogtest.util.toRelativeTimeString
import androidx.compose.runtime.produceState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

@Composable
fun ArtistListScreen(
    artists: List<Artist>,
    viewModel: CatalogViewModel,
    isAlbumArtist: Boolean = false,
    onItemClick: (id: Long, name: String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = artists, key = { it.id }) { artist ->
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(artist.id, artist.name) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Fetch mosaic by the artist type
                val mosaicPaths by produceState(emptyList<ArtPath>(), artist.id, isAlbumArtist) {
                    value = if (isAlbumArtist) {
                        viewModel.getMosaicForAlbumArtist(artist.id)
                    } else {
                        viewModel.getMosaicForTrackArtist(artist.id)
                    }
                }
                AsyncImage(
                    model              = artist.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                    contentDescription = "Single cover",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                MosaicArt(
                    paths    = mosaicPaths,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = artist.name,
                        style    = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text  = buildString {
                            append("${artist.trackCount} tracks")
                            if (artist.albumCount > 0) append(" · ${artist.albumCount} albums")
                            append(" · ${artist.totalDurationMs.toHumanDuration()}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    artist.playCount.toPlayCountString()?.let {
                        Text(
                            text  = "$it · ${artist.totalPlayTimeMs.toHumanDuration()} listened · ${artist.lastPlayed.toRelativeTimeString()}",
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
