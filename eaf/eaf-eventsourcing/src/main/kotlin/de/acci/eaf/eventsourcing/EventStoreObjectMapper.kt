package de.acci.eaf.eventsourcing

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import java.util.UUID

/**
 * Factory for creating a configured ObjectMapper for event store JSONB serialization.
 *
 * The ObjectMapper is configured for:
 * - Kotlin data class support
 * - ISO-8601 timestamp format
 * - Lenient deserialization (ignores unknown properties)
 * - Custom serializers for EAF value classes (TenantId, UserId, CorrelationId)
 */
public object EventStoreObjectMapper {

    /**
     * Create a new ObjectMapper configured for event store use.
     *
     * Features:
     * - Kotlin module for data class support
     * - JavaTimeModule for Instant/OffsetDateTime serialization
     * - ISO-8601 date format (no timestamps as arrays)
     * - Ignores unknown properties during deserialization
     * - Custom serializers for EAF identifier value classes
     */
    public fun create(): ObjectMapper = jacksonObjectMapper().apply {
        // Register Java 8 date/time module for Instant support
        registerModule(JavaTimeModule())

        // Register custom serializers for EAF value classes
        registerModule(eafIdentifiersModule())

        // Write dates as ISO-8601 strings, not timestamps
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        // Be lenient when deserializing - ignore unknown properties
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    private fun eafIdentifiersModule(): SimpleModule = SimpleModule().apply {
        // TenantId serialization
        addSerializer(TenantId::class.java, object : JsonSerializer<TenantId>() {
            override fun serialize(value: TenantId, gen: JsonGenerator, serializers: SerializerProvider) {
                gen.writeString(value.value.toString())
            }
        })
        addDeserializer(TenantId::class.java, object : JsonDeserializer<TenantId>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): TenantId {
                return TenantId(UUID.fromString(p.valueAsString))
            }
        })

        // UserId serialization
        addSerializer(UserId::class.java, object : JsonSerializer<UserId>() {
            override fun serialize(value: UserId, gen: JsonGenerator, serializers: SerializerProvider) {
                gen.writeString(value.value.toString())
            }
        })
        addDeserializer(UserId::class.java, object : JsonDeserializer<UserId>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): UserId {
                return UserId(UUID.fromString(p.valueAsString))
            }
        })

        // CorrelationId serialization
        addSerializer(CorrelationId::class.java, object : JsonSerializer<CorrelationId>() {
            override fun serialize(value: CorrelationId, gen: JsonGenerator, serializers: SerializerProvider) {
                gen.writeString(value.value.toString())
            }
        })
        addDeserializer(CorrelationId::class.java, object : JsonDeserializer<CorrelationId>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CorrelationId {
                return CorrelationId(UUID.fromString(p.valueAsString))
            }
        })
    }
}
