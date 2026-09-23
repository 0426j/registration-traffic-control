package com.rtc.registration.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain

/**
 * 임시 보안 설정 — 인증/인가는 로드맵의 별도 항목(마이너: idempotency 키의
 * 사용자 식별용 Spring Security 기본 세팅)에서 다룬다. 그 전까지는 모든
 * 요청을 허용해 동시성/트래픽 로직 검증에 집중한다.
 */
@Configuration
class SecurityConfig {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            authorizeHttpRequests {
                authorize(anyRequest, permitAll)
            }
        }
        return http.build()
    }
}
