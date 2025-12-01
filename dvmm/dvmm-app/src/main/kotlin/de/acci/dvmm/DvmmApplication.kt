package de.acci.dvmm

import de.acci.eaf.tenant.TenantContextWebFilter
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

/**
 * DVMM Spring Boot Application entry point.
 *
 * Imports TenantContextWebFilter from eaf-tenant for multi-tenancy support.
 * The filter extracts tenant_id from JWT and sets it in the Reactor context.
 */
@SpringBootApplication
@Import(TenantContextWebFilter::class)
public class DvmmApplication

public fun main(args: Array<String>) {
    runApplication<DvmmApplication>(*args)
}
