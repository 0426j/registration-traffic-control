package com.rtc.registration.config

import org.flywaydb.core.Flyway
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext

/**
 * Spring Boot 4.1.1에는 Flyway 자동설정 모듈이 아직 없어(다운로드된 모든
 * spring-boot-*.jar 안에 flyway 관련 클래스가 전혀 없음을 확인함), EntityManagerFactory
 * 빈 생성(Hibernate 스키마 검증) 전에 직접 마이그레이션을 실행해 순서를 보장한다.
 */
class FlywayContextInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val env = applicationContext.environment
        Flyway
            .configure()
            .dataSource(
                env.getProperty("spring.datasource.url"),
                env.getProperty("spring.datasource.username"),
                env.getProperty("spring.datasource.password"),
            ).load()
            .migrate()
    }
}
