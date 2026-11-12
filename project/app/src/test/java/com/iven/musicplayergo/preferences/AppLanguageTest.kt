package com.iven.musicplayergo.preferences

import com.iven.musicplayergo.R
import com.iven.musicplayergo.discovery.DiscoveryChoices
import com.iven.musicplayergo.discovery.DiscoveryLabels
import com.iven.musicplayergo.photos.PhotoSource
import com.iven.musicplayergo.photos.PhotoSourceKind
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AppLanguageTest {
    @Test fun defaultAndInvalidPreferencesUseEnglishWhileExplicitLanguagesSurvive() {
        listOf(null, "", "  ", "en_US", "file://locale").forEach { assertEquals("en", AppLanguage.tag(it)) }
        listOf("en", "fr", "pt-BR", "zh-TW").forEach { assertEquals(it, AppLanguage.tag(it)) }
    }
    @Test fun nestedDescriptionsAndUserContentRenderWithoutChangingSongOrFileNames() {
        val text = UiText.of(R.string.photo_chosen_mood, UiText.Joined(listOf(
            UiText.of(R.string.photo_mood_calm), UiText.Literal("自定义 title"))))
        assertEquals("You chose: calm, 自定义 title", text.english())
        assertEquals("Importing Photo analysis: 50%", UiText.of(R.string.photo_model_progress,
            UiText.of(R.string.photo_model_photo), 50).english())
    }

    @Test fun defaultResourcesAreCompleteAndDoNotReintroduceChineseUiCopy() {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        fun keys(directory: File): Set<String> = directory.listFiles().orEmpty().filter { it.extension == "xml" }.flatMap { file ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            (0 until nodes.length).map { index ->
                val node = nodes.item(index)
                if (directory.name == "values") assertFalse("Default UI copy: ${file.name}/${node.attributes.getNamedItem("name").nodeValue}",
                    node.textContent.any { it in '\u4e00'..'\u9fff' })
                node.attributes.getNamedItem("name").nodeValue
            }
        }.toSet()
        val defaults = keys(File(res, "values"))
        res.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("values-") }.forEach {
            // Localized resources must have an English fallback (ignore internal AndroidX styles).
            assertTrue("Missing default strings for ${it.name}: ${keys(it) - defaults}", defaults.containsAll(keys(it)))
        }
    }

    @Test fun protocolChoicesAndLegacyGeneratedSourceNamesUseDisplayResourcesWithoutDataMigration() {
        (DiscoveryChoices.moods + DiscoveryChoices.tags + DiscoveryChoices.scenes + DiscoveryChoices.markets).forEach {
            assertTrue(DiscoveryLabels.text(it) is UiText.Resource)
            assertTrue(DiscoveryLabels.text(it).english().isNotBlank())
        }
        assertEquals("Warm colors", DiscoveryLabels.text("warm_colors").english())
        assertEquals("United States (US)", DiscoveryLabels.text("US").english())
        assertEquals("Original name", DiscoveryLabels.text("Original name").english())
        val old = PhotoSource("existing", "照片文件夹", PhotoSourceKind.DIRECTORY, listOf("content://existing/tree"))
        assertEquals("Photo folder", old.displayName().english())
        assertEquals("照片文件夹", old.name)
        assertEquals("Selected photos", old.copy(name = "已选照片组").displayName().english())
        assertEquals("My album 原名", old.copy(name = "My album 原名").displayName().english())
    }
}
