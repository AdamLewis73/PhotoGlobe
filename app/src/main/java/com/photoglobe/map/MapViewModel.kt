package com.photoglobe.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photoglobe.data.MediaLibraryScanner
import com.photoglobe.data.PhotoEntity
import com.photoglobe.data.PhotoGlobeDatabase
import com.photoglobe.permission.MediaAccess
import com.photoglobe.permission.MediaTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel(app: Application) : AndroidViewModel(app) {

    private val db = PhotoGlobeDatabase.get(app)
    private val scanner = MediaLibraryScanner(app)

    /** Everything on the map. Room emits again automatically as the scan inserts batches. */
    val photos: StateFlow<List<PhotoEntity>> =
        db.photoDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    fun tier(): MediaTier = MediaAccess.tier(getApplication())

    fun refreshStatus() {
        val app = getApplication<Application>()
        val tier = MediaAccess.tier(app)
        _status.value = when {
            tier == MediaTier.NONE -> "No photo access yet"
            !MediaAccess.hasMediaLocation(app) ->
                "Location access missing - photos will look un-geotagged"
            else -> "$tier access"
        }
    }

    /**
     * Scan, inserting in batches so the map fills in as it goes rather than blocking on
     * completion (D-021). Expect roughly 4 ms per photo (D-027).
     */
    fun scan() {
        if (_scanning.value) return
        _scanning.value = true
        viewModelScope.launch {
            try {
                val ids = scanner.enumerate()
                _status.value = "Scanning ${ids.size} photos..."
                scanner.scan(ids, db.photoDao()) { p ->
                    _status.value =
                        if (p.done) "${p.geotagged} of ${p.total} photos have a location"
                        else "${p.processed} / ${p.total} - ${p.geotagged} placed"
                }
            } catch (t: Throwable) {
                _status.value = "Scan failed: ${t.javaClass.simpleName}"
            }
            _scanning.value = false
        }
    }
}
