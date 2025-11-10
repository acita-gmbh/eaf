package com.axians.eaf.products.widget.api.auth

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration

@SpringBootApplication(
    scanBasePackages = [
        "com.axians.eaf.framework.security",
        "com.axians.eaf.products.widget.api.auth",
    ],
    exclude = [
        DataSourceAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        FlywayAutoConfiguration::class,
    ],
)
class AuthControllerTestApplication
