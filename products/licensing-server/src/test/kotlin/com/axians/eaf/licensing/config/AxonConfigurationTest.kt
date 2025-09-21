package com.axians.eaf.licensing.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.axonframework.serialization.json.JacksonSerializer

class AxonConfigurationTest :
    FunSpec({

        context("Axon Configuration Unit Tests") {
            test("should create Jackson serializer") {
                val axonConfiguration = AxonConfiguration()

                val serializer = axonConfiguration.eventSerializer()

                serializer shouldNotBe null
                serializer.shouldBeInstanceOf<JacksonSerializer>()
            }

            test("should be open class for Spring proxying") {
                val configClass = AxonConfiguration::class.java

                // Verify class is not final (open for Spring)
                val isFinal =
                    java.lang.reflect.Modifier
                        .isFinal(configClass.modifiers)
                isFinal shouldBe false
            }

            test("should have open bean methods for Spring proxying") {
                val serializerMethod =
                    AxonConfiguration::class.java
                        .getDeclaredMethod("eventSerializer")

                // Verify methods are not final (open for Spring)
                val serializerFinal =
                    java.lang.reflect.Modifier
                        .isFinal(serializerMethod.modifiers)
                serializerFinal shouldBe false
            }
        }
    })
