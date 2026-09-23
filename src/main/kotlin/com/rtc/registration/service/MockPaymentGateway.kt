package com.rtc.registration.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * 실제 PG 연동을 흉내내는 가짜 결제 게이트웨이. 네트워크 왕복을 시뮬레이션하는
 * 지연과, 설정 가능한 확률의 실패를 인위적으로 주입한다 — [PaymentService]가
 * 이 결과에 따라 접수를 확정하거나 좌석을 되돌린다.
 */
@Component
class MockPaymentGateway(
    @param:Value("\${payment.mock.failure-rate:0.2}") private val failureRate: Double,
    @param:Value("\${payment.mock.min-delay-ms:20}") private val minDelayMs: Long,
    @param:Value("\${payment.mock.max-delay-ms:150}") private val maxDelayMs: Long,
) {
    fun charge(amount: Long): PaymentResult {
        Thread.sleep(Random.nextLong(minDelayMs, maxDelayMs + 1))
        return if (Random.nextDouble() < failureRate) PaymentResult.FAILED else PaymentResult.SUCCEEDED
    }
}
