package com.rtc.registration

import com.rtc.registration.config.FlywayContextInitializer
import com.rtc.registration.repository.RegistrationRepository
import com.rtc.registration.web.ExamSessionController
import com.rtc.registration.web.RegistrationController
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * 락 없는(`strategy=none`) 접수가 동시 요청 하에서 정원을 초과해 허용한다는 것을
 * 실제 HTTP 레벨에서 재현하는 테스트.
 *
 * 좌석 10석짜리 회차에 50개 요청을 동시에 쏴서, 성공한 접수 수가 정원(10)을
 * 넘는지 확인한다 — 넘는 것이 "통과"이며, 이 문제를 고친 3가지 전략은
 * [ConcurrencyControlComparisonTest]에서 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [FlywayContextInitializer::class])
class OverbookingReproductionTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var registrationRepository: RegistrationRepository

    @Test
    fun `concurrent requests overbook a lock-free registration`() {
        val capacity = 10
        val concurrentRequests = 50
        val client = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()

        val examSessionId =
            client
                .post()
                .uri("/api/exam-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ExamSessionController.CreateExamSessionRequest(name = "overbooking-test", capacity = capacity))
                .exchange()
                .expectBody(ExamSessionController.ExamSessionResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        fireConcurrentRequests(concurrentRequests) { i ->
            client
                .post()
                .uri("/api/exam-sessions/$examSessionId/registrations?strategy=none")
                .contentType(MediaType.APPLICATION_JSON)
                .body(RegistrationController.RegisterRequest(userId = "user-$i"))
                .exchange()
        }

        val successfulRegistrations = registrationRepository.countByExamSessionId(examSessionId)

        assertThat(successfulRegistrations)
            .describedAs("정원 %d석인데 락이 없어 성공한 접수 수가 정원을 초과해야 이 테스트의 목적(버그 재현)이 성립한다", capacity)
            .isGreaterThan(capacity.toLong())
    }
}
