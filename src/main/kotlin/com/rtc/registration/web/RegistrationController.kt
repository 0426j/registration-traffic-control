package com.rtc.registration.web

import com.rtc.registration.service.IdempotentRegistrationDispatcher
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/exam-sessions/{examSessionId}/registrations")
class RegistrationController(
    private val dispatcher: IdempotentRegistrationDispatcher,
) {
    data class RegisterRequest(
        @field:NotBlank val userId: String,
        @field:NotBlank val idempotencyKey: String,
    )

    data class RegistrationResponse(
        val id: Long,
        val examSessionId: Long,
        val userId: String,
        val status: String,
    )

    @PostMapping
    fun register(
        @PathVariable examSessionId: Long,
        @RequestParam strategy: String,
        @RequestBody request: RegisterRequest,
    ): ResponseEntity<RegistrationResponse> {
        val registration = dispatcher.register(strategy, examSessionId, request.userId, request.idempotencyKey)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            RegistrationResponse(registration.id!!, examSessionId, request.userId, registration.status.name),
        )
    }
}
