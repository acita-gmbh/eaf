package com.axians.eaf.products.widget.test.config

/**
 * Central place to disable auto-configurations that would otherwise require a full JPA stack.
 */
object TestAutoConfigurationOverrides {
    /**
     * Disables Modulith JPA events AND Hibernate JPA.
     * Use when you don't need JPA/jOOQ (e.g., AuthController tests with minimal dependencies).
     */
    const val DISABLE_MODULITH_JPA =
        "spring.autoconfigure.exclude=" +
            "org.springframework.modulith.events.jpa.JpaEventPublicationAutoConfiguration," +
            "org.springframework.modulith.events.jpa.archiving.ArchivingAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"

    /**
     * Disables ONLY Modulith JPA events, keeps Hibernate/jOOQ enabled.
     * Use when you need jOOQ for projections (e.g., RBAC tests with full Widget CQRS stack).
     */
    const val DISABLE_MODULITH_EVENTS =
        "spring.autoconfigure.exclude=" +
            "org.springframework.modulith.events.jpa.JpaEventPublicationAutoConfiguration," +
            "org.springframework.modulith.events.jpa.archiving.ArchivingAutoConfiguration"
}
