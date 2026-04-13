package aman.catalogtest.ui.components

import aman.catalog.audio.models.ArtPath
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * Mosaic cover art that distributes up to [maxImages] images evenly across rows.
 */
@Composable
fun MosaicArt(
    paths: List<ArtPath>,
    modifier: Modifier = Modifier,
    maxImages: Int = 12
) {
    val displayPaths = paths.take(maxImages)
    val count = displayPaths.size

    Box(modifier = modifier) {
        if (count == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        } else {
            
            val rowCount = when {
                count <= 2 -> 1
                count <= 6 -> 2
                count <= 9 -> 3
                else       -> 4
            }

            
            val baseItemsPerRow = count / rowCount
            val remainder = count % rowCount

            
            Column(modifier = Modifier.fillMaxSize()) {
                var currentIndex = 0
                
                for (rowIndex in 0 until rowCount) {
                    // top rows get one extra image when count doesn't divide evenly
                    val itemsInThisRow = baseItemsPerRow + if (rowIndex < remainder) 1 else 0
                    val rowPaths = displayPaths.subList(currentIndex, currentIndex + itemsInThisRow)
                    currentIndex += itemsInThisRow

                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (artPath in rowPaths) {
                            AsyncImage(
                                model              = "${artPath.path}?t=${artPath.dateModified}",
                                contentDescription = null,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}
