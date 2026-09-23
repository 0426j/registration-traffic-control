package com.rtc.registration

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * [count]개의 [action]을 최대한 같은 순간에 동시 실행되도록 쏜다. 각 스레드는
 * 준비(ready)를 알린 뒤 공용 신호(start)를 기다렸다가 한꺼번에 출발한다.
 */
fun fireConcurrentRequests(
    count: Int,
    timeoutSeconds: Long = 30,
    action: (index: Int) -> Unit,
) {
    val readyLatch = CountDownLatch(count)
    val startLatch = CountDownLatch(1)
    val doneLatch = CountDownLatch(count)
    val executor = Executors.newFixedThreadPool(count)

    repeat(count) { i ->
        executor.submit {
            readyLatch.countDown()
            startLatch.await()
            try {
                action(i)
            } finally {
                doneLatch.countDown()
            }
        }
    }

    readyLatch.await(10, TimeUnit.SECONDS)
    startLatch.countDown()
    doneLatch.await(timeoutSeconds, TimeUnit.SECONDS)
    executor.shutdown()
}
