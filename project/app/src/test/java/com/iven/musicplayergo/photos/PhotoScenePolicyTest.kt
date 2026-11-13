package com.iven.musicplayergo.photos

import com.iven.musicplayergo.preferences.english
import com.iven.musicplayergo.preferences.contains
import org.junit.Assert.*
import org.junit.Test

class PhotoScenePolicyTest {
    private fun scores(label: String, score: Float = .35f) = FloatArray(PhotoScenePolicy.names.size) { .1f }.apply {
        this[PhotoScenePolicy.names.keys.indexOf(label)] = score
    }
    @Test fun agreementKeepsFlowerCueWithoutInventingButterfly() {
        assertEquals(listOf(PhotoLabel("flowers", .35f)), PhotoScenePolicy.labels(arrayOf(scores("flowers"), scores("flowers"))))
        assertEquals("butterfly", PhotoScenePolicy.names.getValue("butterfly").english())
        assertEquals("butterfly", PhotoScenePolicy.labels(arrayOf(scores("butterfly"), scores("butterfly"))).single().label)
    }
    @Test fun aConflictingCropOrWeakMarginAbstains() {
        assertTrue(PhotoScenePolicy.labels(arrayOf(scores("butterfly"), scores("flowers"))).isEmpty())
        assertTrue(PhotoScenePolicy.labels(arrayOf(scores("flowers", .19f), scores("flowers", .19f))).isEmpty())
        val ambiguous = scores("flowers").apply { this[PhotoScenePolicy.names.keys.indexOf("butterfly")] = .34f }
        assertTrue(PhotoScenePolicy.labels(arrayOf(ambiguous, scores("flowers"))).isEmpty())
    }
    @Test fun unrelatedClassesCompeteAndBadOutputsAreRejected() {
        assertEquals("document", PhotoScenePolicy.labels(arrayOf(scores("document"), scores("document"))).single().label)
        assertThrows(IllegalArgumentException::class.java) { PhotoScenePolicy.labels(arrayOf(scores("flowers"))) }
        assertThrows(IllegalArgumentException::class.java) { PhotoScenePolicy.labels(arrayOf(floatArrayOf(1f), floatArrayOf(1f))) }
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, 1.1f)) {
            assertThrows(IllegalArgumentException::class.java) { PhotoScenePolicy.labels(arrayOf(scores("flowers",bad), scores("flowers"))) }
        }
    }
    @Test fun overlappingScenesMaySupportOnlyABroadDescription() {
        assertEquals("waterscape",PhotoScenePolicy.labels(arrayOf(scores("river"),scores("lakeside"))).single().label)
        val mixed = scores("flowers").apply { this[PhotoScenePolicy.names.keys.indexOf("meadow")] = .34f }
        assertEquals("flower_plants",PhotoScenePolicy.labels(arrayOf(mixed,mixed)).single().label)
        val outside = scores("river").apply { this[PhotoScenePolicy.names.keys.indexOf("document")] = .34f }
        assertTrue(PhotoScenePolicy.labels(arrayOf(outside,scores("lakeside"))).isEmpty())
        assertEquals("water scenery",PhotoScenePolicy.title("waterscape").english())
        assertTrue(PhotoScenePolicy.labels(arrayOf(scores("river",.19f),scores("lakeside",.19f))).isEmpty())
    }
    @Test fun atmosphereRetainsAmbiguousObjectsButRejectsFlatWeakAndDocumentInputs() {
        val scene = arrayOf(scores("flowers"),scores("butterfly"))
        val views = arrayOf(floatArrayOf(.4f,.25f,.20f,.25f),floatArrayOf(.35f,.25f,.2f,.25f))
        assertTrue(PhotoScenePolicy.labels(scene).isEmpty())
        val cues = PhotoAtmospherePolicy.cues(views,scene)
        assertEquals("lively",cues.maxBy { it.confidence }.label)
        assertEquals(1f,cues.sumOf { it.confidence.toDouble() }.toFloat(),.00001f)
        assertTrue(PhotoAtmospherePolicy.cues(arrayOf(FloatArray(4){.3f},FloatArray(4){.3f}),scene).isEmpty())
        assertTrue(PhotoAtmospherePolicy.cues(arrayOf(FloatArray(4){.1f},FloatArray(4){.1f}),scene).isEmpty())
        for (kind in listOf("document","screenshot"))
            assertTrue(PhotoAtmospherePolicy.cues(views,arrayOf(scores(kind),scores("flowers"))).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { PhotoAtmospherePolicy.cues(arrayOf(FloatArray(4){Float.NaN},views[1]),scene) }
        assertThrows(IllegalArgumentException::class.java) { PhotoAtmospherePolicy.cues(arrayOf(floatArrayOf(.3f)),scene) }
    }
}
