package com.rtc.registration.web

import com.rtc.registration.service.PaymentService
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/registrations/{registrationId}/payment")
class PaymentController(
    private val paymentService: PaymentService,
) {
    data class PaymentRequest(
        @field:Min(1) val amount: Long,
    )

    data class PaymentResponse(
        val registrationId: Long,
        val status: String,
    )

    @PostMapping
    fun pay(
        @PathVariable registrationId: Long,
        @RequestBody request: PaymentRequest,
    ): ResponseEntity<PaymentResponse> {
        val registration = paymentService.pay(registrationId, request.amount)
        return ResponseEntity.ok(PaymentResponse(registration.id!!, registration.status.name))
    }
}
