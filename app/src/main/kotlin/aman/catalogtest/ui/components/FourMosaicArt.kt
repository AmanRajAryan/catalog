package aman.catalogtest.ui.components

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
 * A fixed 4-image mosaic composable using manual layout (no LazyGrid).
 * Kept for reference — prefer [MosaicArt] for new code.
 *
 * Layout:
 * - 0 paths → grey placeholder
 * - 1 path  → full single image
 * - 2 paths → side by side
 * - 3 paths → one left, two stacked right
 * - 4+      → 2×2 grid
 */
@Composable
fun FourMosaicArt(
    paths:    List<String>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (paths.size) {
            0 -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            1 -> AsyncImage(
                model              = paths[0],
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
            2 -> Row(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model              = paths[0],
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.weight(1f).fillMaxHeight()
                )
                AsyncImage(
                    model              = paths[1],
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.weight(1f).fillMaxHeight()
                )
            }
            3 -> Row(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model              = paths[0],
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.weight(1f).fillMaxHeight()
                )
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    AsyncImage(
                        model              = paths[1],
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.weight(1f).fillMaxWidth()
                    )
                    AsyncImage(
                        model              = paths[2],
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
            else -> Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AsyncImage(
                        model              = paths[0],
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.weight(1f).fillMaxHeight()
                    )
                    AsyncImage(
                        model              = paths[1],
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AsyncImage(
                        model              = paths[2],
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.weight(1f).fillMaxHeight()
                    )
                    AsyncImage(
                        model              = paths[3],
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}
