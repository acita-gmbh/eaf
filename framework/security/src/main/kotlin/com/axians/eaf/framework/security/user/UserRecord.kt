package com.axians.eaf.framework.security.user

/** Represents a user entry retrieved from the identity provider. */
data class UserRecord(
    val id: String,
    val active: Boolean,
)
