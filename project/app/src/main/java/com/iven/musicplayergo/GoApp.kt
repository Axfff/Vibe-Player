package com.iven.musicplayergo

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.request.CachePolicy
import com.iven.musicplayergo.utils.Theming
import com.iven.musicplayergo.covers.CoverRepositories
import com.iven.musicplayergo.covers.CoverArtFetcher


class GoApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        GoPreferences.initPrefs(applicationContext)
        // Migrate cover metadata out of backed-up general preferences on the first upgraded launch.
        CoverRepositories.get(this)
        AppCompatDelegate.setDefaultNightMode(Theming.getDefaultNightMode(applicationContext))
    }

    override fun newImageLoader() = ImageLoader.Builder(this)
        .components { add(CoverArtFetcher.Factory()) }
        .diskCachePolicy(CachePolicy.DISABLED)
        .crossfade(true)
        .build()
}
