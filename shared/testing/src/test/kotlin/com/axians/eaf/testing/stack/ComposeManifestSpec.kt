package com.axians.eaf.testing.stack

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import java.nio.file.Files

class ComposeManifestSpec :
    FunSpec({
        test("1.3-UNIT-001: compose.yml declares required services and versions") {
            val compose = Files.readString(resolveRepoFile("compose.yml"))
            listOf(
                "image: postgres:16.1-alpine",
                "image: quay.io/keycloak/keycloak:26.0.0",
                "image: redis:7.2-alpine",
                "image: prom/prometheus",
                "image: grafana/grafana",
            ).forEach { marker ->
                compose.contains(marker).shouldBeTrue()
            }
            compose.contains("name: eaf-network").shouldBeTrue()
            compose.contains("test-realm.json:ro").shouldBeTrue()
        }
    })
