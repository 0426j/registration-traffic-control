package com.rtc.registration.service

import com.rtc.registration.domain.Registration
import com.rtc.registration.repository.RegistrationRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

/**
 * `strategy` 이름으로 [RegistrationService] 구현체를 골라 위임하되, 그 전에
 * 같은 idempotencyKey로 이미 처리된 요청이 있으면 새로 좌석을 소모하지 않고
 * 기존 결과를 그대로 돌려준다. 두 요청이 동시에 같은 키로 들어와 사전 조회를
 * 둘 다 통과하더라도, DB의 유일 인덱스가 한쪽만 커밋을 허용하므로
 * [DataIntegrityViolationException]을 잡아 승자의 결과를 재조회해 반환한다.
 */
@Service
class IdempotentRegistrationDispatcher(
    private val registrationServices: Map<String, RegistrationService>,
    private val registrationRepository: RegistrationRepository,
    private val meterRegistry: MeterRegistry,
) {
    fun register(
        strategy: String,
        examSessionId: Long,
        userId: String,
        idempotencyKey: String,
    ): Registration {
        val sample = Timer.start(meterRegistry)
        var result = "success"
        try {
            registrationRepository.findByIdempotencyKey(idempotencyKey)?.let {
                result = "duplicate"
                return it
            }

            val registrationService = registrationServices[strategy] ?: throw UnknownRegistrationStrategyException(strategy)

            return try {
                registrationService.register(examSessionId, userId, idempotencyKey)
            } catch (ex: DataIntegrityViolationException) {
                result = "duplicate"
                registrationRepository.findByIdempotencyKey(idempotencyKey) ?: throw ex
            }
        } catch (ex: NoSeatsRemainingException) {
            result = "no_seats"
            throw ex
        } catch (ex: OptimisticLockRetryExhaustedException) {
            result = "retry_exhausted"
            throw ex
        } catch (ex: RuntimeException) {
            result = "error"
            throw ex
        } finally {
            sample.stop(
                Timer
                    .builder("registration.duration")
                    .description("접수 처리 소요시간(멱등 조회 포함)")
                    .tag("strategy", strategy)
                    .tag("result", result)
                    .publishPercentileHistogram()
                    .register(meterRegistry),
            )
        }
    }
}
