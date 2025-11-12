package com.axians.eaf.framework.security.config

import com.axians.eaf.framework.security.revocation.TokenRevocationStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Instant

@Configuration
@Profile("test")
@ConditionalOnMissingBean(TokenRevocationStore::class)
open class TestTokenRevocationConfiguration {
    @Bean
    open fun tokenRevocationStore(): TokenRevocationStore =
        object : TokenRevocationStore {
            override fun isRevoked(jti: String): Boolean = false

            override fun revoke(
                jti: String,
                expiresAt: Instant?,
            ) {
                // no-op for tests
            }
        }
}
