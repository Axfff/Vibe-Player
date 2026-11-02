package com.iven.musicplayergo.regression

import android.view.inspector.WindowInspector
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iven.musicplayergo.dialogs.QueueAdapter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real adapter removals must persist and clean up independently of confirmation settings. */
@RunWith(AndroidJUnit4::class)
class QueueDeletionRegressionTest {
    @Test fun immediateRemovalPersistsRemainingAndEmptyQueue() {
        RegressionFixture.open().use { f ->
            val adapter = prepareQueue(f, false, "B", "C")
            var cleared = 0
            f.onMain { adapter.onQueueCleared = { cleared++ } }

            f.onActivity { assertTrue(adapter.performQueueSongDeletion(it, 0)) }
            f.onMain {
                assertEquals(listOf(f.tracks.getValue("C")), f.holder.queueSongs)
                assertEquals("Removal must update saved queue immediately", f.holder.queueSongs, f.preferences.queue)
                assertEquals(1, adapter.itemCount)
                assertEquals(0, cleared)
                assertNotNull(f.holder.isQueue)
            }

            f.onActivity { assertTrue(adapter.performQueueSongDeletion(it, 0)) }
            f.onMain {
                assertTrue(f.holder.queueSongs.isEmpty())
                assertTrue("An old saved queue must not survive the last removal", f.preferences.queue?.isEmpty() == true)
                assertEquals(0, adapter.itemCount)
                assertNull(f.holder.isQueue)
                assertEquals("Empty queue dismisses its sheet once", 1, cleared)
            }
        }
    }

    @Test fun currentQueuedSongCannotBeRemovedWithEitherConfirmationSetting() {
        RegressionFixture.open().use { f ->
            val adapter = prepareQueue(f, false, "A", "B")
            var cleared = 0
            f.onMain {
                adapter.onQueueCleared = { cleared++ }
                f.holder.isQueueStarted = true
            }
            listOf(false, true).forEach { ask ->
                f.onMain { f.preferences.isAskForRemoval = ask }
                f.onActivity { assertFalse(adapter.performQueueSongDeletion(it, 0)) }
                f.onMain {
                    assertSame(f.holder.currentSong, f.holder.queueSongs.first())
                    assertEquals(listOf(f.holder.currentSong!!, f.tracks.getValue("B")), f.holder.queueSongs)
                    assertEquals(f.holder.queueSongs, f.preferences.queue)
                    assertEquals(2, adapter.itemCount)
                    assertNotNull(f.holder.isQueue)
                    assertEquals(0, cleared)
                }
            }
        }
    }

    @Test fun confirmationCancellationPreservesQueueAndApprovalClearsIt() {
        RegressionFixture.open().use { f ->
            val adapter = prepareQueue(f, true, "B")
            var cleared = 0
            f.onMain { adapter.onQueueCleared = { cleared++ } }

            f.onActivity { assertTrue(adapter.performQueueSongDeletion(it, 0)) }
            clickDialogButton(f, android.R.id.button2)
            f.onMain {
                assertEquals(listOf(f.tracks.getValue("B")), f.holder.queueSongs)
                assertEquals(f.holder.queueSongs, f.preferences.queue)
                assertEquals(1, adapter.itemCount)
                assertEquals(0, cleared)
            }

            f.onActivity { assertTrue(adapter.performQueueSongDeletion(it, 0)) }
            clickDialogButton(f, android.R.id.button1)
            f.onMain {
                assertTrue(f.holder.queueSongs.isEmpty())
                assertTrue(f.preferences.queue?.isEmpty() == true)
                assertNull(f.holder.isQueue)
                assertEquals(0, adapter.itemCount)
                assertEquals(1, cleared)
            }
        }
    }

    private fun prepareQueue(f: RegressionFixture, ask: Boolean, vararg labels: String): QueueAdapter {
        f.play("A")
        f.expectPlaying("A")
        return f.onMain {
            f.holder.pauseMediaPlayer()
            val current = checkNotNull(f.holder.currentSong)
            f.holder.queueSongs.clear()
            // Playback adds launch metadata; queue the actual playing object for its guard.
            f.holder.queueSongs.addAll(labels.map {
                f.tracks.getValue(it).let { track -> if (track.id == current.id) current else track }
            })
            f.holder.isQueue = current
            f.preferences.queue = f.holder.queueSongs
            f.preferences.isAskForRemoval = ask
            QueueAdapter()
        }
    }

    private fun clickDialogButton(f: RegressionFixture, id: Int) {
        fun button(): Button? = WindowInspector.getGlobalWindowViews().asReversed()
            .mapNotNull { it.findViewById<Button>(id) }.firstOrNull { it.isShown }
        f.await("queue removal confirmation button") { f.onMain { button() != null } }
        f.onMain { assertTrue(button()!!.performClick()) }
        f.await("queue removal confirmation dismissed") { f.onMain { button() == null } }
    }
}
