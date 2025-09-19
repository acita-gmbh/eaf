package com.axians.eaf.licensing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class LicensingServerApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<LicensingServerApplication>(*args)
}
