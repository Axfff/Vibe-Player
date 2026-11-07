package com.iven.musicplayergo.photos

import com.iven.musicplayergo.analysis.MoodScore
import org.junit.Assert.*
import org.junit.Test

class PhotoRankerTest {
    private fun photo(id: String, label: String = "lakeside", score: Float = .8f, hash: String = id) =
        PhotoFeatures(ScopedPhoto(id, "content://test/$id"), hash, "fixture-model", listOf(PhotoLabel(label, score)))
    @Test fun formulaUsesIndependentMusicAndPhotoScores() {
        val result = PhotoRanker.rank(listOf(MoodScore("calm", .8f), MoodScore("happy", .2f)), listOf(photo("a"))).single()
        assertEquals(.64f, result.score, .00001f); assertTrue(result.reasons.single().contains("calm")); assertEquals(PhotoRanker.VERSION, result.ruleVersion)
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
        assertEquals(listOf("lakeside","valley"),PhotoRanker.suggestions(listOf(MoodScore("calm",1f))))
    }
    @Test fun softmaxIsNumericallyStableAndRejectsBadModelOutput() {
        val logits=FloatArray(1000){10000f};val probabilities=PhotoRanker.softmax(logits)
        assertEquals(.001f,probabilities[0],.00001f)
        assertThrows(IllegalArgumentException::class.java){PhotoRanker.softmax(floatArrayOf(1f))}
        logits[1]=Float.NaN;assertThrows(IllegalArgumentException::class.java){PhotoRanker.softmax(logits)}
    }
    @Test fun searchCapabilityDistinguishesExtensionAndUnknown() {
        assertEquals(SystemPhotoSearchCapability.UNSUPPORTED,PhotoSearchPolicy.capability(32,99))
        assertEquals(SystemPhotoSearchCapability.UNKNOWN,PhotoSearchPolicy.capability(36,null))
        assertEquals(SystemPhotoSearchCapability.UNSUPPORTED,PhotoSearchPolicy.capability(36,18))
        assertEquals(SystemPhotoSearchCapability.MAY_REQUEST,PhotoSearchPolicy.capability(33,19))
        assertEquals(200,PhotoSearchPolicy.query("x".repeat(201)).length)
        assertEquals("lake",PhotoSearchPolicy.query(" lake "))
    }
}
