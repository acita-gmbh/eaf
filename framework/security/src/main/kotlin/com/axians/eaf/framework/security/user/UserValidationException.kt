package com.axians.eaf.framework.security.user

/** Signals that the identity provider could not confirm user status. */
class UserValidationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
