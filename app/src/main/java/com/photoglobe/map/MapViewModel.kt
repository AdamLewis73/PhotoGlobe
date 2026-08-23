package com.photoglobe.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photoglobe.data.LibrarySync
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
    private val librarySync = LibrarySync(app, db.photoDao(), db.scanStateDao())

    /** Everything on the map. Room emits again automatically as the scan inserts batches. */
    val photos: StateFlow<List<PhotoEntity>> =
        db.photoDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Photos behind the last tapped cluster. Empty means the sheet is closed (D-016). */
    private val _selection = MutableStateFlow<List<PhotoEntity>>(emptyList())
    val selection: StateFlow<List<PhotoEntity>> = _selection.asStateFlow()

    fun selectPhotos(ids: List<Long>) {
        if (ids.isEmpty()) { _selection.value = emptyList(); return }
        viewModelScope.launch { _selection.value = db.photoDao().byIds(ids) }
    }

    fun clearSelection() { _selection.value = emptyList() }

    fun tier(): MediaTier = MediaAccess.tier(getApplication())

    fun refreshStatus() {
        val app = getApplication<Application>()
        val tier = MediaAccess.tier(app)
        _status.value = when {
            tier == MediaTier.NONE -> "No photo access yet"
            !MediaAccess.hasMediaLocation(app) ->
                "Location access missing - photos will look un-geotagged"
            else -> ""
        }
    }

    /**
     * Runs on every resume (D-006). The first call scans the whole library; every call after
     * that touches only what is new, so it costs milliseconds and the user never notices it.
     *
     * [quiet] suppresses progress chatter for automatic runs - there is no reason to narrate
     * a sync that found two new photos.
     */
    fun sync(quiet: Boolean = false) {
        if (_scanning.value) return
        if (tier() == MediaTier.NONE) return
        _scanning.value = true

        viewModelScope.launch {
            try {
                val result = librarySync.sync { p ->
                    if (!quiet || p.total > PROGRESS_THRESHOLD) {
                        _status.value =
                            if (p.done) ""
                            else "${p.processed} / ${p.total} - ${p.geotagged} placed"
                    }
                }
                _status.value = summarise(result, quiet)
            } catch (t: Throwable) {
                _status.value = "Sync failed: ${t.javaClass.simpleName}"
            }
            _scanning.value = false
        }
    }

    private fun summarise(result: LibrarySync.Result, quiet: Boolean): String = when {
        result.fullScan -> "${photos.value.size} photos placed"
        result.added == 0 && result.removed == 0 -> if (quiet) "" else "Up to date"
        else -> buildString {
            if (result.added > 0) append("${result.added} new")
            if (result.removed > 0) {
                if (isNotEmpty()) append(", ")
                append("${result.removed} removed")
            }
        }
    }

    private companion object {
        /** Above this many candidates a "quiet" sync still shows progress - it is no longer quick. */
        const val PROGRESS_THRESHOLD = 50
    }
}
