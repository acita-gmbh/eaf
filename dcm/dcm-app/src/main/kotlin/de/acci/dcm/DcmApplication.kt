package de.acci.dcm

import de.acci.eaf.tenant.TenantContextWebFilter
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

/**
 * DCM Spring Boot Application entry point.
 *
 * Imports TenantContextWebFilter from eaf-tenant for multi-tenancy support.
 * The filter extracts tenant_id from JWT and sets it in the Reactor context.
 */
@SpringBootApplication
@Import(TenantContextWebFilter::class)
public class DcmApplication

/**
 * Main entry point.
 */
public fun main(args: Array<String>) {
    runApplication<DcmApplication>(*args)
}
