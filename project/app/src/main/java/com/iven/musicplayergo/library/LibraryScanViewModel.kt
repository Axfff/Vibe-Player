package com.iven.musicplayergo.library

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class LibraryScanState(
    val running: Boolean = false,
    val report: MediaScanReport? = null,
    val permissionDenied: Boolean = false,
    val failed: Boolean = false,
    val refreshPending: Boolean = false
)

/** Activity-scoped: survives rotation and never retains an Activity, View or dialog. */
class LibraryScanViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableLiveData(LibraryScanState())
    internal val state: LiveData<LibraryScanState> = mutableState
    private var job: Job? = null

    fun startScan() {
        if (job?.isActive == true) return
        val application = getApplication<Application>()
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(application, permission) != PackageManager.PERMISSION_GRANTED) {
            mutableState.value = LibraryScanState(permissionDenied = true)
            return
        }
        mutableState.value = LibraryScanState(running = true)
        job = viewModelScope.launch {
            try {
                val report = LocalMediaIndexer(application).scan()
                mutableState.value = LibraryScanState(report = report, refreshPending = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                mutableState.value = LibraryScanState(permissionDenied = true)
            } catch (_: Exception) {
                // Do not log device filenames. A failed indexing request is never an empty library.
                mutableState.value = LibraryScanState(failed = true)
            }
        }
    }

    /** Consume before refreshing: LiveData observers can dispatch synchronously. */
    internal fun consumeRefreshRequest(): Boolean {
        val current = mutableState.value ?: return false
        if (!current.refreshPending) return false
        mutableState.value = current.copy(refreshPending = false)
        return true
    }
}
