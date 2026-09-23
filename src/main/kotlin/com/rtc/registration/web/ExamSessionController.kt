package com.rtc.registration.web

import com.rtc.registration.domain.ExamSession
import com.rtc.registration.repository.ExamSessionRepository
import com.rtc.registration.service.ExamSessionSeatCounter
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/exam-sessions")
class ExamSessionController(
    private val examSessionRepository: ExamSessionRepository,
    private val seatCounter: ExamSessionSeatCounter,
) {
    data class CreateExamSessionRequest(
        @field:NotBlank val name: String,
        @field:Min(1) val capacity: Int,
    )

    data class ExamSessionResponse(
        val id: Long,
        val name: String,
        val capacity: Int,
        val seatsRemaining: Int,
    )

    @PostMapping
    fun create(
        @RequestBody request: CreateExamSessionRequest,
    ): ResponseEntity<ExamSessionResponse> {
        val saved =
            examSessionRepository.save(
                ExamSession(name = request.name, capacity = request.capacity, seatsRemaining = request.capacity),
            )
        seatCounter.initialize(saved.id!!, saved.capacity)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toResponse())
    }

    private fun ExamSession.toResponse() = ExamSessionResponse(id!!, name, capacity, seatsRemaining)
}
