package com.axians.eaf.framework.observability.metrics

import com.axians.eaf.framework.security.tenant.TenantContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.autoconfigure.transaction.jta.JtaAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Duration

@SpringBootTest(
    classes = [PrometheusEndpointIntegrationTest.TestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.application.name=observability-integration",
        "management.endpoints.web.base-path=/actuator",
        "management.endpoint.prometheus.enabled=true",
        "management.endpoints.web.exposure.include=health,prometheus",
        "spring.security.user.name=metrics-admin",
        "spring.security.user.password=password",
        "spring.security.user.roles=eaf-admin",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.transaction.jta.JtaAutoConfiguration,org.springframework.modulith.events.jpa.JpaEventPublicationAutoConfiguration",
    ],
)
@ActiveProfiles("observability-test")
class PrometheusEndpointIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var customMetrics: CustomMetrics

    @Autowired
    private lateinit var tenantContext: TenantContext

    init {
        extension(SpringExtension())

        test("5.2-INT-001: unauthenticated requests to /actuator/prometheus are rejected") {
            val response = restTemplate.getForEntity("/actuator/prometheus", String::class.java)
            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        test("5.2-INT-002: authorized requests can scrape Prometheus metrics with service and tenant tags") {
            tenantContext.setCurrentTenantId("integration-tenant")
            try {
                customMetrics.recordCommand("TestCommand", Duration.ofMillis(5), success = true)
                customMetrics.recordEvent("TestEvent", Duration.ofMillis(3), success = true)
            } finally {
                tenantContext.clearCurrentTenant()
            }

            val headers = HttpHeaders()
            headers.accept = listOf(MediaType.TEXT_PLAIN)
            val response =
                restTemplate
                    .withBasicAuth("metrics-admin", "password")
                    .exchange(
                        "/actuator/prometheus",
                        HttpMethod.GET,
                        HttpEntity<Void>(headers),
                        String::class.java,
                    )

            response.statusCode shouldBe HttpStatus.OK
            val body = response.body ?: error("Prometheus response body was null")
            body shouldContain "service_name=\"observability-integration\""
            body shouldContain "tenant_id=\"integration-tenant\""
            body shouldContain "eaf_commands_total"
            body shouldContain "eaf_events_total"
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(TestApplication::class.java, *args)
        }

        @DynamicPropertySource
        @JvmStatic
        fun disableManagementSecurity(registry: DynamicPropertyRegistry) {
            registry.add("management.security.enabled") { false }
        }
    }

    @SpringBootApplication(
        scanBasePackages = ["com.axians.eaf.framework.observability.metrics"],
        exclude = [
            DataSourceAutoConfiguration::class,
            HibernateJpaAutoConfiguration::class,
            JtaAutoConfiguration::class,
        ],
    )
    @EnableAutoConfiguration(
        exclude = [
            DataSourceAutoConfiguration::class,
            HibernateJpaAutoConfiguration::class,
            JtaAutoConfiguration::class,
        ],
    )
    open class TestApplication {
        @Bean
        open fun tenantContext(): TenantContext = TenantContext()

        @Bean
        open fun userDetailsService(): UserDetailsService =
            InMemoryUserDetailsManager(
                User
                    .withUsername("metrics-admin")
                    .password("{noop}password")
                    .roles("eaf-admin")
                    .build(),
            )

        @Bean
        open fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
            http
                .authorizeHttpRequests { authorize ->
                    authorize
                        .requestMatchers("/actuator/health")
                        .permitAll()
                        .requestMatchers("/actuator/prometheus")
                        .hasRole("eaf-admin")
                        .anyRequest()
                        .permitAll()
                }.httpBasic { }
                .csrf { csrf -> csrf.disable() }
                .build()
    }
}
