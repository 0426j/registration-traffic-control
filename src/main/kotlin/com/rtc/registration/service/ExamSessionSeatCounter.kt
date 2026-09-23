package com.rtc.registration.service

import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component

/**
 * [RedisAtomicRegistrationService]가 쓰는 Redis 좌석 카운터의 키 규칙과 초기화.
 * exam_session 생성 시점에 [initialize]를 호출해 카운터를 좌석 수만큼 세팅한다.
 */
@Component
class ExamSessionSeatCounter(
    private val redissonClient: RedissonClient,
) {
    fun initialize(
        examSessionId: Long,
        capacity: Int,
    ) {
        redissonClient.getAtomicLong(key(examSessionId)).set(capacity.toLong())
    }

    companion object {
        fun key(examSessionId: Long) = "exam-session:$examSessionId:seats-remaining"
    }
}
