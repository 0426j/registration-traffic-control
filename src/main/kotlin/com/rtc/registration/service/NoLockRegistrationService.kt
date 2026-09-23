package com.rtc.registration.service

import com.rtc.registration.domain.Registration
import com.rtc.registration.repository.ExamSessionRepository
import com.rtc.registration.repository.RegistrationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 락 없는(read-then-write) 접수 서비스.
 *
 * 동시 요청 하에서 seatsRemaining 읽기와 쓰기 사이에 경쟁 조건이 있어
 * 정원을 초과해 접수될 수 있다 — [OverbookingReproductionTest][com.rtc.registration.OverbookingReproductionTest]로
 * 재현한 문제의 baseline. 의도적으로 고치지 않고 남겨둔다 (다른 전략과의 비교 대상).
 */
@Service("none")
class NoLockRegistrationService(
    private val examSessionRepository: ExamSessionRepository,
    private val registrationRepository: RegistrationRepository,
) : RegistrationService {
    @Transactional
    override fun register(
        examSessionId: Long,
        userId: String,
        idempotencyKey: String,
    ): Registration {
        val session =
            examSessionRepository
                .findById(examSessionId)
                .orElseThrow { ExamSessionNotFoundException(examSessionId) }

        if (session.seatsRemaining <= 0) {
            throw NoSeatsRemainingException(examSessionId)
        }

        session.seatsRemaining -= 1
        examSessionRepository.save(session)

        return registrationRepository.save(
            Registration(examSessionId = examSessionId, userId = userId, idempotencyKey = idempotencyKey, strategy = "none"),
        )
    }

    @Transactional
    override fun release(examSessionId: Long) {
        val session =
            examSessionRepository
                .findById(examSessionId)
                .orElseThrow { ExamSessionNotFoundException(examSessionId) }
        session.seatsRemaining += 1
        examSessionRepository.save(session)
    }
}
