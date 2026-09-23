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
 * 정원을 초과해 접수될 수 있다 — 이 프로젝트 로드맵의 다음 단계(비관적 락 /
 * 낙관적 락 / Redis 원자적 차감)에서 해결할 문제를 의도적으로 재현한 버전.
 */
@Service
class RegistrationService(
    private val examSessionRepository: ExamSessionRepository,
    private val registrationRepository: RegistrationRepository,
) {
    @Transactional
    fun register(
        examSessionId: Long,
        userId: String,
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
            Registration(examSessionId = examSessionId, userId = userId),
        )
    }
}
