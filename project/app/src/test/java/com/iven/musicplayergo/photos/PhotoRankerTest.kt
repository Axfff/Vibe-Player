package com.iven.musicplayergo.photos

import com.iven.musicplayergo.analysis.MoodScore
import org.junit.Assert.*
import org.junit.Test

class PhotoRankerTest {
    private fun photo(id: String, label: String = "lakeside", score: Float = .8f, hash: String = id) =
        PhotoFeatures(ScopedPhoto(id, "content://test/$id"), hash, "fixture-model", listOf(PhotoLabel(label, score)),
            PhotoAtmospherePolicy.names.keys.map { PhotoLabel(it, if (it == if (label == "flowers") "lively" else "peaceful") score else (1f-score)/3f) })
    @Test fun formulaUsesIndependentMusicAndPhotoScores() {
        val result = PhotoRanker.rank(listOf(MoodScore("calm", .8f), MoodScore("happy", .2f)), listOf(photo("a"))).single()
        assertEquals(.6533333f, result.score, .00001f); assertTrue(result.reasons.first().contains("舒缓")); assertEquals(PhotoRanker.VERSION, result.ruleVersion)
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
        assertEquals(listOf("树林","湖面","山谷"),PhotoRanker.suggestions(listOf(MoodScore("calm",1f))))
    }
    @Test fun unrelatedAndUncertainScenesDoNotBecomeMoodRecommendations() {
        val happy = listOf(MoodScore("happy",1f))
        assertTrue(PhotoRanker.rank(happy,listOf(photo("a","document"),photo("b").copy(labels=emptyList()))).isEmpty())
        val result=PhotoRanker.rank(happy,listOf(photo("flower","flowers"))).single()
        assertTrue(result.reasons.any { it.contains("花卉") })
        assertTrue(result.reasons.none { it.contains("蝴蝶") })
        assertTrue(PhotoRanker.mapping.values.flatten().all { it in PhotoScenePolicy.names || it in PhotoScenePolicy.broadNames })
    }
    @Test fun broadAtmosphereCanRankWithoutInventingAnObjectButUniformSupportCannot() {
        val calm = listOf(MoodScore("calm", 1f))
        val unnamed = photo("unnamed").copy(labels=emptyList())
        assertTrue(PhotoRanker.rank(calm,listOf(unnamed)).single().reasons.single().contains("宁静舒缓"))
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
