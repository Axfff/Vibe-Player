package com.iven.musicplayergo.preferences

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import com.iven.musicplayergo.R
import java.util.*

// https://medium.com/swlh/android-app-specific-language-change-programmatically-using-kotlin-d650a5392220
class ContextUtils(base: Context): ContextWrapper(base) {

    companion object {

        fun updateLocale(ctx: Context, localeToSwitchTo: Locale): ContextWrapper =
            ContextUtils(AppLanguage.withLocale(ctx, localeToSwitchTo))

        fun getLocalesList(resources: Resources): Map<String, String> {
            val codes = resources.getStringArray(R.array.supported_locales)
            val names = resources.getStringArray(R.array.supported_locales_names)
            val localesMap = codes.zip(names).toMap()
            return localesMap.entries.sortedBy { it.value }.associate { it.toPair() }
        }
    }
}
