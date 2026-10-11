package com.openpets.android.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BoundedWriteQueueTest {
    @Test
    fun rejectsOverflowAndShutdownStopsAccepting() {
        val queue = BoundedWriteQueue(capacity = 1, threadName = "bounded-write-test")
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val ran = AtomicInteger()

        assertTrue(queue.submit(Runnable {
            workerStarted.countDown()
            releaseWorker.await()
            ran.incrementAndGet()
        }))
        assertTrue(workerStarted.await(5, TimeUnit.SECONDS))
        assertTrue(queue.submit(Runnable { ran.incrementAndGet() }))
        assertFalse(queue.submit(Runnable { ran.incrementAndGet() }))
        assertFalse(queue.submit(Runnable { ran.incrementAndGet() }))
        assertTrue(queue.dropped > 0)

        releaseWorker.countDown()
        queue.shutdown()
        assertTrue(queue.awaitTermination(5, TimeUnit.SECONDS))
        assertFalse(queue.submit(Runnable { ran.incrementAndGet() }))
        assertEquals(2, ran.get())
    }
}
