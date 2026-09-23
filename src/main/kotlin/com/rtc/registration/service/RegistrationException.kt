package com.rtc.registration.service

class ExamSessionNotFoundException(
    examSessionId: Long,
) : RuntimeException("exam session not found: $examSessionId")

class NoSeatsRemainingException(
    examSessionId: Long,
) : RuntimeException("no seats remaining for exam session: $examSessionId")

class OptimisticLockRetryExhaustedException(
    examSessionId: Long,
) : RuntimeException("optimistic lock retry exhausted for exam session: $examSessionId")

class UnknownRegistrationStrategyException(
    strategy: String,
) : RuntimeException("unknown registration strategy: $strategy")
