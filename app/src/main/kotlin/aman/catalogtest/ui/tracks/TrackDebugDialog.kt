package aman.catalogtest.ui.tracks

import aman.catalog.audio.models.Track
import aman.catalogtest.util.toDateAddedString
import aman.catalogtest.util.toDateString
import aman.catalogtest.util.toHumanDuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun TrackDebugDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    val fields = remember(track) {
        buildList {
            add("── Identity" to "")
            add("id"              to track.id.toString())
            add("path"            to track.path)
            add("uri"             to track.uri.toString())
            add("mimeType"        to track.mimeType)
            add("sizeBytes"       to "%,d bytes (%.2f MB)".format(track.sizeBytes, track.sizeBytes / 1_048_576.0))

            add("── Display Tags" to "")
            add("title"           to track.title)
            add("artist"          to track.artist)
            add("album"           to track.album)
            add("albumArtist"     to track.albumArtist.ifBlank { "—" })
            add("genre"           to track.genre.ifBlank { "—" })
            add("composer"        to track.composer.ifBlank { "—" })
            add("lyricist"        to track.lyricist.ifBlank { "—" })

            add("── Structured Entities" to "")
            add("artists"         to track.artists.joinToString { "${it.name} (id=${it.id})" }.ifBlank { "—" })
            add("genres"          to track.genres.joinToString { "${it.name} (id=${it.id})" }.ifBlank { "—" })
            add("composers"       to track.composers.joinToString { "${it.name} (id=${it.id})" }.ifBlank { "—" })
            add("lyricists"       to track.lyricists.joinToString { "${it.name} (id=${it.id})" }.ifBlank { "—" })

            add("── Album Link" to "")
            add("albumId"         to (track.albumId?.toString() ?: "null (SET_NULL)"))
            add("folderName"      to track.folderName)
            add("folderPath"      to track.folderPath)

            add("── Playback Metadata" to "")
            add("durationMs"      to "${track.durationMs} ms (${track.durationMs.toHumanDuration()})")
            add("trackNumber"     to track.trackNumber.toString())
            add("discNumber"      to track.discNumber.toString())
            add("year"            to if (track.year > 0) track.year.toString() else "—")
            add("releaseDate"     to track.releaseDate.ifBlank { "—" })

            add("── Timestamps" to "")
            add("dateAdded"       to "${track.dateAdded}s → ${track.dateAdded.toDateAddedString()}")
            add("dateModified"    to "${track.dateModified}ms → ${track.dateModified.toDateString()}")

            add("── User Data" to "")
            add("playCount"       to track.playCount.toString())
            add("lastPlayed"      to if (track.lastPlayed > 0) "${track.lastPlayed}ms → ${track.lastPlayed.toDateString()}" else "Never played")
            add("totalPlayTime"   to "${track.totalPlayTimeMs} ms (${track.totalPlayTimeMs.toHumanDuration()})")
            add("dateFavorited"   to if (track.dateFavorited > 0) "${track.dateFavorited}ms → ${track.dateFavorited.toDateString()}" else "Not favourited")

            add("── Extended Metadata (TagLib)" to "")
            add("bitrate"         to "${track.metadata.bitrate} kbps")
            add("sampleRate"      to "${track.metadata.sampleRate} Hz")
            add("channels"        to track.metadata.channels.toString())
            add("codec"           to track.metadata.codec.ifBlank { "—" })
            add("bitsPerSample"   to if (track.metadata.bitsPerSample > 0) "${track.metadata.bitsPerSample}-bit" else "—")
            add("contentRating"   to when (track.metadata.contentRating) {
                1    -> "1 (Explicit)"
                2    -> "2 (Clean)"
                else -> "0 (None/Unknown)"
            })

            add("── Raw Found Tags (TagLib)" to "")
            add("foundTitle"       to track.metadata.foundTitle.ifBlank { "—" })
            add("foundArtist"      to track.metadata.foundArtist.ifBlank { "—" })
            add("foundAlbum"       to track.metadata.foundAlbum.ifBlank { "—" })
            add("foundAlbumArtist" to track.metadata.foundAlbumArtist.ifBlank { "—" })
            add("foundGenre"       to track.metadata.foundGenre.ifBlank { "—" })
            add("foundComposer"    to track.metadata.foundComposer.ifBlank { "—" })
            add("foundLyricist"    to track.metadata.foundLyricist.ifBlank { "—" })
            add("foundYear"        to if (track.metadata.foundYear > 0) track.metadata.foundYear.toString() else "—")
            add("foundReleaseDate" to track.metadata.foundReleaseDate.ifBlank { "—" })
            add("foundTrackNumber" to track.metadata.foundTrackNumber.toString())
            add("foundDiscNumber"  to track.metadata.foundDiscNumber.toString())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Debug Viewer", style = MaterialTheme.typography.titleMedium) },
        text             = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(fields) { (key, value) ->
                    if (value.isEmpty()) {
                        Text(
                            text     = key,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    } else {
                        Column(modifier = Modifier.padding(vertical = 3.dp)) {
                            Text(
                                text  = key,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text  = value,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }
            }
        },
        confirmButton    = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
