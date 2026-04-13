package aman.catalogtest.ui.components

import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.MatchedAlbum
import aman.catalogtest.util.toHumanDuration
import aman.catalogtest.util.toPlayCountString
import aman.catalogtest.util.toRelativeTimeString
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

@Composable
fun MatchedAlbumRow(matchedAlbum: MatchedAlbum, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model              = matchedAlbum.album.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
            contentDescription = "Album art",
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = matchedAlbum.album.title,
                style    = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text  = buildString {
                    append(matchedAlbum.album.albumArtist.ifBlank { "Unknown Artist" })
                    if (matchedAlbum.album.year.isNotBlank()) append(" · ${matchedAlbum.album.year}")
                },
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text  = "${matchedAlbum.matchedTrackCount} of ${matchedAlbum.album.trackCount} tracks · " +
                        "${matchedAlbum.matchedDurationMs.toHumanDuration()} of ${matchedAlbum.album.totalDurationMs.toHumanDuration()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            matchedAlbum.album.playCount.toPlayCountString()?.let {
                Text(
                    text     = "$it · ${matchedAlbum.album.totalPlayTimeMs.toHumanDuration()} listened · ${matchedAlbum.album.lastPlayed.toRelativeTimeString()}",
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
