package com.photoglobe.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.photoglobe.data.PhotoEntity
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * The whole app, as far as the MVP is concerned: launch straight onto this (D-013).
 * No splash, no menu, no onboarding between the icon and the map.
 */
@Composable
fun MapScreen(
    photos: List<PhotoEntity>,
    status: String,
    canScan: Boolean,
    onRequestAccess: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context)          // must run before MapView is constructed
        MapView(context).apply {
            getMapAsync { map ->
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(20.0, 0.0))
                    .zoom(1.2)
                    .build()
                map.setStyle(PhotoMap.STYLE_URL) { style ->
                    PhotoMap.install(style, emptyList())
                    mapLibreMap = map
                }
            }
        }
    }

    // MapView is a plain Android View and needs every lifecycle callback forwarded.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // Recompose pushes new photos into the source as the scan inserts batches (D-021).
        mapLibreMap?.let { map ->
            map.style?.let { style -> PhotoMap.update(style, photos) }
        }

        Card(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth()) {
            Text(
                text = if (status.isEmpty()) "${photos.size} photos on the map" else status,
                modifier = Modifier.padding(12.dp)
            )
            Button(
                onClick = { if (canScan) onScan() else onRequestAccess() },
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)
            ) {
                Text(if (canScan) "Scan library" else "Grant photo access")
            }
        }
    }
}
