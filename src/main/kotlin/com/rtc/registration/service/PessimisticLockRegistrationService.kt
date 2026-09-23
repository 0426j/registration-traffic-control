package com.rtc.registration.service

import com.rtc.registration.domain.Registration
import com.rtc.registration.repository.ExamSessionRepository
import com.rtc.registration.repository.RegistrationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * `SELECT ... FOR UPDATE`로 exam_session 행을 잠그고 좌석을 차감한다.
 * 동시 요청은 DB 행 락에서 순차적으로 대기하게 되어 정원을 초과하지 않는다.
 */
@Service("pessimistic")
class PessimisticLockRegistrationService(
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
            examSessionRepository.findByIdForUpdate(examSessionId)
                ?: throw ExamSessionNotFoundException(examSessionId)

        if (session.seatsRemaining <= 0) {
            throw NoSeatsRemainingException(examSessionId)
        }

        session.seatsRemaining -= 1
        examSessionRepository.save(session)

        return registrationRepository.save(
            Registration(
                examSessionId = examSessionId,
                userId = userId,
                idempotencyKey = idempotencyKey,
                strategy = "pessimistic",
            ),
        )
    }

    @Transactional
    override fun release(examSessionId: Long) {
        val session =
            examSessionRepository.findByIdForUpdate(examSessionId)
                ?: throw ExamSessionNotFoundException(examSessionId)
        session.seatsRemaining += 1
        examSessionRepository.save(session)
    }
}
