package de.acci.eaf.notifications

/**
 * Email address value object.
 * Purpose: type safety for notification recipients.
 *
 * Note: Minimal validation for MVP. Full RFC 5322 validation is complex
 * and typically handled by the mail server or a dedicated library.
 */
@JvmInline
public value class EmailAddress private constructor(public val value: String) {
    public companion object {
        private val BASIC_EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+\$")

        /**
         * Create an email address from a string.
         * @throws IllegalArgumentException if the format is invalid
         */
        public fun of(email: String): EmailAddress {
            require(BASIC_EMAIL_PATTERN.matches(email)) {
                "Invalid email format: $email"
            }
            return EmailAddress(email.lowercase())
        }

        /**
         * Try to create an email address, returning null if invalid.
         */
        public fun ofOrNull(email: String): EmailAddress? =
            if (BASIC_EMAIL_PATTERN.matches(email)) EmailAddress(email.lowercase()) else null
    }

    override fun toString(): String = value
}
