package com.iven.musicplayergo.photos

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PhotoRecommendationWorkTest {
    private class Resource(private val closes: AtomicInteger) : PhotoFeatureExtractor {
        override suspend fun extract(photo: ScopedPhoto): PhotoFeatures = error("not needed")
        override fun close() { closes.incrementAndGet() }
    }
    @Test fun cancellationDuringBlockingConstructionStillClosesTheReturnedResource() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val closes = AtomicInteger()
        val task = launch(Dispatchers.Default) {
            withPhotoExtractor({ entered.countDown(); check(release.await(5,TimeUnit.SECONDS)); Resource(closes) }) {
                fail("Cancelled constructor must not enter inference")
            }
        }
        try { assertTrue(entered.await(5,TimeUnit.SECONDS)); task.cancel() }
        finally { release.countDown(); task.join() }
        assertEquals(1,closes.get())
    }
    @Test fun cancellationDuringInferenceClosesExactlyOnce() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val closes = AtomicInteger()
        val task = launch { withPhotoExtractor({ Resource(closes) }) { entered.complete(Unit); awaitCancellation() } }
        withTimeout(5_000) { entered.await() }
        task.cancelAndJoin()
        assertEquals(1,closes.get())
    }
    @Test fun pagesCannotOverlapAndCancelledReservationCanBeRetried() {
        val pages = PhotoPageCursor(12)
        assertEquals(0..4,pages.begin()); assertNull(pages.begin())
        pages.finish(true)
        assertEquals(5..9,pages.begin()); pages.finish(false)
        assertEquals(5..9,pages.begin()); pages.finish(true)
        assertEquals(10..11,pages.begin()); assertNull(pages.begin()); pages.finish(true)
        assertNull(pages.begin()); assertEquals(12,pages.shown)
    }
}
