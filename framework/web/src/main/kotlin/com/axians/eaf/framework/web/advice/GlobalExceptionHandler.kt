package com.axians.eaf.framework.web.advice

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import java.net.URI
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleValidationError(
        ex: IllegalArgumentException,
        request: WebRequest,
    ): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.message ?: "Validation failed",
            )
        problemDetail.title = "Validation Error"
        problemDetail.type = URI.create("/problems/validation-error")
        problemDetail.setProperty("timestamp", Instant.now())
        problemDetail.setProperty("path", request.getDescription(false))

        return ResponseEntity.badRequest().body(problemDetail)
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
