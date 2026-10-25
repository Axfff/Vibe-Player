package com.iven.musicplayergo

import android.app.Application
import android.content.res.Resources
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.iven.musicplayergo.models.Album
import com.iven.musicplayergo.models.Music
import com.iven.musicplayergo.utils.MusicUtils
import com.iven.musicplayergo.utils.Versioning
import kotlinx.coroutines.*
import java.io.File

/** A failed/unfinished query must not make saved playlist entries appear deleted. */
data class PlaylistLibraryState(
    val songs: List<Music> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val refreshFailed: Boolean = false
)


class MusicViewModel(application: Application): AndroidViewModel(application) {

    /**
     * This is the job for all coroutines started by this ViewModel.
     * Cancelling this job will cancel all coroutines started by this ViewModel.
     */
    private val mViewModelJob = SupervisorJob()

    private val mUiDispatcher = Dispatchers.Main
    private val mUiScope = CoroutineScope(mUiDispatcher + mViewModelJob)
    private var scanJob: Job? = null

    val deviceMusic = MutableLiveData<MutableList<Music>?>()
    private val mutablePlaylistLibraryState = MutableLiveData(PlaylistLibraryState())
    val playlistLibraryState: LiveData<PlaylistLibraryState> = mutablePlaylistLibraryState
    val playlistLibrary: List<Music>
        get() = mutablePlaylistLibraryState.value?.songs.orEmpty()

    private var mDeviceMusicList = mutableListOf<Music>()

    fun getSongFromIntent(queriedDisplayName: String) =
        mDeviceMusicList.firstOrNull { s -> s.displayName == queriedDisplayName }

    var deviceMusicFiltered: MutableList<Music>? = null

    //keys: artist || value: its songs
    var deviceSongsByArtist: Map<String?, List<Music>>? = null

    //keys: album || value: its songs
    var deviceMusicByAlbum: Map<String?, List<Music>>? = null

    //keys: artist || value: albums
    var deviceAlbumsByArtist: MutableMap<String, List<Album>>? = mutableMapOf()

    //keys: artist || value: songs contained in the folder
    var deviceMusicByFolder: Map<String, List<Music>>? = null

    fun getRandomMusic(): Music? = deviceMusicFiltered?.randomOrNull()

    /**
     * Cancel all coroutines when the ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        mViewModelJob.cancel()
    }

    fun cancel() {
        onCleared()
    }

    fun getDeviceMusic() {
        scanLibrary(updateSavedPlayback = true)
    }

    /** Refresh both library views while leaving the running playback snapshot alone. */
    fun refreshPlaylistLibrary() {
        scanLibrary(updateSavedPlayback = false)
    }

    private fun scanLibrary(updateSavedPlayback: Boolean) {
        if (scanJob?.isActive == true) return
        scanJob = mUiScope.launch {
            val previous = mutablePlaylistLibraryState.value ?: PlaylistLibraryState()
            mutablePlaylistLibraryState.value = previous.copy(isLoading = true, refreshFailed = false)
            val scanned = try {
                val scanned = withContext(Dispatchers.IO) { queryForMusic(getApplication()) }
                    ?: throw IllegalStateException("The media library query did not complete")
                mDeviceMusicList = scanned
                buildLibrary(getApplication<Application>().resources, updateSavedPlayback)
                scanned
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                exception.printStackTrace()
                mutablePlaylistLibraryState.value = previous.copy(
                    isLoading = false, refreshFailed = true
                )
                if (!previous.hasLoaded) deviceMusic.value = null
                return@launch
            }
            // Observer/UI failures are not failed MediaStore queries. Publish only
            // after query and index construction have completed successfully.
            mutablePlaylistLibraryState.value = PlaylistLibraryState(
                songs = scanned.toList(), hasLoaded = true
            )
            deviceMusic.value = scanned.toMutableList()
        }
    }

