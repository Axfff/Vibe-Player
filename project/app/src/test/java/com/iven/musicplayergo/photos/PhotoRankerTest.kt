package com.iven.musicplayergo.photos

import com.iven.musicplayergo.preferences.english
import com.iven.musicplayergo.preferences.contains
import com.iven.musicplayergo.analysis.MoodScore
import org.junit.Assert.*
import org.junit.Test

class PhotoRankerTest {
    private fun photo(id: String, label: String = "lakeside", score: Float = .8f, hash: String = id) =
        PhotoFeatures(ScopedPhoto(id, "content://test/$id"), hash, "fixture-model", listOf(PhotoLabel(label, score)),
            PhotoAtmospherePolicy.names.keys.map { PhotoLabel(it, if (it == if (label == "flowers") "lively" else "peaceful") score else (1f-score)/3f) })
    @Test fun formulaUsesIndependentMusicAndPhotoScores() {
        val result = PhotoRanker.rank(listOf(MoodScore("calm", .8f), MoodScore("happy", .2f)), listOf(photo("a"))).single()
        assertEquals(.7568627f, result.score, .00001f); assertTrue(result.reasons.first().contains("calm")); assertEquals(PhotoRanker.VERSION, result.ruleVersion)
    }
    @Test fun tiesAreStableAndDefaultIsFive() {
        val candidates = (10 downTo 1).map { photo(it.toString().padStart(2,'0')) }
        assertEquals(listOf("01","02","03","04","05"), PhotoRanker.rank(listOf(MoodScore("calm",1f)), candidates).map { it.features.photo.id })
    }
    @Test fun duplicateContentsAreNotRepeated() {
        assertEquals(1, PhotoRanker.rank(listOf(MoodScore("calm",1f)), listOf(photo("a",hash="same"),photo("b",hash="same"))).size)
        val candidates = listOf(photo("b",hash="same"),photo("a",hash="same"),photo("c"))
        assertEquals(PhotoRanker.rank(listOf(MoodScore("calm",1f)), candidates),
            PhotoRanker.rank(listOf(MoodScore("calm",1f)), candidates.reversed()))
    }
    @Test fun noSignalOrMatchReturnsEmpty() {
        assertTrue(PhotoRanker.rank(emptyList(),listOf(photo("a"))).isEmpty())
        assertTrue(PhotoRanker.rank(listOf(MoodScore("happy",1f)),listOf(photo("a"))).isEmpty())
        assertTrue(PhotoRanker.rank(listOf(MoodScore("calm",Float.NaN)),listOf(photo("a"))).isEmpty())
        assertTrue(PhotoRanker.rank(listOf(MoodScore("calm",1f)),listOf(photo("a",score=.01f))).isEmpty())
    }
    @Test fun fewerThanFiveReturnsActualCount() {
        assertEquals(2,PhotoRanker.rank(listOf(MoodScore("calm",1f)),listOf(photo("a"),photo("b"))).size)
    }
    @Test fun onlyActualMappedCuesAreExplained() {
        val result=PhotoRanker.rank(listOf(MoodScore("calm",1f),MoodScore("unmapped",1f)),listOf(photo("a"))).single()
        assertEquals(2,result.reasons.size);assertTrue(result.reasons.none { it.contains("unmapped") })
    }
    @Test fun suggestionsComeOnlyFromExplicitSignals() {
        assertTrue(PhotoRanker.suggestions(emptyList()).isEmpty())
        assertEquals(listOf("forest","lakeside","valley"),PhotoRanker.suggestions(listOf(MoodScore("calm",1f))).map { it.english() })
    }
    @Test fun multipleAtmospheresDoNotHideTheConcreteSceneExplanation() {
        val feature = photo("mixed").copy(atmospheres=listOf(PhotoLabel("lively",.05f), PhotoLabel("peaceful",.45f),
            PhotoLabel("reflective",.4f), PhotoLabel("intense",.1f)))
        val result = PhotoRanker.rank(listOf(MoodScore("calm",.6f),MoodScore("sad",.4f)),listOf(feature)).single()
        assertEquals(2,result.reasons.size)
        assertTrue(result.reasons.first().contains("calm")); assertTrue(result.reasons.first().contains("sad"))
        assertTrue(result.reasons.last().contains("lakeside")); assertTrue(result.reasons.last().contains("model estimate"))
    }
    @Test fun musicSummaryReportsMixedEvidenceAndRejectsInvalidOrUnmappedSignals() {
        val mixed = listOf(MoodScore("relaxing",.15f),MoodScore("sad",.06f))
        assertTrue(PhotoRanker.musicDescription(mixed).contains("Calm and sad cues coexist"))
        assertTrue(PhotoRanker.musicDescription(mixed).contains("relaxing, sad"))
        assertEquals(PhotoRanker.musicDescription(mixed),PhotoRanker.musicDescription(mixed+mixed+listOf(MoodScore("sad",.05f))))
        assertFalse(PhotoRanker.musicDescription(listOf(MoodScore("happy",.8f))).contains("coexist"))
        assertEquals(PhotoRanker.musicDescription(emptyList()),PhotoRanker.musicDescription(listOf(MoodScore("happy",Float.NaN),
            MoodScore("sad",.01f),MoodScore("calm",2f),MoodScore("unknown",1f))))
    }
    @Test fun unrelatedAndUncertainScenesDoNotBecomeMoodRecommendations() {
        val happy = listOf(MoodScore("happy",1f))
        assertTrue(PhotoRanker.rank(happy,listOf(photo("a","document"),photo("b").copy(labels=emptyList()))).isEmpty())
        val result=PhotoRanker.rank(happy,listOf(photo("flower","flowers"))).single()
        assertTrue(result.reasons.any { it.contains("flowers") })
        assertTrue(result.reasons.none { it.contains("butterfly") })
        assertTrue(PhotoRanker.mapping.values.flatten().all { it in PhotoScenePolicy.names || it in PhotoScenePolicy.broadNames })
    }
    @Test fun broadAtmosphereCanRankWithoutInventingAnObjectButUniformSupportCannot() {
        val calm = listOf(MoodScore("calm", 1f))
        val unnamed = photo("unnamed").copy(labels=emptyList())
        assertTrue(PhotoRanker.rank(calm,listOf(unnamed)).single().reasons.single().contains("peaceful"))
        assertTrue(PhotoRanker.rank(calm,listOf(unnamed.copy(atmospheres=emptyList()))).isEmpty())
        val uniform = unnamed.copy(atmospheres=PhotoAtmospherePolicy.names.keys.map { PhotoLabel(it,.25f) })
        assertTrue(PhotoRanker.rank(calm,listOf(uniform)).isEmpty())
        assertTrue(PhotoRanker.rank(calm,listOf(uniform.copy(atmospheres=listOf(PhotoLabel("peaceful",1f))))).isEmpty())
        assertTrue(PhotoRanker.rank(calm,listOf(uniform.copy(atmospheres=uniform.atmospheres.map { it.copy(confidence=Float.NaN) }))).isEmpty())
        val duplicated = calm + calm
        assertEquals(PhotoRanker.rank(calm,listOf(unnamed)),PhotoRanker.rank(duplicated,listOf(unnamed)))
        assertTrue(PhotoRanker.rank(listOf(MoodScore("nature",1f)),listOf(unnamed)).isEmpty())
    }
    @Test fun searchCapabilityDistinguishesExtensionAndUnknown() {
        assertTrue(PhotoSearchPolicy.explanation(36,17).contains("17"))
        assertTrue(PhotoSearchPolicy.explanation(36,17).contains("19"))
        assertFalse(PhotoSearchPolicy.explanation(36,19).contains("Updating this app"))
        assertTrue(PhotoSearchPolicy.explanation(32,null).contains("This Android version"))
        assertTrue(PhotoSearchPolicy.explanation(36,null).contains("Cannot read"))
        assertEquals(SystemPhotoSearchCapability.UNSUPPORTED,PhotoSearchPolicy.capability(32,99))
        assertEquals(SystemPhotoSearchCapability.UNKNOWN,PhotoSearchPolicy.capability(36,null))
        assertEquals(SystemPhotoSearchCapability.UNSUPPORTED,PhotoSearchPolicy.capability(36,18))
        assertEquals(SystemPhotoSearchCapability.MAY_REQUEST,PhotoSearchPolicy.capability(33,19))
        assertEquals(200,PhotoSearchPolicy.query("x".repeat(201)).length)
        assertEquals("lake",PhotoSearchPolicy.query(" lake "))
    }
    private fun atmosphere(id: String, peaceful: Float, reflective: Float) = photo(id, "butterfly").copy(
        atmospheres = listOf(PhotoLabel("lively", .1f), PhotoLabel("peaceful", peaceful),
            PhotoLabel("reflective", reflective), PhotoLabel("intense", .1f)))
    @Test fun relaxedSadMusicAndRelaxedPositiveMusicPreferDifferentAtmospheres() {
        val calm = atmosphere("calm", .55f, .25f)
        val sad = atmosphere("sad", .25f, .55f)
        val pool = listOf(calm, sad)
        val shared = listOf(MoodScore("relaxing", .15f), MoodScore("meditative", .06f))
        val sadMusic = shared + listOf(MoodScore("sad", .07f), MoodScore("melancholic", .06f))
        val calmMusic = shared + listOf(MoodScore("calm", .08f), MoodScore("happy", .08f), MoodScore("sad", .07f))
        assertEquals(listOf("sad"), PhotoRanker.rank(sadMusic, pool).map { it.features.photo.id })
        assertEquals(listOf("calm"), PhotoRanker.rank(calmMusic, pool).map { it.features.photo.id })
        // Both fixtures have the same object label: no butterfly blacklist or ID rule.
        assertEquals(PhotoRanker.rank(sadMusic, pool), PhotoRanker.rank(sadMusic + sadMusic, pool.reversed()))
    }
    @Test fun clearPreferenceDoesNotPadWithConflictingPicturesAndMixedMusicKeepsChoices() {
        val calm = atmosphere("calm", .45f, .35f)
        val sad = atmosphere("sad", .35f, .45f)
        assertTrue(PhotoRanker.rank(listOf(MoodScore("sad", 1f)), listOf(calm)).isEmpty())
        assertEquals(2, PhotoRanker.rank(listOf(MoodScore("calm", .5f), MoodScore("sad", .5f)), listOf(calm, sad)).size)
        val tied = atmosphere("tie", .4f, .4f)
        assertEquals("tie", PhotoRanker.rank(listOf(MoodScore("sad", 1f)), listOf(tied)).single().features.photo.id)
        assertEquals("calm", PhotoRanker.rank(listOf(MoodScore("relaxing", 1f)), listOf(calm, sad)).single().features.photo.id)
    }
}
