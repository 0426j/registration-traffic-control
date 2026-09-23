package com.rtc.registration

import com.rtc.registration.config.FlywayContextInitializer
import com.rtc.registration.domain.RegistrationStatus
import com.rtc.registration.repository.RegistrationRepository
import com.rtc.registration.web.ExamSessionController
import com.rtc.registration.web.PaymentController
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
 * 결제가 실패하면 접수는 FAILED로 바뀌고, 붙잡고 있던 좌석은 반환되어 다른
 * 사용자가 그 좌석으로 접수할 수 있어야 한다. `failure-rate=1`로 결제가
 * 항상 실패하게 고정하고, 정원 1석짜리 회차로 좌석 반환 여부를 명확히 본다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["payment.mock.failure-rate=1.0", "payment.mock.min-delay-ms=1", "payment.mock.max-delay-ms=5"],
)
@ContextConfiguration(initializers = [FlywayContextInitializer::class])
class PaymentFailureReleasesSeatTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var registrationRepository: RegistrationRepository

    @Test
    fun `failed payment releases the seat for another user`() {
        val client = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()

        val examSessionId =
            client
                .post()
                .uri("/api/exam-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ExamSessionController.CreateExamSessionRequest(name = "payment-failure-test", capacity = 1))
                .exchange()
                .expectBody(ExamSessionController.ExamSessionResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        val firstRegistrationId =
            client
                .post()
                .uri("/api/exam-sessions/$examSessionId/registrations?strategy=redis")
                .contentType(MediaType.APPLICATION_JSON)
                .body(RegistrationController.RegisterRequest(userId = "first", idempotencyKey = "$examSessionId-first"))
                .exchange()
                .expectBody(RegistrationController.RegistrationResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        // 정원이 1석뿐이라, 결제 전인데도 다른 사용자는 지금은 자리가 없어 거부된다.
        client
            .post()
            .uri("/api/exam-sessions/$examSessionId/registrations?strategy=redis")
            .contentType(MediaType.APPLICATION_JSON)
            .body(RegistrationController.RegisterRequest(userId = "second-before-release", idempotencyKey = "$examSessionId-second-a"))
            .exchange()
            .expectStatus()
            .isEqualTo(409)

        val paid =
            client
                .post()
                .uri("/api/registrations/$firstRegistrationId/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .body(PaymentController.PaymentRequest(amount = 38_000))
                .exchange()
                .expectBody(PaymentController.PaymentResponse::class.java)
                .returnResult()
                .responseBody!!

        assertThat(paid.status).isEqualTo(RegistrationStatus.FAILED.name)
        assertThat(registrationRepository.findById(firstRegistrationId).get().status)
            .isEqualTo(RegistrationStatus.FAILED)

        // 좌석이 반환됐으므로 이번엔 성공해야 한다.
        client
            .post()
            .uri("/api/exam-sessions/$examSessionId/registrations?strategy=redis")
            .contentType(MediaType.APPLICATION_JSON)
            .body(RegistrationController.RegisterRequest(userId = "second-after-release", idempotencyKey = "$examSessionId-second-b"))
            .exchange()
            .expectStatus()
            .isCreated
    }
}
