package com.iven.musicplayergo.library

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFolderNameTest {
    @Test
    fun missingPathUsesRootLabelAndDoesNotAbortFollowingRows() {
        val paths = listOf(null, "/storage/emulated/0/Music/song.mp3")

        assertEquals(listOf("Root", "Music"), paths.map { legacyMediaFolderName(it, "Root") })
    }

    @Test
    fun nestedAudioKeepsItsImmediateFolderName() {
        assertEquals("Live", legacyMediaFolderName("/storage/emulated/0/Music/Live/song.mp3", "Root"))
        assertEquals("Music", legacyMediaFolderName("/storage/1234-5678/Music/song.mp3", "Root"))
    }

    @Test
    fun emulatedStorageRootKeepsTheRootLabel() {
        assertEquals("Root", legacyMediaFolderName("/storage/emulated/0/song.mp3", "Root"))
    }

    @Test
    fun pathsWithoutAParentKeepTheRootLabel() {
        assertEquals("Root", legacyMediaFolderName("song.mp3", "Root"))
        assertEquals("Root", legacyMediaFolderName("", "Root"))
    }
}
