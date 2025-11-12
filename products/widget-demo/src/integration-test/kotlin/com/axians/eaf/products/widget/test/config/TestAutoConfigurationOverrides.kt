package com.axians.eaf.products.widget.test.config

/**
 * Central place to disable auto-configurations that would otherwise require a full JPA stack.
 */
object TestAutoConfigurationOverrides {
    const val DISABLE_MODULITH_JPA =
        "spring.autoconfigure.exclude=" +
            "org.springframework.modulith.events.jpa.JpaEventPublicationAutoConfiguration," +
            "org.springframework.modulith.events.jpa.archiving.ArchivingAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
}
