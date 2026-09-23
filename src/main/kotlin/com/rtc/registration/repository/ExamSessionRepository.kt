package com.rtc.registration.repository

import com.rtc.registration.domain.ExamSession
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ExamSessionRepository : JpaRepository<ExamSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExamSession e where e.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): ExamSession?

    /**
     * seatsRemaining이 여전히 [expectedSeatsRemaining]일 때만 [newSeatsRemaining]으로
     * 갱신하는 compare-and-set. 영향받은 행 수(0 또는 1)를 돌려주며, 0이면 그 사이
     * 다른 트랜잭션이 먼저 갱신했다는 뜻 — 호출자가 재시도한다.
     */
    @Modifying
    @Query(
        "update ExamSession e set e.seatsRemaining = :newSeatsRemaining " +
            "where e.id = :id and e.seatsRemaining = :expectedSeatsRemaining",
    )
    fun compareAndSetSeatsRemaining(
        @Param("id") id: Long,
        @Param("expectedSeatsRemaining") expectedSeatsRemaining: Int,
        @Param("newSeatsRemaining") newSeatsRemaining: Int,
    ): Int
}
