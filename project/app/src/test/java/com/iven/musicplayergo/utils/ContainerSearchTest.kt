package com.iven.musicplayergo.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ContainerSearchTest {
    @Test
    fun dottedCapitalIStillMatchesALowercaseSubstring() {
        val names = listOf("Other", "İstanbul", "istanbul")

        assertEquals(listOf("İstanbul", "istanbul"), Lists.processQueryForStringsLists("ist", names))
    }

    @Test
    fun greekSigmaMatchesRegardlessOfWordFinalCasing() {
        val names = listOf("Other", "ΟΣ", "οσ", "ος")

        assertEquals(listOf("ΟΣ", "οσ", "ος"), Lists.processQueryForStringsLists("οσ", names))
    }

    @Test
    fun mixedCaseSubstringMatchesKeepTheInputOrderAndMetadata() {
        val names = listOf("Night Drive", "Morning", "MIDNIGHT", "night train")

        assertEquals(
            listOf("Night Drive", "MIDNIGHT", "night train"),
            Lists.processQueryForStringsLists("nIgHt", names)
        )
        assertEquals(listOf("Night Drive", "Morning", "MIDNIGHT", "night train"), names)
    }

    @Test
    fun clearingTheQueryRestoresEveryNameIncludingEmptyNames() {
        val names = listOf("B", "", "A")

        assertEquals(names, Lists.processQueryForStringsLists("", names))
    }

    @Test
    fun missingOrEmptyListsAndUnmatchedQueriesReturnNoMatches() {
        assertEquals(emptyList<String>(), Lists.processQueryForStringsLists("night", null))
        assertEquals(emptyList<String>(), Lists.processQueryForStringsLists("night", emptyList()))
        assertEquals(emptyList<String>(), Lists.processQueryForStringsLists("night", listOf("Morning")))
    }
}
