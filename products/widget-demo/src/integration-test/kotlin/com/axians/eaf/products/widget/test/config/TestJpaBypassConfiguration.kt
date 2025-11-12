package com.axians.eaf.products.widget.test.config

import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile

/**
 * Removes JPA auto-configuration artifacts from the test profile to ensure Modulith/JPA
 * infrastructure doesn't start when we only need Testcontainers + jOOQ.
 */
@TestConfiguration
@Profile("test")
open class TestJpaBypassConfiguration {
    @Bean
    open fun disableJpaBeans(): BeanFactoryPostProcessor =
        BeanFactoryPostProcessor { beanFactory: ConfigurableListableBeanFactory ->
            val registry = beanFactory as? BeanDefinitionRegistry

            listOf(
                "entityManagerFactory",
                "jpaSharedEM_entityManagerFactory",
            ).forEach { beanName ->
                if (registry?.containsBeanDefinition(beanName) == true) {
                    registry.removeBeanDefinition(beanName)
                }
            }
        }
}
