package com.rtc.registration.service

import com.rtc.registration.domain.Registration
import com.rtc.registration.domain.RegistrationStatus
import com.rtc.registration.repository.RegistrationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * 접수(좌석 확보) 이후의 결제 단계. [MockPaymentGateway] 호출은 트랜잭션 밖에서
 * 실행한다 — 외부 API 왕복(지연 포함) 동안 DB 커넥션/락을 쥐고 있지 않기 위함.
 * 결제 실패 시 해당 접수가 썼던 전략([Registration.strategy])의 `release()`로
 * 좌석을 되돌린다.
 *
 * `compareAndSetStatus`는 `@Modifying` 쿼리라 활성 트랜잭션이 필요한데, 이 클래스
 * 안에서 `@Transactional` 메서드를 self-invocation으로 호출하면 AOP 프록시가
 * 안 걸려 트랜잭션이 안 열린다(`OptimisticLockRegistrationService`와 같은 이유) —
 * 그래서 [TransactionTemplate]로 그 구간만 명시적으로 감싼다.
 */
@Service
class PaymentService(
    private val registrationRepository: RegistrationRepository,
    private val registrationServices: Map<String, RegistrationService>,
    private val mockPaymentGateway: MockPaymentGateway,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    fun pay(
        registrationId: Long,
        amount: Long,
    ): Registration {
        val registration = findRegistration(registrationId)
        if (registration.status != RegistrationStatus.PENDING_PAYMENT) {
            return registration
        }

        val result = mockPaymentGateway.charge(amount)
        return applyResult(registrationId, result)
    }

    private fun applyResult(
        registrationId: Long,
        result: PaymentResult,
    ): Registration {
        val newStatus = if (result == PaymentResult.SUCCEEDED) RegistrationStatus.CONFIRMED else RegistrationStatus.FAILED

        val updatedRows =
            transactionTemplate.execute {
                registrationRepository.compareAndSetStatus(registrationId, RegistrationStatus.PENDING_PAYMENT, newStatus)
            } ?: 0

        if (updatedRows == 1 && newStatus == RegistrationStatus.FAILED) {
            val registration = findRegistration(registrationId)
            registrationServices[registration.strategy]?.release(registration.examSessionId)
        }

        return findRegistration(registrationId)
    }

    private fun findRegistration(registrationId: Long): Registration =
        registrationRepository.findById(registrationId).orElseThrow { RegistrationNotFoundException(registrationId) }
}
