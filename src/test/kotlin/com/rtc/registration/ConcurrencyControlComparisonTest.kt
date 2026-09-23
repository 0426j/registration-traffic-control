package com.rtc.registration

import com.rtc.registration.config.FlywayContextInitializer
import com.rtc.registration.repository.RegistrationRepository
import com.rtc.registration.web.ExamSessionController
import com.rtc.registration.web.RegistrationController
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.time.measureTime

/**
 * [OverbookingReproductionTest]에서 재현한 정원 초과 버그를 세 가지 동시성
 * 제어 전략(pessimistic/optimistic/redis)이 실제로 고쳤는지 검증한다.
 *
 * 각 전략에 대해 좌석 10석짜리 회차에 50개 동시 요청을 쏘고, 성공한 접수 수가
 * 정확히 정원(10)과 같아야 한다 — `none`과 달리 초과도, 부족도 없어야 통과.
 * 소요 시간도 함께 출력해 전략별 성능 감을 잡는다(정식 부하테스트는 별도
 * 로드맵 항목에서 nGrinder/k6로 다룬다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [FlywayContextInitializer::class])
class ConcurrencyControlComparisonTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var registrationRepository: RegistrationRepository

    @ParameterizedTest(name = "strategy={0}")
    @ValueSource(strings = ["pessimistic", "optimistic", "redis"])
    fun `each locking strategy allows exactly capacity registrations`(strategy: String) {
        val capacity = 10
        val concurrentRequests = 50
        val client = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()

        val examSessionId =
            client
                .post()
                .uri("/api/exam-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ExamSessionController.CreateExamSessionRequest(name = "comparison-$strategy", capacity = capacity))
                .exchange()
                .expectBody(ExamSessionController.ExamSessionResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        val elapsed =
            measureTime {
                fireConcurrentRequests(concurrentRequests) { i ->
                    client
                        .post()
                        .uri("/api/exam-sessions/$examSessionId/registrations?strategy=$strategy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(RegistrationController.RegisterRequest(userId = "user-$i", idempotencyKey = "$examSessionId-user-$i"))
                        .exchange()
                }
            }
        println("[$strategy] $concurrentRequests concurrent requests took $elapsed")

        val successfulRegistrations = registrationRepository.countByExamSessionId(examSessionId)

        assertThat(successfulRegistrations)
            .describedAs("전략 '%s'는 정원(%d)을 초과하거나 부족하게 허용하면 안 된다", strategy, capacity)
            .isEqualTo(capacity.toLong())
    }
}
