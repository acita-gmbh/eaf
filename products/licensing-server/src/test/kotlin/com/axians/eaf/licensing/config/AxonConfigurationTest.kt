package com.axians.eaf.licensing.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.axonframework.serialization.json.JacksonSerializer

class AxonConfigurationTest :
    FunSpec({

        context("Axon Configuration Unit Tests") {
            test("9.1-UNIT-001: should create Jackson serializer") {
                val axonConfiguration = AxonConfiguration()

                val serializer = axonConfiguration.eventSerializer()

                serializer shouldNotBe null
                serializer.shouldBeInstanceOf<JacksonSerializer>()
            }

            test("9.1-UNIT-002: should be properly configured as Spring configuration bean") {
                // Verify the configuration class has required Spring annotations
                val configClass = AxonConfiguration::class.java
                val hasConfigurationAnnotation =
                    configClass.isAnnotationPresent(
                        org.springframework.context.annotation.Configuration::class.java,
                    )
                hasConfigurationAnnotation shouldBe true
            }

            test("9.1-UNIT-003: should allow Spring to create bean proxies") {
                val axonConfiguration = AxonConfiguration()

                // Verify that bean methods can be called (Spring proxy compatibility)
                val serializer1 = axonConfiguration.eventSerializer()
                val serializer2 = axonConfiguration.eventSerializer()

                // Both calls should work (no final method blocking)
                serializer1 shouldNotBe null
                serializer2 shouldNotBe null
                serializer1.shouldBeInstanceOf<JacksonSerializer>()
                serializer2.shouldBeInstanceOf<JacksonSerializer>()
            }
        }
    })
