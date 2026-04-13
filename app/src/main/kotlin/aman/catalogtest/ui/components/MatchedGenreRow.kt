package aman.catalogtest.ui.components

import aman.catalog.audio.models.ArtPath
import aman.catalog.audio.models.MatchedGenre
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun MatchedGenreRow(
    matchedGenre: MatchedGenre,
    getMosaic: suspend () -> List<ArtPath>,
    onClick: () -> Unit
) {
    val mosaicPaths by produceState(emptyList<ArtPath>(), matchedGenre.genre.id) {
        value = getMosaic()
    }

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AsyncImage(
                model              = matchedGenre.genre.coverArtPath?.let { "${it.path}?t=${it.dateModified}" },
                contentDescription = "Genre art",
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
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = matchedGenre.genre.name,
                style    = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text  = "${matchedGenre.genre.trackCount} tracks · " +
                        "${matchedGenre.genre.albumCount} albums · " +
                        matchedGenre.genre.totalDurationMs.toHumanDuration(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text  = "${matchedGenre.matchedTrackCount} of ${matchedGenre.genre.trackCount} tracks · " +
                        "${matchedGenre.matchedDurationMs.toHumanDuration()} of ${matchedGenre.genre.totalDurationMs.toHumanDuration()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            matchedGenre.genre.playCount.toPlayCountString()?.let {
                Text(
                    text     = "$it · ${matchedGenre.genre.totalPlayTimeMs.toHumanDuration()} listened · ${matchedGenre.genre.lastPlayed.toRelativeTimeString()}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 136.dp, end = 16.dp))
}
