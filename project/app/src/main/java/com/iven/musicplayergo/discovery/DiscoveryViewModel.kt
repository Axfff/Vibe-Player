package com.iven.musicplayergo.discovery

import android.app.Application
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.iven.musicplayergo.preferences.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DiscoveryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = DiscoveryRepository(DiscoveryFileStorage(File(app.noBackupFilesDir, "discovery-collection-v1.json")))
    private val preferences = app.getSharedPreferences("discovery-settings", 0)
    private val debug = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val updates = MutableLiveData(0)
    var endpoint = preferences.getString("endpoint", "").orEmpty()
    var customService = preferences.getBoolean("custom_service", false)
        private set
    var intent = MusicIntent()
    var scene: String? = null
    var market = "US"
    var photoUri: Uri? = null
        private set
    var photo: PhotoIntent? = null
        private set
    var localPhotoDescription: UiText? = null
        private set
    var status: DiscoveryStatus? = null
        private set
    var results: DiscoveryResults? = null
        private set
    private var resultQuery: DiscoveryQuery? = null
    var collection = DiscoveryCollection()
        private set
    var collectionError: String? = null
        private set
    var message = "ready"
        private set
    var busy = false
        private set
    private var generation = 0
    private var job: Job? = null
    private var gateway: DiscoveryGateway? = null

    init { loadCollection() }

    private fun changed() { updates.value = (updates.value ?: 0) + 1 }

    fun loadCollection() {
        try { collection = repository.load(); collectionError = null }
        catch (error: DiscoveryFailure) { collectionError = error.code }
        changed()
    }

    fun selectPhoto(uri: Uri?) {
        cancel()
        photoUri = uri
        photo = null
        localPhotoDescription = null
        message = if (uri == null) "photo_cancelled" else "photo_selected"
        changed()
    }

    fun useManual() {
        cancel()
        photoUri = null
        photo = null
        localPhotoDescription = null
        message = "manual"
        changed()
    }

    fun connect() = runOperation { client ->
        val current = client.status()
        return@runOperation { status = current; message = "connected" }
    }

    fun useCustomService(enabled: Boolean) {
        if (enabled == customService) return
        cancel()
        customService = enabled
        preferences.edit().putBoolean("custom_service", enabled).apply()
        status = null
        message = "ready"
        changed()
    }

    fun discover() {
        if (busy) return
        val query = DiscoveryQuery(intent, scene, market, repository.exclusions(), photo)
        if (!query.valid()) { message = "invalid_input"; changed(); return }
        results = null
        resultQuery = null
        runOperation { client ->
            val current = client.status()
            val response = client.discover(current.token, query)
            return@runOperation { status = current; results = response; resultQuery = query; message = if (response.tracks.isEmpty()) "no_results" else "results" }
        }
    }

    /** Called only by the explicit positive consent action, never by the picker callback. */
    fun analyzeConsented() {
        val selected = photoUri ?: return
        val consentedStatus = status
        if (status?.canAnalyze != true) { message = "manual_model_blocked"; changed(); return }
        runOperation { client ->
            val current = client.status()
            if (!current.canAnalyze) throw DiscoveryFailure("vision_not_live")
            if (current.recipient != consentedStatus?.recipient || current.model != consentedStatus.model) throw DiscoveryFailure("vision_consent_changed")
            val jpeg = DiscoveryPhoto.prepare(getApplication<Application>().contentResolver, selected)
            val suggestion = client.analyze(current.token, jpeg)
            return@runOperation { status = current; photo = suggestion; localPhotoDescription = null; photoUri = null; message = "photo_suggested" }
        }
    }

    /** Explicit local action; never falls back to uploading when model/read/inference fails. */
    fun analyzeLocal() {
        if (busy) return
        val selected = photoUri ?: return
        val version = ++generation
        busy = true
        message = "loading"
        changed()
        job = viewModelScope.launch {
            try {
                val suggestion = withTimeout(60_000) { DiscoveryLocalPhoto(getApplication()).analyze(selected) }
                if (version == generation) {
                    photo = suggestion.intent
                    localPhotoDescription = suggestion.description
                    photoUri = null
                    message = "photo_suggested"
                }
            } catch (error: CancellationException) {
                if (version == generation) message = "cancelled"
                throw error
            } catch (error: Exception) {
                if (version == generation) message = (error as? DiscoveryFailure)?.code ?: "local_photo_failed"
            } finally {
                if (version == generation) { busy = false; changed() }
            }
        }
    }

    /** Suggestions are applied only by an explicit user choice; existing music intent always wins. */
    fun applySuggestions() {
        photo?.let {
            intent = intent.copy(mood = it.moods.first(), tags = it.tags.ifEmpty { intent.tags })
            scene = it.scenes.firstOrNull()
            message = "photo_applied"
        }
        changed()
    }

    fun save(track: DiscoveryTrack) = mutateCollection {
        val query = resultQuery ?: throw DiscoveryFailure("invalid_input")
        repository.save(track, query.intent, query.market)
        message = "saved"
    }
    fun remove(ref: DiscoveryRef) = mutateCollection { repository.remove(ref); message = "removed" }
    fun exclude(ref: DiscoveryRef) = mutateCollection {
        repository.exclude(ref)
        results = results?.copy(tracks = results!!.tracks.filterNot { it.ref == ref })
        message = "excluded"
    }
    fun clearExclusions() = mutateCollection { repository.clearExclusions(); message = "exclusions_cleared" }
    fun showMessage(code: String) { message = code; changed() }

    private fun mutateCollection(block: () -> Unit) {
        try { block(); collection = repository.collection }
        catch (error: DiscoveryFailure) { message = error.code }
        changed()
    }

    private fun runOperation(action: (DiscoveryGateway) -> (() -> Unit)) {
        if (busy) return
        val base = if (customService) try { DiscoveryLinks.endpoint(endpoint, debug) }
            catch (error: DiscoveryFailure) { message = error.code; changed(); return } else null
        if (base != null) {
            endpoint = base
            preferences.edit().putString("endpoint", base).apply()
        }
        val version = ++generation
        val client: DiscoveryGateway = if (base == null) DeviceDiscoveryCatalog() else DiscoveryClient(base)
        gateway = client
        busy = true
        message = "loading"
        changed()
        job = viewModelScope.launch {
            try {
                val apply = withTimeout(55_000) {
                    suspendCancellableCoroutine<() -> Unit> { continuation ->
                        val task = workers.submit {
                            try {
                                val result = action(client)
                                continuation.resume(result)
                            } catch (error: Exception) { continuation.resumeWithException(error) }
                        }
                        continuation.invokeOnCancellation { client.cancel(); task.cancel(true) }
                    }
                }
                if (version == generation) apply()
            } catch (error: CancellationException) {
                if (version == generation) message = "cancelled"
                throw error
            } catch (error: Exception) {
                if (version == generation) {
                    message = (error as? DiscoveryFailure)?.code ?: "network_error"
                    status = null
                }
            } finally {
                client.cancel()
                if (version == generation) { busy = false; gateway = null; changed() }
            }
        }
    }

    fun cancel() {
        generation++
        gateway?.cancel()
        gateway = null
        job?.cancel()
        job = null
        if (busy) { busy = false; message = "cancelled"; changed() }
    }

    override fun onCleared() { cancel(); super.onCleared() }

    companion object {
        private val workers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "Discovery request").apply { isDaemon = true }
        }
    }
}
