plugins {
    id("eaf.kotlin-conventions")
    id("eaf.test-conventions")
}

// eaf-auth: IdP-agnostic authentication interfaces
// No concrete IdP implementations here (Keycloak etc. go to eaf-auth-keycloak)
dependencies {
    api(project(":eaf:eaf-core"))
}
