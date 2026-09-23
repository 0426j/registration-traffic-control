package com.rtc.registration.web

import com.rtc.registration.service.RegistrationService
import com.rtc.registration.service.UnknownRegistrationStrategyException
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
    private val registrationServices: Map<String, RegistrationService>,
) {
    data class RegisterRequest(
        @field:NotBlank val userId: String,
    )

    data class RegistrationResponse(
        val id: Long,
        val examSessionId: Long,
        val userId: String,
    )

    @PostMapping
    fun register(
        @PathVariable examSessionId: Long,
        @RequestParam strategy: String,
        @RequestBody request: RegisterRequest,
    ): ResponseEntity<RegistrationResponse> {
        val registrationService =
            registrationServices[strategy] ?: throw UnknownRegistrationStrategyException(strategy)
        val registration = registrationService.register(examSessionId, request.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            RegistrationResponse(registration.id!!, examSessionId, request.userId),
        )
    }
}
