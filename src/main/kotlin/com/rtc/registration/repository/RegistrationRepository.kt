package com.rtc.registration.repository

import com.rtc.registration.domain.Registration
import org.springframework.data.jpa.repository.JpaRepository

interface RegistrationRepository : JpaRepository<Registration, Long> {
    fun countByExamSessionId(examSessionId: Long): Long
}
