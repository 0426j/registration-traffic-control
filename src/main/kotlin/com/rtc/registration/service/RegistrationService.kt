package com.rtc.registration.service

import com.rtc.registration.domain.Registration

/**
 * 접수 서비스 인터페이스. 좌석 차감 방식(동시성 제어 전략)별로 구현체가 갈린다:
 * - [NoLockRegistrationService] ("none"): 락 없음, 정원 초과 재현용 baseline
 * - [PessimisticLockRegistrationService] ("pessimistic"): SELECT ... FOR UPDATE
 * - [OptimisticLockRegistrationService] ("optimistic"): compare-and-set + 재시도
 * - [RedisAtomicRegistrationService] ("redis"): Redisson AtomicLong 원자적 차감
 *
 * [com.rtc.registration.web.RegistrationController]가 `strategy` 파라미터로
 * 빈 이름을 골라 위임한다.
 */
interface RegistrationService {
    fun register(
        examSessionId: Long,
        userId: String,
    ): Registration
}
