package com.axians.eaf.framework.web.advice

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import java.net.URI
import java.time.Instant
import java.util.concurrent.ExecutionException

@RestControllerAdvice
class GlobalExceptionHandler {
    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleValidationError(
        ex: IllegalArgumentException,
        request: WebRequest,
    ): ResponseEntity<ProblemDetail> {
        val message =
            if (ex.message?.contains("tenant context mismatch") == true) {
                "Access denied"
            } else {
                ex.message ?: "Validation failed"
            }

        val status =
            if (ex.message?.contains("tenant context mismatch") == true) {
                HttpStatus.FORBIDDEN
            } else {
                HttpStatus.BAD_REQUEST
            }

        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                status,
                message,
            )
        problemDetail.title = if (status == HttpStatus.FORBIDDEN) "Access Denied" else "Validation Error"
        problemDetail.type =
            URI.create(if (status == HttpStatus.FORBIDDEN) "/problems/access-denied" else "/problems/validation-error")
        problemDetail.setProperty("timestamp", Instant.now())
        problemDetail.setProperty("path", request.getDescription(false))

        return ResponseEntity.status(status).body(problemDetail)
    }

    @ExceptionHandler(org.axonframework.commandhandling.CommandExecutionException::class)
    fun handleCommandExecutionError(
        ex: org.axonframework.commandhandling.CommandExecutionException,
        request: WebRequest,
    ): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Command execution failed: ${ex.message}",
            )
        problemDetail.title = "Command Execution Error"
        problemDetail.type = URI.create("/problems/command-execution-error")
        problemDetail.setProperty("timestamp", Instant.now())
        problemDetail.setProperty("path", request.getDescription(false))

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail)
    }

    @ExceptionHandler(java.util.concurrent.TimeoutException::class)
    fun handleTimeoutError(
        ex: java.util.concurrent.TimeoutException,
        request: WebRequest,
    ): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.REQUEST_TIMEOUT,
                "Request timed out: ${ex.message}",
            )
        problemDetail.title = "Request Timeout"
        problemDetail.type = URI.create("/problems/timeout-error")
        problemDetail.setProperty("timestamp", Instant.now())
        problemDetail.setProperty("path", request.getDescription(false))

        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(problemDetail)
    }

    @ExceptionHandler(ExecutionException::class)
    fun handleExecutionException(
        ex: ExecutionException,
        request: WebRequest,
    ): ResponseEntity<ProblemDetail> {
        // Generate correlation ID for tracking
        val errorId =
            java.util.UUID
                .randomUUID()
                .toString()

        // Unwrap the ExecutionException to get the actual root cause
        val rootCause = ex.cause ?: ex
        val causeChain =
            buildString {
                appendLine("ExecutionException wrapper -> ${rootCause.javaClass.simpleName}")
                var current = rootCause
                while (current.cause != null && current.cause != current) {
                    current = current.cause!!
                    appendLine("  -> ${current.javaClass.simpleName}: ${current.message}")
                }
            }

        // Log full error details with correlation ID for debugging
        logger.error(
            "errorId={} ExecutionException caught in controller. Root cause: {} - {}. Full chain:\n{}",
            errorId,
            rootCause.javaClass.simpleName,
            rootCause.message,
            causeChain,
            rootCause,
        )

        // Return generic message to client with correlation ID (prevent information disclosure)
        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An internal error occurred. Please contact support with error ID: $errorId",
            )
        problemDetail.title = "Query Execution Error"
        problemDetail.type = URI.create("/problems/execution-error")
        problemDetail.setProperty("timestamp", Instant.now())
        problemDetail.setProperty("path", request.getDescription(false))
        problemDetail.setProperty("errorId", errorId)
        problemDetail.setProperty("rootCauseType", rootCause.javaClass.simpleName)

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericError(
        ex: Exception,
        request: WebRequest,
    ): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred: ${ex.javaClass.simpleName}",
            )
        problemDetail.title = "Server Error"
        problemDetail.type = URI.create("/problems/generic-error")
        problemDetail.setProperty("timestamp", Instant.now())
        problemDetail.setProperty("path", request.getDescription(false))
        problemDetail.setProperty("exceptionType", ex.javaClass.simpleName)

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail)
    }
}
