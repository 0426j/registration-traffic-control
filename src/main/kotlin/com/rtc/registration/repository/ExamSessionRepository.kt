package com.rtc.registration.repository

import com.rtc.registration.domain.ExamSession
import org.springframework.data.jpa.repository.JpaRepository

interface ExamSessionRepository : JpaRepository<ExamSession, Long>
