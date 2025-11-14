package com.axians.eaf.products.widget.test.config

import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.net.URI

/**
 * Exception Handler for Method Security Exceptions (Story 3.10).
 *
 * **CRITICAL ARCHITECTURAL INSIGHT (from AI agents analysis):**
 * - AccessDeniedHandler in FilterChain catches FILTER-STACK exceptions (URL-based security)
 * - @PreAuthorize throws exceptions in MVC-STACK (method-based security)
 * - **Filter Stack != MVC Stack** → AccessDeniedHandler CANNOT catch method security exceptions!
 *
 * **SOLUTION:**
 * - @ControllerAdvice operates in MVC-Stack and CAN catch @PreAuthorize exceptions
 * - Spring Security 6.3+ throws AuthorizationDeniedException (new class in 6.3+)
 * - We catch BOTH AccessDeniedException (legacy) and AuthorizationDeniedException (new)
 *
 * **Why This is Required:**
 * Without this advice, @PreAuthorize access denials result in 500 Internal Server Error
 * instead of proper 403 Forbidden responses with RFC 7807 ProblemDetail format.
 *
 * **CRITICAL:** @Order(HIGHEST_PRECEDENCE) ensures this handler runs BEFORE
 * ProblemDetailExceptionHandler's generic Exception handler (which returns 500).
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Profile("rbac-test")
class RbacTestAccessDeniedAdvice {
    /**
     * Handles legacy AccessDeniedException from @PreAuthorize.
     *
     * Returns RFC 7807 ProblemDetail with 403 Forbidden status.
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                ex.message ?: "Access Denied",
            )
        problemDetail.type = URI.create("https://eaf.axians.com/errors/access-denied")

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problemDetail)
    }

    /**
     * Handles Spring Security 6.3+ AuthorizationDeniedException from @PreAuthorize.
     *
     * Returns RFC 7807 ProblemDetail with 403 Forbidden status.
     */
    @ExceptionHandler(AuthorizationDeniedException::class)
    fun handleAuthorizationDenied(ex: AuthorizationDeniedException): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                ex.message ?: "Access Denied",
            )
        problemDetail.type = URI.create("https://eaf.axians.com/errors/access-denied")

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problemDetail)
    }
}
