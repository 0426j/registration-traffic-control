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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 락 없는 [com.rtc.registration.service.RegistrationService]가 동시 요청 하에서
 * 정원을 초과해 접수를 허용한다는 것을 실제 HTTP 레벨에서 재현하는 테스트.
 *
 * 좌석 10석짜리 회차에 50개 요청을 동시에 쏴서, 성공한 접수 수가 정원(10)을
 * 넘는지 확인한다 — 넘는 것이 "통과"이며, 이 프로젝트가 다음 단계(락 3종
 * 비교)에서 고칠 문제가 실제로 존재함을 증명하는 것이 이 테스트의 목적이다.
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

        val readyLatch = CountDownLatch(concurrentRequests)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(concurrentRequests)
        val executor = Executors.newFixedThreadPool(concurrentRequests)

        repeat(concurrentRequests) { i ->
            executor.submit {
                readyLatch.countDown()
                startLatch.await()
                try {
                    client
                        .post()
                        .uri("/api/exam-sessions/$examSessionId/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(RegistrationController.RegisterRequest(userId = "user-$i"))
                        .exchange()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        readyLatch.await(10, TimeUnit.SECONDS)
        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        val successfulRegistrations = registrationRepository.countByExamSessionId(examSessionId)

        assertThat(successfulRegistrations)
            .describedAs("정원 %d석인데 락이 없어 성공한 접수 수가 정원을 초과해야 이 테스트의 목적(버그 재현)이 성립한다", capacity)
            .isGreaterThan(capacity.toLong())
    }
}
