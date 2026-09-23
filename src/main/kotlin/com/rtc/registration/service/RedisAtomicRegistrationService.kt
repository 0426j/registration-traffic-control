package com.rtc.registration.service

import com.rtc.registration.domain.Registration
import com.rtc.registration.repository.RegistrationRepository
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Redis(Redisson AtomicLong)의 원자적 decrement-then-check로 좌석을 차감한다.
 * DB 락 없이도 동시 요청이 안전하게 직렬화되며, [ExamSessionSeatCounter]가
 * exam_session 생성 시 카운터를 초기화해둔다.
 *
 * decrementAndGet()이 음수가 되면(정원 초과) 즉시 incrementAndGet()으로 되돌리는
 * "보정" 패턴 — DECR 자체가 원자적이라 두 요청이 동시에 마지막 좌석을 두고
 * 경쟁해도 정확히 한쪽만 성공한다.
 */
@Service("redis")
class RedisAtomicRegistrationService(
    private val redissonClient: RedissonClient,
    private val registrationRepository: RegistrationRepository,
) : RegistrationService {
    @Transactional
    override fun register(
        examSessionId: Long,
        userId: String,
    ): Registration {
        val seatCounter = redissonClient.getAtomicLong(ExamSessionSeatCounter.key(examSessionId))
        val remaining = seatCounter.decrementAndGet()

        if (remaining < 0) {
            seatCounter.incrementAndGet()
            throw NoSeatsRemainingException(examSessionId)
        }

        return registrationRepository.save(
            Registration(examSessionId = examSessionId, userId = userId),
        )
    }
}
