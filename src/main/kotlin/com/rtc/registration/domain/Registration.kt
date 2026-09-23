package com.rtc.registration.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "registration")
class Registration(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "exam_session_id")
    val examSessionId: Long,
    @Column(name = "user_id")
    val userId: String,
    @Column(name = "idempotency_key")
    val idempotencyKey: String,
    @Column(name = "strategy")
    val strategy: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: RegistrationStatus = RegistrationStatus.PENDING_PAYMENT,
    @Column(name = "registered_at")
    val registeredAt: Instant = Instant.now(),
)
