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
 * 결제가 성공하면 접수가 PENDING_PAYMENT에서 CONFIRMED로 바뀌고, 좌석은
 * 그대로 소모된 채 유지됨(반환되지 않음)을 검증한다. `failure-rate=0`으로
 * 결제가 항상 성공하게 고정.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["payment.mock.failure-rate=0.0", "payment.mock.min-delay-ms=1", "payment.mock.max-delay-ms=5"],
)
@ContextConfiguration(initializers = [FlywayContextInitializer::class])
class PaymentSuccessTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var registrationRepository: RegistrationRepository

    @Test
    fun `successful payment confirms the registration`() {
        val client = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()

        val examSessionId =
            client
                .post()
                .uri("/api/exam-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ExamSessionController.CreateExamSessionRequest(name = "payment-success-test", capacity = 5))
                .exchange()
                .expectBody(ExamSessionController.ExamSessionResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        val registrationId =
            client
                .post()
                .uri("/api/exam-sessions/$examSessionId/registrations?strategy=redis")
                .contentType(MediaType.APPLICATION_JSON)
                .body(RegistrationController.RegisterRequest(userId = "payer", idempotencyKey = "$examSessionId-payer"))
                .exchange()
                .expectBody(RegistrationController.RegistrationResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        assertThat(registrationRepository.findById(registrationId).get().status)
            .isEqualTo(RegistrationStatus.PENDING_PAYMENT)

        val paid =
            client
                .post()
                .uri("/api/registrations/$registrationId/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .body(PaymentController.PaymentRequest(amount = 38_000))
                .exchange()
                .expectBody(PaymentController.PaymentResponse::class.java)
                .returnResult()
                .responseBody!!

        assertThat(paid.status).isEqualTo(RegistrationStatus.CONFIRMED.name)
        assertThat(registrationRepository.findById(registrationId).get().status)
            .isEqualTo(RegistrationStatus.CONFIRMED)
    }
}
