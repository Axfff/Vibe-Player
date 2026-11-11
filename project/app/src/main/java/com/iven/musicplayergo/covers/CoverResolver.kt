package com.iven.musicplayergo.covers

import java.io.File

sealed class CoverDisplaySource {
    data class CustomFile(val file: File) : CoverDisplaySource()
    data class MediaStoreAlbum(val albumId: Long) : CoverDisplaySource()
    object DefaultAlbum : CoverDisplaySource()
    object DefaultPlaylist : CoverDisplaySource()
}

interface CoverResolver { fun resolve(target: CoverTarget): CoverDisplaySource }

class RepositoryCoverResolver(private val repository: CustomCoverRepository) : CoverResolver {
    override fun resolve(target: CoverTarget): CoverDisplaySource {
        val custom = (repository.getCover(target) as? CoverResult.Success)?.value
        if (custom != null) {
            val file = repository.coverFile(custom)
            if (file.isFile && file.canRead()) return CoverDisplaySource.CustomFile(file)
        }
        return when (target) {
            is CoverTarget.Album -> if (target.albumId > 0) CoverDisplaySource.MediaStoreAlbum(target.albumId)
            else CoverDisplaySource.DefaultAlbum
            is CoverTarget.Playlist -> CoverDisplaySource.DefaultPlaylist
        }
    }
}
