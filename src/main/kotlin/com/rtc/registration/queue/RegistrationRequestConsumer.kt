package com.rtc.registration.queue

import com.rtc.registration.service.ExamSessionNotFoundException
import com.rtc.registration.service.NoSeatsRemainingException
import com.rtc.registration.service.RegistrationService
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.redisson.api.RedissonClient
import org.redisson.api.stream.StreamCreateGroupArgs
import org.redisson.api.stream.StreamReadGroupArgs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [RegistrationRequestProducer]가 쌓은 스트림을 컨슈머 그룹으로 순차 읽어
 * 실제 등록을 처리하는 백그라운드 워커. 좌석 차감 자체는 이미 정확성이
 * 검증된 "redis"(원자적 차감) 전략에 위임한다 — 이 전략의 목적은 "락을
 * 다르게 건다"가 아니라 "요청을 큐로 흡수해 처리를 뒤로 미룬다"는 것이므로.
 */
@Component
class RegistrationRequestConsumer(
    private val redissonClient: RedissonClient,
    @param:Qualifier("redis") private val registrationService: RegistrationService,
) {
    private val running = AtomicBoolean(true)
    private lateinit var workerThread: Thread

    @PostConstruct
    fun start() {
        val stream = redissonClient.getStream<String, String>(RegistrationRequestProducer.STREAM_KEY)
        runCatching {
            stream.createGroup(StreamCreateGroupArgs.name(RegistrationRequestProducer.GROUP_NAME).makeStream())
        }.onFailure { ex ->
            log.debug("consumer group already exists (expected on restart): {}", ex.message)
        }

        workerThread = Thread(::pollLoop, "registration-queue-worker")
        workerThread.isDaemon = true
        workerThread.start()
    }

    @PreDestroy
    fun stop() {
        running.set(false)
        workerThread.interrupt()
    }

    private fun pollLoop() {
        val stream = redissonClient.getStream<String, String>(RegistrationRequestProducer.STREAM_KEY)
        while (running.get()) {
            try {
                val messages =
                    stream.readGroup(
                        RegistrationRequestProducer.GROUP_NAME,
                        RegistrationRequestProducer.CONSUMER_NAME,
                        StreamReadGroupArgs.neverDelivered().count(50).timeout(Duration.ofSeconds(1)),
                    )
                messages.forEach { (id, fields) ->
                    processMessage(fields)
                    stream.ack(RegistrationRequestProducer.GROUP_NAME, id)
                }
            } catch (ex: InterruptedException) {
                return
            } catch (ex: Exception) {
                if (!running.get()) {
                    // 종료(stop())가 워커 스레드를 interrupt할 때 Redisson이 InterruptedException을
                    // RedisException으로 감싸서 던지는 경우가 있다 — 종료 중이면 정상 상황이라 무시.
                    return
                }
                log.warn("registration queue worker iteration failed, continuing", ex)
            }
        }
    }

    private fun processMessage(fields: Map<String, String>) {
        val examSessionId = fields[RegistrationRequestProducer.FIELD_EXAM_SESSION_ID]!!.toLong()
        val userId = fields[RegistrationRequestProducer.FIELD_USER_ID]!!
        try {
            registrationService.register(examSessionId, userId)
        } catch (ex: NoSeatsRemainingException) {
            log.info("registration rejected, no seats remaining: examSessionId={}", examSessionId)
        } catch (ex: ExamSessionNotFoundException) {
            log.warn("registration dropped, exam session not found: examSessionId={}", examSessionId)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RegistrationRequestConsumer::class.java)
    }
}
