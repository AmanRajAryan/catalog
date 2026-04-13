package aman.catalogtest.ui.tracks

import aman.catalog.audio.models.Track
import aman.catalogtest.util.toHumanDuration
import aman.catalogtest.util.toPlayCountString
import aman.catalogtest.util.toRelativeTimeString
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/**
 * A single track row used across all list screens.
 *
 * @param track           The track to display.
 * @param onFavoriteClick Called when the heart icon is tapped.
 * @param onClick         Called when the row body is tapped (opens options dialog).
 * @param trailingContent Optional slot for a custom trailing composable (e.g. a drag handle).
 *                        When provided it replaces the default favourite icon button.
 */
@Composable
fun TrackItemRow(
    track: Track,
    onFavoriteClick: (Track) -> Unit,
    onClick: (Track) -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable { onClick(track) }
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
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

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = track.title,
                style    = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text     = "${track.artist} · ${track.album}",
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (track.artists.size > 1) {
                Text(
                    text     = track.artists.joinToString(" · ") { it.name },
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            track.playCount.toPlayCountString()?.let {
                Text(
                    text     = "$it · ${track.totalPlayTimeMs.toHumanDuration()} listened · ${track.lastPlayed.toRelativeTimeString()}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        } else {
            IconButton(onClick = { onFavoriteClick(track) }) {
                Icon(
                    imageVector        = if (track.dateFavorited > 0) Icons.Filled.Favorite
                                        else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (track.dateFavorited > 0) "Unfavourite" else "Favourite",
                    tint               = if (track.dateFavorited > 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
