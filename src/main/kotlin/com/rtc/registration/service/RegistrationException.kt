package com.rtc.registration.service

class ExamSessionNotFoundException(
    examSessionId: Long,
) : RuntimeException("exam session not found: $examSessionId")

class NoSeatsRemainingException(
    examSessionId: Long,
) : RuntimeException("no seats remaining for exam session: $examSessionId")
