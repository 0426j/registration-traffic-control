package com.rtc.registration.queue

import io.micrometer.core.instrument.MeterRegistry
import org.redisson.api.RedissonClient
import org.redisson.api.StreamMessageId
import org.redisson.api.stream.StreamAddArgs
import org.springframework.stereotype.Component

/**
 * 접수 요청을 Redis Stream에 적재만 하고 바로 응답한다 — 실제 좌석 차감/등록은
 * [RegistrationRequestConsumer]가 뒤에서 순차 처리한다. 폭주 트래픽을 큐가
 * 흡수해서 API 응답 자체는 부하와 무관하게 빨라야 한다는 게 이 전략의 핵심.
 */
@Component
class RegistrationRequestProducer(
    private val redissonClient: RedissonClient,
    private val meterRegistry: MeterRegistry,
) {
    fun enqueue(
        examSessionId: Long,
        userId: String,
        idempotencyKey: String,
    ): StreamMessageId {
        val stream = redissonClient.getStream<String, String>(STREAM_KEY)
        meterRegistry.counter("registration.queue.enqueued").increment()
        return stream.add(
            StreamAddArgs.entries(
                mapOf(
                    FIELD_EXAM_SESSION_ID to examSessionId.toString(),
                    FIELD_USER_ID to userId,
                    FIELD_IDEMPOTENCY_KEY to idempotencyKey,
                ),
            ),
        )
    }

    companion object {
        const val STREAM_KEY = "registration-queue"
        const val GROUP_NAME = "registration-workers"
        const val CONSUMER_NAME = "worker-1"
        const val FIELD_EXAM_SESSION_ID = "examSessionId"
        const val FIELD_USER_ID = "userId"
        const val FIELD_IDEMPOTENCY_KEY = "idempotencyKey"
    }
}
