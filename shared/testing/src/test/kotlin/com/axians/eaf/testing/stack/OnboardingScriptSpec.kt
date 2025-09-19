package com.axians.eaf.testing.stack

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import java.nio.file.Files

class OnboardingScriptSpec :
    FunSpec({
        test("init-dev script documents credential rotation and service URLs") {
            val script = Files.readString(resolveRepoFile("scripts/init-dev.sh"))
            script.contains("Keycloak admin password not set").shouldBeTrue()
            script.contains("Keycloak admin password captured for this session").shouldBeTrue()
            script.contains("http://localhost:8080").shouldBeTrue()
            script.contains("http://localhost:3001").shouldBeTrue()
            script.contains("Security reminder: Keycloak admin password persisted").shouldBeTrue()
        }
    })
