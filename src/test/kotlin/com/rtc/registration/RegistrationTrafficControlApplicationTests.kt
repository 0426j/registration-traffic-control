package com.rtc.registration

import com.rtc.registration.config.FlywayContextInitializer
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration

@SpringBootTest
@ContextConfiguration(initializers = [FlywayContextInitializer::class])
class RegistrationTrafficControlApplicationTests {
    @Test
    fun contextLoads() {
    }
}
