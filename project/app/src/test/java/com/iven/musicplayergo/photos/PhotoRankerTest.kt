package com.iven.musicplayergo.photos

import com.iven.musicplayergo.analysis.MoodScore
import org.junit.Assert.*
import org.junit.Test

class PhotoRankerTest {
    private fun photo(id: String, label: String = "lakeside", score: Float = .8f, hash: String = id) =
        PhotoFeatures(ScopedPhoto(id, "content://test/$id"), hash, "fixture-model", listOf(PhotoLabel(label, score)))
    @Test fun formulaUsesIndependentMusicAndPhotoScores() {
        val result = PhotoRanker.rank(listOf(MoodScore("calm", .8f), MoodScore("happy", .2f)), listOf(photo("a"))).single()
        assertEquals(.64f, result.score, .00001f); assertTrue(result.reasons.single().contains("舒缓")); assertEquals(PhotoRanker.VERSION, result.ruleVersion)
    }
    @Test fun tiesAreStableAndDefaultIsFive() {
        val candidates = (10 downTo 1).map { photo(it.toString().padStart(2,'0')) }
        assertEquals(listOf("01","02","03","04","05"), PhotoRanker.rank(listOf(MoodScore("calm",1f)), candidates).map { it.features.photo.id })
    }
    @Test fun duplicateContentsAreNotRepeated() {
        assertEquals(1, PhotoRanker.rank(listOf(MoodScore("calm",1f)), listOf(photo("a",hash="same"),photo("b",hash="same"))).size)
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
        assertEquals(1,result.reasons.size);assertFalse(result.reasons.single().contains("unmapped"))
    }
    @Test fun suggestionsComeOnlyFromExplicitSignals() {
        assertTrue(PhotoRanker.suggestions(emptyList()).isEmpty())
        assertEquals(listOf("树林","湖面","山谷"),PhotoRanker.suggestions(listOf(MoodScore("calm",1f))))
    }
    @Test fun unrelatedAndUncertainScenesDoNotBecomeMoodRecommendations() {
        val happy = listOf(MoodScore("happy",1f))
        assertTrue(PhotoRanker.rank(happy,listOf(photo("a","document"),photo("b").copy(labels=emptyList()))).isEmpty())
        val result=PhotoRanker.rank(happy,listOf(photo("flower","flowers"))).single()
        assertTrue(result.reasons.single().contains("花卉"))
        assertFalse(result.reasons.single().contains("蝴蝶"))
        assertTrue(PhotoRanker.mapping.values.flatten().all { it in PhotoScenePolicy.names || it in PhotoScenePolicy.broadNames })
    }
    @Test fun searchCapabilityDistinguishesExtensionAndUnknown() {
        assertTrue(PhotoSearchPolicy.explanation(36,17).contains("17"))
        assertTrue(PhotoSearchPolicy.explanation(36,17).contains("19"))
        assertFalse(PhotoSearchPolicy.explanation(36,19).contains("更新 App"))
        assertTrue(PhotoSearchPolicy.explanation(32,null).contains("此 Android 版本"))
        assertTrue(PhotoSearchPolicy.explanation(36,null).contains("无法读取"))
        assertEquals(SystemPhotoSearchCapability.UNSUPPORTED,PhotoSearchPolicy.capability(32,99))
        assertEquals(SystemPhotoSearchCapability.UNKNOWN,PhotoSearchPolicy.capability(36,null))
        assertEquals(SystemPhotoSearchCapability.UNSUPPORTED,PhotoSearchPolicy.capability(36,18))
        assertEquals(SystemPhotoSearchCapability.MAY_REQUEST,PhotoSearchPolicy.capability(33,19))
        assertEquals(200,PhotoSearchPolicy.query("x".repeat(201)).length)
        assertEquals("lake",PhotoSearchPolicy.query(" lake "))
    }
}
