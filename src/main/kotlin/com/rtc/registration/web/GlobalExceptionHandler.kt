package com.rtc.registration.web

import com.rtc.registration.service.ExamSessionNotFoundException
import com.rtc.registration.service.NoSeatsRemainingException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    data class ErrorResponse(
        val message: String,
    )

    @ExceptionHandler(ExamSessionNotFoundException::class)
    fun handleNotFound(ex: ExamSessionNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(ex.message ?: "not found"))

    @ExceptionHandler(NoSeatsRemainingException::class)
    fun handleNoSeats(ex: NoSeatsRemainingException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(ex.message ?: "conflict"))
}
