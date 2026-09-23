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
 * 같은 idempotencyKey로 두 번 요청해도(네트워크 재시도 등으로 클라이언트가
 * 중복 전송하는 상황을 흉내냄) 좌석이 두 번 소모되지 않고, 두 응답이 같은
 * 접수를 가리켜야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [FlywayContextInitializer::class])
class IdempotencyTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var registrationRepository: RegistrationRepository

    @Test
    fun `repeating the same idempotency key does not consume a second seat`() {
        val client = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()

        val examSessionId =
            client
                .post()
                .uri("/api/exam-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ExamSessionController.CreateExamSessionRequest(name = "idempotency-test", capacity = 1))
                .exchange()
                .expectBody(ExamSessionController.ExamSessionResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        val idempotencyKey = "$examSessionId-retry-me"
        val request = RegistrationController.RegisterRequest(userId = "retrier", idempotencyKey = idempotencyKey)

        fun submit() =
            client
                .post()
                .uri("/api/exam-sessions/$examSessionId/registrations?strategy=redis")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectBody(RegistrationController.RegistrationResponse::class.java)
                .returnResult()
                .responseBody!!

        val first = submit()
        val second = submit()

        assertThat(second.id).isEqualTo(first.id)
        assertThat(registrationRepository.countByExamSessionId(examSessionId))
            .describedAs("같은 idempotencyKey를 두 번 보내도 좌석 1개짜리 회차에 행이 1개만 생겨야 한다")
            .isEqualTo(1L)
    }
}
