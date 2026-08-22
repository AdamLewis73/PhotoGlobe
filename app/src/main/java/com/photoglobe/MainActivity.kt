package com.photoglobe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photoglobe.map.MapScreen
import com.photoglobe.map.MapViewModel
import com.photoglobe.permission.MediaAccess
import com.photoglobe.permission.MediaTier

/**
 * Single activity. Launches straight onto the map - cold-start-to-map is the headline
 * metric (D-013), so nothing is allowed between the launcher icon and a visible map.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MapViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshStatus()
        if (viewModel.tier() != MediaTier.NONE) viewModel.scan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.refreshStatus()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val photos by viewModel.photos.collectAsStateWithLifecycle()
                    val status by viewModel.status.collectAsStateWithLifecycle()
                    val selection by viewModel.selection.collectAsStateWithLifecycle()

                    MapScreen(
                        photos = photos,
                        status = status,
                        canScan = viewModel.tier() != MediaTier.NONE,
                        onRequestAccess = {
                            permissionLauncher.launch(MediaAccess.requiredPermissions())
                        },
                        onScan = { viewModel.scan() },
                        selection = selection,
                        onMapTap = { ids -> viewModel.selectPhotos(ids) },
                        onDismissSheet = { viewModel.clearSelection() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
    }
}
