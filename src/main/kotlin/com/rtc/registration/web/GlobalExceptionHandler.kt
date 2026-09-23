package com.rtc.registration.web

import com.rtc.registration.service.ExamSessionNotFoundException
import com.rtc.registration.service.NoSeatsRemainingException
import com.rtc.registration.service.OptimisticLockRetryExhaustedException
import com.rtc.registration.service.UnknownRegistrationStrategyException
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

    @ExceptionHandler(OptimisticLockRetryExhaustedException::class)
    fun handleRetryExhausted(ex: OptimisticLockRetryExhaustedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(ex.message ?: "conflict"))

    @ExceptionHandler(UnknownRegistrationStrategyException::class)
    fun handleUnknownStrategy(ex: UnknownRegistrationStrategyException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(ex.message ?: "bad request"))
}
