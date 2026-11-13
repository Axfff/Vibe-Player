package com.iven.musicplayergo.preferences

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.CheckBox
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.GoPreferences
import com.iven.musicplayergo.R
import com.iven.musicplayergo.discovery.DiscoveryActivity
import com.iven.musicplayergo.photos.PhotoModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class EnglishUiTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun texts(view: View): List<String> = (if (view is TextView) listOf(view.text.toString()) else emptyList()) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()

    @Test fun englishDefaultDoesNotInheritChineseSystemResourcesAndExplicitChoicePersists() {
        val prefs = GoPreferences.initPrefs(context)
        val previous = prefs.locale
        try {
            prefs.locale = null
            val chinese = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) })
            assertEquals("zh", chinese.resources.configuration.locales[0].language)
            val english = AppLanguage.context(chinese)
            assertEquals("en", english.resources.configuration.locales[0].language)
            assertEquals("Adjust cover", english.getString(R.string.cover_crop_title))
            assertEquals("Photo analysis", PhotoModel.PHOTO.title.resolve(chinese))
            assertEquals("zh", chinese.resources.configuration.locales[0].language)
            assertEquals("en", ContextUtils.updateLocale(chinese, Locale.ENGLISH).resources.configuration.locales[0].language)
            prefs.locale = "en"
            AppLanguage.apply(context.applicationContext)
            assertEquals("en", context.applicationContext.resources.configuration.locales[0].language)
            assertEquals("en", GoPreferences(context).locale)
            assertTrue(ContextUtils.getLocalesList(english.resources).containsKey("en"))
        } finally { prefs.locale = previous; AppLanguage.apply(context.applicationContext) }
    }

    @Test fun legacyLocalhostDoesNotBlockFirstUseAndDiscoverySetupNeedsOnlyThePhotoModel() {
        val prefs = context.getSharedPreferences("discovery-settings", 0)
        val oldEndpoint = prefs.getString("endpoint", null)
        val hadMode = prefs.contains("custom_service")
        val oldMode = prefs.getBoolean("custom_service", false)
        val language = GoPreferences.initPrefs(context)
        val oldLanguage = language.locale
        try {
            language.locale = "en"
            prefs.edit().putString("endpoint", "http://127.0.0.1:8765").remove("custom_service").commit()
            ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertFalse(activity.model.customService)
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.discovery_service_fields).visibility)
                    assertTrue(activity.findViewById<TextView>(R.id.discovery_provider_status).text.contains("no computer"))
                    assertFalse(texts(activity.window.decorView).any { it.any { character -> character in '\u4e00'..'\u9fff' } })
                    activity.findViewById<CheckBox>(R.id.discovery_custom_service).performClick()
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.discovery_service_fields).visibility)
                    activity.findViewById<CheckBox>(R.id.discovery_custom_service).performClick()
                    activity.findViewById<View>(R.id.discovery_photo_models).performClick()
                    activity.supportFragmentManager.executePendingTransactions()
                    val shown = WindowInspector.getGlobalWindowViews().flatMap(::texts)
                    assertTrue(shown.contains("Import Photo analysis file"))
                    assertFalse(shown.contains("Import Music analysis file"))
                    assertFalse(shown.contains("Import Music atmosphere file"))
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals("en", activity.resources.configuration.locales[0].language)
                    assertFalse(activity.model.customService)
                    assertEquals("http://127.0.0.1:8765", activity.model.endpoint)
                }
            }
        } finally {
            prefs.edit().putString("endpoint", oldEndpoint).apply {
                if (hadMode) putBoolean("custom_service", oldMode) else remove("custom_service")
            }.commit()
            language.locale = oldLanguage
            AppLanguage.apply(context.applicationContext)
        }
    }
}
