package com.rtc.registration

import com.rtc.registration.config.FlywayContextInitializer
import com.rtc.registration.repository.RegistrationRepository
import com.rtc.registration.web.ExamSessionController
import com.rtc.registration.web.QueuedRegistrationController
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.client.RestTestClient
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.measureTime

/**
 * 정원(10)보다 훨씬 큰 버스트(200건)를 큐 엔드포인트에 동시에 쏴도, 각 요청의
 * "적재" 응답 자체는 빠르게 끝나고(폭주를 큐가 흡수), 뒤에서 컨슈머가 처리를
 * 마치면 결과적으로 정확히 정원만큼만 접수가 확정됨을 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [FlywayContextInitializer::class])
class QueueBurstAbsorptionTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var registrationRepository: RegistrationRepository

    @Test
    fun `burst of requests is absorbed by the queue and settles at exactly capacity`() {
        val capacity = 10
        val burstSize = 200
        val client = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()

        val examSessionId =
            client
                .post()
                .uri("/api/exam-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ExamSessionController.CreateExamSessionRequest(name = "queue-burst-test", capacity = capacity))
                .exchange()
                .expectBody(ExamSessionController.ExamSessionResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        val enqueueDurationsMs = ConcurrentLinkedQueue<Long>()

        val enqueueElapsed =
            measureTime {
                fireConcurrentRequests(burstSize) { i ->
                    val start = System.nanoTime()
                    client
                        .post()
                        .uri("/api/exam-sessions/$examSessionId/registrations/queue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(QueuedRegistrationController.QueueRegisterRequest(userId = "user-$i"))
                        .exchange()
                    enqueueDurationsMs.add((System.nanoTime() - start) / 1_000_000)
                }
            }
        println(
            "[queue] enqueue $burstSize requests took $enqueueElapsed " +
                "(avg ${enqueueDurationsMs.average()}ms, max ${enqueueDurationsMs.max()}ms)",
        )

        waitUntilRegistrationCountStabilizes(examSessionId)

        val successfulRegistrations = registrationRepository.countByExamSessionId(examSessionId)
        assertThat(successfulRegistrations)
            .describedAs("정원(%d)보다 훨씬 큰 버스트(%d)를 쏴도 컨슈머가 처리를 마치면 정확히 정원만큼만 남아야 한다", capacity, burstSize)
            .isEqualTo(capacity.toLong())
    }

    private fun waitUntilRegistrationCountStabilizes(examSessionId: Long) {
        var previousCount = -1L
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val current = registrationRepository.countByExamSessionId(examSessionId)
            if (current == previousCount) return
            previousCount = current
            Thread.sleep(300)
        }
    }
}