    @Suppress("DEPRECATION")
    fun queryForMusic(application: Application): MutableList<Music>? {
         return try {
             // Build a fresh result: a refresh must not retain deleted rows or add duplicates.
             val scannedMusic = mutableListOf<Music>()
             val pathColumn = if (Versioning.isQ()) {
                 MediaStore.Audio.AudioColumns.BUCKET_DISPLAY_NAME
             } else {
                 MediaStore.Audio.AudioColumns.DATA
             }

             val projection = arrayOf(
                 MediaStore.Audio.AudioColumns.ARTIST, // 0
                 MediaStore.Audio.AudioColumns.YEAR, // 1
                 MediaStore.Audio.AudioColumns.TRACK, // 2
                 MediaStore.Audio.AudioColumns.TITLE, // 3
                 MediaStore.Audio.AudioColumns.DISPLAY_NAME, // 4,
                 MediaStore.Audio.AudioColumns.DURATION, //5,
                 MediaStore.Audio.AudioColumns.ALBUM, // 6
                 MediaStore.Audio.AudioColumns.ALBUM_ID, // 7
                 pathColumn, // 8
                 MediaStore.Audio.AudioColumns._ID, // 9
                 MediaStore.MediaColumns.DATE_MODIFIED // 10
             )

             val selection = "${MediaStore.Audio.AudioColumns.IS_MUSIC} = 1"
             val sortOrder = MediaStore.Audio.Media.DEFAULT_SORT_ORDER

             val musicCursor = application.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, sortOrder)
                 ?: return null

             // Query the storage for music files
             musicCursor.use { cursor ->

                 val artistIndex =
                     cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST)
                 val yearIndex =
                     cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.YEAR)
                 val trackIndex =
                     cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TRACK)
                 val titleIndex =
                     cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE)
                 val displayNameIndex =
                     cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DISPLAY_NAME)
                 val durationIndex =
                     cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION)
                 val albumIndex =
                     cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM)
                 val albumIdIndex =
                     cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM_ID)
                 val relativePathIndex =
                     cursor.getColumnIndexOrThrow(pathColumn)
                 val idIndex =
                     cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns._ID)
                 val dateAddedIndex =
                     cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DATE_MODIFIED)

                 while (cursor.moveToNext()) {
                     // Now loop through the music files
                     val audioId = cursor.getLong(idIndex)
                     val audioArtist = cursor.getString(artistIndex)
                     val audioYear = cursor.getInt(yearIndex)
                     val audioTrack = cursor.getInt(trackIndex)
                     val audioTitle = cursor.getString(titleIndex)
                     val audioDisplayName = cursor.getString(displayNameIndex)
                     val audioDuration = cursor.getLong(durationIndex)
                     val audioAlbum = cursor.getString(albumIndex)
                     val albumId = cursor.getLong(albumIdIndex)
                     val audioRelativePath = cursor.getString(relativePathIndex)
                     val audioDateAdded = cursor.getInt(dateAddedIndex)

                     val audioFolderName =
                         if (Versioning.isQ()) {
                             audioRelativePath ?: application.getString(R.string.slash)
                         } else {
                             var returnedPath = File(audioRelativePath).parentFile?.name
                             if (returnedPath == null || returnedPath == "0") {
                                 returnedPath = application.getString(R.string.slash)
                             }
                             returnedPath
                         }

                     // Add the current music to the list
                     scannedMusic.add(
                         Music(
                             audioArtist,
                             audioYear,
                             audioTrack,
                             audioTitle,
                             audioDisplayName,
                             audioDuration,
                             audioAlbum,
                             albumId,
                             audioFolderName,
                             audioId,
                             GoConstants.ARTIST_VIEW,
                             0,
                             audioDateAdded
                         )
                     )
                 }
             }
             scannedMusic
         } catch (e: Exception) {
             e.printStackTrace()
             null
         }
    }

    private fun buildLibrary(resources: Resources, updateSavedPlayback: Boolean) {
        // Removing duplicates by comparing everything except path which is different
        // if the same song is hold in different paths
        deviceMusicFiltered =
            mDeviceMusicList.distinctBy { it.artist to it.year to it.track to it.title to it.duration to it.album }
                .toMutableList()

        GoPreferences.getPrefsInstance().filters?.let { filter ->
            deviceMusicFiltered =  deviceMusicFiltered?.filter { music ->
               !filter.contains(music.artist) and !filter.contains(music.album) and !filter.contains(music.relativePath)
           }?.toMutableList()
        }

        deviceMusicFiltered?.let { dsf ->
            // group music by artist
            deviceSongsByArtist = dsf.groupBy { it.artist }
            deviceMusicByAlbum = dsf.groupBy { it.album }
            deviceMusicByFolder = dsf.groupBy { it.relativePath!! }
        }

        // Rebuild derived indexes even when a successful scan has no songs.
        deviceAlbumsByArtist = mutableMapOf()
        // group artists songs by albums
        deviceSongsByArtist?.keys?.iterator()?.let { iterate ->
            while (iterate.hasNext()) {
                iterate.next()?.let { artistKey ->
                    val album = deviceSongsByArtist?.getValue(artistKey)
                    deviceAlbumsByArtist?.set(
                        artistKey, MusicUtils.buildSortedArtistAlbums(resources, album)
                    )
                }
            }
        }
        // Saved playlists are resolved without deleting unavailable memberships.
        // A user-requested refresh also must not replace an active playback snapshot.
        if (updateSavedPlayback) updatePreferences()
    }

    private fun updatePreferences() {
        // update queue/favorites by updating moved songs id, albumId
        // and filtering out deleted songs

        val prefs = GoPreferences.getPrefsInstance()

        deviceMusicFiltered?.let { deviceMusic ->

            // update queue songs id
            prefs.queue?.run {
                val updatedQueue = map {
                    val music = findMusic(it)
                    it.copy(albumId = music?.albumId, id= music?.id)
                }
                // filter queue to remove songs not available on the device
                prefs.queue = updatedQueue.filter {
                    deviceMusic.contains(it.copy(startFrom = 0, launchedBy = GoConstants.ARTIST_VIEW))
                }
            }

            // update favorite songs id
            prefs.favorites?.run {
                val updatedFavorites = map {
                    val music = findMusic(it)
                    it.copy(albumId = music?.albumId, id= music?.id)
                }
                prefs.favorites = updatedFavorites.filter {
                    // Playback source is not file identity (e.g. a playlist position bookmark).
                    deviceMusic.contains(it.copy(startFrom = 0, launchedBy = GoConstants.ARTIST_VIEW))
                }
            }

            // check if pre queue song exists and update id
            if (prefs.isQueue != null) {
                prefs.isQueue?.let { preQueueSong ->
                    val found = findMusic(preQueueSong)
                    prefs.isQueue = if (found != null) {
                        preQueueSong.copy(albumId = found.albumId, id = found.id)
                    } else {
                        getRandomMusic()
                    }
                }
            }

            // check if latestPlayedSong exists and update id
            if (prefs.latestPlayedSong == null) {
                prefs.latestPlayedSong = getRandomMusic()
            } else {
                prefs.latestPlayedSong?.let { lps ->
                    val found = findMusic(lps)
                    prefs.latestPlayedSong = if (found != null) {
                        lps.copy(albumId = found.albumId, id = found.id)
                    } else {
                        // if queue had started, update latestPlayedSong
                        // picking the first queued song
                        prefs.queue?.let { queue ->
                            if (prefs.isQueue != null && queue.isNotEmpty()) {
                                prefs.latestPlayedSong = queue[0]
                            }
                            return
                        }
                        getRandomMusic()
                    }
                }
            }
        }
    }

    private fun findMusic(song: Music?): Music? {
        val songToFind = song?.copy(startFrom = 0)
        return deviceMusicFiltered?.find { newMusic ->
            songToFind?.title == newMusic.title && songToFind?.displayName == newMusic.displayName
                    && songToFind?.track == newMusic.track && songToFind.album == newMusic.album
                    && songToFind.year == newMusic.year && songToFind.duration == newMusic.duration
        }
    }
}
