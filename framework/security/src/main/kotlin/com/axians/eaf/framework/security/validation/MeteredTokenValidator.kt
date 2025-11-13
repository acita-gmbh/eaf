package com.axians.eaf.framework.security.validation

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Base class for JWT validators that emit per-layer metrics.
 *
 * Provides automatic instrumentation for:
 * - `jwt_validation_layer_duration_seconds{layer}` - Validation duration histogram
 * - `jwt_validation_layer_failures_total{layer,reason}` - Failure counter
 *
 * Story 3.9: Per-layer metrics for 10-layer JWT validation
 */
abstract class MeteredTokenValidator(
    private val layerName: String,
    private val meterRegistry: MeterRegistry,
) : OAuth2TokenValidator<Jwt> {
    private val durationTimer: Timer =
        Timer
            .builder("jwt_validation_layer_duration_seconds")
            .description("JWT validation layer execution time")
            .tag("layer", layerName)
            .publishPercentileHistogram()
            .register(meterRegistry)

    /**
     * Validates the JWT and records metrics.
     * Delegates to [doValidate] for actual validation logic.
     */
    final override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val sample = Timer.start()
        return try {
            val result = doValidate(token)
            sample.stop(durationTimer)

            if (result.hasErrors()) {
                recordFailure(meterRegistry, result.errors.first().description ?: "unknown")
            }

            result
        } catch (
            // LEGITIMATE: Observability only, re-throws immediately
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            sample.stop(durationTimer)
            recordFailure(meterRegistry, ex.javaClass.simpleName)
            throw ex
        }
    }

    /**
     * Perform actual validation logic.
     * Subclasses implement their specific validation here.
     */
    protected abstract fun doValidate(token: Jwt): OAuth2TokenValidatorResult

    private fun recordFailure(
        meterRegistry: MeterRegistry,
        reason: String,
    ) {
        Counter
            .builder("jwt_validation_layer_failures_total")
            .description("JWT validation layer failures by reason")
            .tag("layer", layerName)
            .tag("reason", reason)
            .register(meterRegistry)
            .increment()
    }
}
