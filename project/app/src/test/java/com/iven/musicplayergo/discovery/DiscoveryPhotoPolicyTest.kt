package com.iven.musicplayergo.discovery

import com.iven.musicplayergo.photos.*
import org.junit.Assert.*
import org.junit.Test

class DiscoveryPhotoPolicyTest {
    private fun features(vararg scores: Float, label: String = "lakeside") = PhotoFeatures(
        ScopedPhoto("arbitrary-id", "content://selected/photo"), "bytes-hash", "real-model-version",
        listOf(PhotoLabel(label, .4f)), listOf("lively", "peaceful", "reflective", "intense")
            .zip(scores.toList()).map { PhotoLabel(it.first, it.second) })

    @Test fun suggestionsUsePictureSignalsWithoutInventingMusicStyles() {
        val result = DiscoveryPhotoPolicy.suggest(features(.1f, .65f, .15f, .1f))!!
        assertEquals(listOf("calm"), result.intent.moods)
        assertEquals(listOf("water"), result.intent.scenes)
        assertTrue(result.intent.tags.isEmpty())
        assertTrue(result.intent.valid())
        assertEquals("medium", result.intent.uncertainty)
        assertTrue(result.description.contains("real-model-version"))
        assertTrue(result.description.contains("宁静舒缓"))
    }

    @Test fun closeAtmospheresRemainExplicitlyUncertain() {
        val result = DiscoveryPhotoPolicy.suggest(features(.1f, .42f, .40f, .08f, label = "unknown"))!!
        assertEquals(listOf("calm", "melancholic"), result.intent.moods)
        assertEquals("high", result.intent.uncertainty)
        assertTrue(result.intent.scenes.isEmpty())
        assertTrue(result.description.contains("不确定"))
    }

    @Test fun malformedWeakAndDocumentSignalsProduceNoSuggestion() {
        val original = features(.1f, .65f, .15f, .1f)
        val invalid = listOf(
            original.copy(atmospheres = emptyList()),
            original.copy(atmospheres = List(4) { PhotoLabel("peaceful", .25f) }),
            features(Float.NaN, .5f, .2f, .3f), features(-.1f, .8f, .2f, .1f),
            features(.2f, .8f, .2f, .1f), features(.25f, .25f, .25f, .25f),
            features(.1f, .65f, .15f, .1f, label = "document"),
            features(.1f, .65f, .15f, .1f, label = "screenshot"))
        invalid.forEach { assertNull(DiscoveryPhotoPolicy.suggest(it)) }
    }

    @Test fun allAtmosphereMappingsAndBroadScenesAreFiniteAndDeterministic() {
        val expected = listOf("bright", "calm", "melancholic", "energetic")
        val scenes = listOf("urban" to "city", "night_sky" to "night", "indoor" to "indoors", "mountain_scenery" to "nature")
        for (index in 0..3) {
            val feature = features(*FloatArray(4) { if (it == index) .7f else .1f }, label = scenes[index].first)
            val result = DiscoveryPhotoPolicy.suggest(feature)!!
            assertEquals(listOf(expected[index]), result.intent.moods)
            assertEquals(listOf(scenes[index].second), result.intent.scenes)
            assertEquals(result, DiscoveryPhotoPolicy.suggest(feature.copy(atmospheres = feature.atmospheres.reversed())))
            assertEquals(result, DiscoveryPhotoPolicy.suggest(feature.copy(photo = ScopedPhoto("different-name", "content://different"))))
        }
    }
}
