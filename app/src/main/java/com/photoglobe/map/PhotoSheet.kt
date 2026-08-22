package com.photoglobe.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.photoglobe.data.PhotoEntity

/**
 * Tap a cluster, get the photos inside it (D-016).
 *
 * A bottom sheet rather than a new screen, so the map stays visible behind and context is
 * never lost. Note this is unrelated to the memory arithmetic in DESIGN.md section 5 - a
 * lazy grid decodes only the tiles on screen and recycles them. That section is about
 * thousands of simultaneous *marker* bitmaps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoGridSheet(
    photos: List<PhotoEntity>,
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var fullScreen by remember { mutableStateOf<PhotoEntity?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = if (photos.size == 1) "1 photo" else "${photos.size} photos",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(horizontal = 8.dp)
        ) {
            items(photos, key = { it.id }) { photo ->
                AsyncImage(
                    model = photo.contentUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(2.dp)
                        .aspectRatio(1f)
                        .clickable { fullScreen = photo }
                )
            }
        }
    }

    fullScreen?.let { photo ->
        Dialog(
            onDismissRequest = { fullScreen = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullScreen = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = photo.contentUri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
