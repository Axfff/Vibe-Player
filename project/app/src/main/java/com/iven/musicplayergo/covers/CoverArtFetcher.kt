package com.iven.musicplayergo.covers

import coil.ImageLoader
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.request.ImageRequest
import coil.request.Options
import coil.request.SuccessResult
import com.iven.musicplayergo.GoPreferences
import com.iven.musicplayergo.extensions.toAlbumArtURI
import java.io.IOException

/** One cancellable request owns the complete custom -> embedded -> default fallback chain. */
internal class CoverArtFetcher(
    private val target: CoverTarget,
    private val options: Options,
    private val loader: ImageLoader
) : Fetcher {
    override suspend fun fetch(): DrawableResult {
        val source = RepositoryCoverResolver(CoverRepositories.get(options.context)).resolve(target)
        val candidates = mutableListOf<Any>()
        if (source is CoverDisplaySource.CustomFile) candidates.add(source.file)
        if (target is CoverTarget.Album && target.albumId > 0 && GoPreferences.getPrefsInstance().isCovers) {
            candidates.add(target.albumId.toAlbumArtURI())
        }
        for (candidate in candidates) {
            val result = loader.execute(ImageRequest.Builder(options.context)
                .data(candidate).size(options.size).scale(options.scale).allowHardware(false).build())
            if (result is SuccessResult) return DrawableResult(result.drawable, result.isSampled, result.dataSource)
        }
        throw IOException("No readable artwork for ${target.storageKey}")
    }

    class Factory : Fetcher.Factory<CoverTarget> {
        override fun create(data: CoverTarget, options: Options, imageLoader: ImageLoader) =
            CoverArtFetcher(data, options, imageLoader)
    }
}
