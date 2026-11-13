package com.iven.musicplayergo

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.request.CachePolicy
import com.iven.musicplayergo.utils.Theming
import com.iven.musicplayergo.covers.CoverRepositories
import com.iven.musicplayergo.covers.CoverArtFetcher
import com.iven.musicplayergo.preferences.AppLanguage


class GoApp : Application(), ImageLoaderFactory {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.context(base))
    }

    override fun onCreate() {
        super.onCreate()
        GoPreferences.initPrefs(applicationContext)
        AppLanguage.apply(this)
        // Migrate cover metadata out of backed-up general preferences on the first upgraded launch.
        CoverRepositories.get(this)
        AppCompatDelegate.setDefaultNightMode(Theming.getDefaultNightMode(applicationContext))
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.apply(this)
    }

    override fun newImageLoader() = ImageLoader.Builder(this)
        .components { add(CoverArtFetcher.Factory()) }
        .diskCachePolicy(CachePolicy.DISABLED)
        .crossfade(true)
        .build()
}
