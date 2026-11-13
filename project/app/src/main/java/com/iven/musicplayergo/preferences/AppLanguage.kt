package com.iven.musicplayergo.preferences

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.iven.musicplayergo.GoPreferences
import java.util.Locale

/** One locale policy for activities, application services and deferred messages. */
object AppLanguage {
    const val DEFAULT = "en"

    fun tag(saved: String?): String = saved?.takeIf {
        it.matches(Regex("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*"))
    } ?: DEFAULT

    fun context(base: Context): Context = withLocale(base,
        Locale.forLanguageTag(tag(GoPreferences.initPrefs(base).locale)))

    /** Update this app's resources after an explicit language change, never system resources. */
    @Suppress("DEPRECATION")
    fun apply(application: Context) {
        val locale = Locale.forLanguageTag(tag(GoPreferences.initPrefs(application).locale))
        Locale.setDefault(locale)
        if (Build.VERSION.SDK_INT >= 24) LocaleList.setDefault(LocaleList(locale))
        val config = Configuration(application.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        if (Build.VERSION.SDK_INT >= 24) config.setLocales(LocaleList(locale))
        application.resources.updateConfiguration(config, application.resources.displayMetrics)
    }

    fun withLocale(base: Context, locale: Locale): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        if (Build.VERSION.SDK_INT >= 24) configuration.setLocales(LocaleList(locale))
        return base.createConfigurationContext(configuration)
    }
}
