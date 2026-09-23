package com.rtc.registration.repository

import com.rtc.registration.domain.Registration
import com.rtc.registration.domain.RegistrationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RegistrationRepository : JpaRepository<Registration, Long> {
    fun countByExamSessionId(examSessionId: Long): Long

    fun findByIdempotencyKey(idempotencyKey: String): Registration?

    /**
     * status가 여전히 [expectedStatus]일 때만 [newStatus]로 바꾸는 compare-and-set.
     * 같은 접수의 결제 결과를 동시에 두 번 반영하려는 경쟁(예: 재시도 중복 호출)을
     * 막는다 — 영향받은 행 수가 0이면 이미 다른 요청이 먼저 상태를 바꿨다는 뜻.
     */
    @Modifying
    @Query(
        "update Registration r set r.status = :newStatus " +
            "where r.id = :id and r.status = :expectedStatus",
    )
    fun compareAndSetStatus(
        @Param("id") id: Long,
        @Param("expectedStatus") expectedStatus: RegistrationStatus,
        @Param("newStatus") newStatus: RegistrationStatus,
    ): Int
}
