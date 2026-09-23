package com.rtc.registration.service

import com.rtc.registration.domain.Registration
import com.rtc.registration.repository.ExamSessionRepository
import com.rtc.registration.repository.RegistrationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * 락을 걸지 않고 읽은 뒤, seatsRemaining이 읽은 값과 여전히 같을 때만 갱신하는
 * compare-and-set(CAS)으로 좌석을 차감한다. 다른 트랜잭션이 먼저 갱신해 CAS가
 * 실패하면(영향받은 행 0개) 재시도한다.
 *
 * 재시도마다 [TransactionTemplate]로 새 트랜잭션을 여는 이유: 같은 트랜잭션/영속성
 * 컨텍스트 안에서 `findById`를 반복하면 1차 캐시가 이전에 읽은(오래된) 엔티티를
 * 그대로 돌려줘서 재시도가 매번 같은 값으로 실패한다. `@Transactional` 메서드를
 * 같은 클래스 안에서 self-invocation으로 호출하면 AOP 프록시가 적용되지 않는
 * 문제도 있어, 애노테이션 대신 프로그래밍 방식 트랜잭션을 쓴다.
 */
@Service("optimistic")
class OptimisticLockRegistrationService(
    private val examSessionRepository: ExamSessionRepository,
    private val registrationRepository: RegistrationRepository,
    transactionManager: PlatformTransactionManager,
) : RegistrationService {
    private val newTransaction =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionTemplate.PROPAGATION_REQUIRES_NEW
        }

    override fun register(
        examSessionId: Long,
        userId: String,
    ): Registration {
        repeat(MAX_ATTEMPTS) {
            val registration =
                newTransaction.execute {
                    val session =
                        examSessionRepository
                            .findById(examSessionId)
                            .orElseThrow { ExamSessionNotFoundException(examSessionId) }

                    if (session.seatsRemaining <= 0) {
                        throw NoSeatsRemainingException(examSessionId)
                    }

                    val updatedRows =
                        examSessionRepository.compareAndSetSeatsRemaining(
                            examSessionId,
                            session.seatsRemaining,
                            session.seatsRemaining - 1,
                        )

                    if (updatedRows == 1) {
                        registrationRepository.save(Registration(examSessionId = examSessionId, userId = userId))
                    } else {
                        null
                    }
                }
            if (registration != null) return registration
        }
        throw OptimisticLockRetryExhaustedException(examSessionId)
    }

    companion object {
        private const val MAX_ATTEMPTS = 30
    }
}
