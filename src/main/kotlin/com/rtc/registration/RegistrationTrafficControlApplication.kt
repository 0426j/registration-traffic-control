package com.rtc.registration

import com.rtc.registration.config.FlywayContextInitializer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RegistrationTrafficControlApplication

fun main(args: Array<String>) {
    runApplication<RegistrationTrafficControlApplication>(*args) {
        addInitializers(FlywayContextInitializer())
    }
}
