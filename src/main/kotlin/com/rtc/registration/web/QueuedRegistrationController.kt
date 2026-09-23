package com.rtc.registration.web

import com.rtc.registration.queue.RegistrationRequestProducer
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 접수 요청을 즉시 확정하지 않고 큐에 적재만 하는 엔드포인트. 응답은 접수
 * 확정이 아니라 "접수 요청을 받았다"는 뜻(202 Accepted) — 실제 성공/거부는
 * [com.rtc.registration.queue.RegistrationRequestConsumer]가 비동기로 처리한다.
 */
@RestController
@RequestMapping("/api/exam-sessions/{examSessionId}/registrations")
class QueuedRegistrationController(
    private val registrationRequestProducer: RegistrationRequestProducer,
) {
    data class QueueRegisterRequest(
        @field:NotBlank val userId: String,
        @field:NotBlank val idempotencyKey: String,
    )

    data class QueuedResponse(
        val requestId: String,
        val examSessionId: Long,
        val userId: String,
    )

    @PostMapping("/queue")
    fun enqueue(
        @PathVariable examSessionId: Long,
        @RequestBody request: QueueRegisterRequest,
    ): ResponseEntity<QueuedResponse> {
        val requestId = registrationRequestProducer.enqueue(examSessionId, request.userId, request.idempotencyKey)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            QueuedResponse(requestId.toString(), examSessionId, request.userId),
        )
    }
}
