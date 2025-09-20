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

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeError(request: WebRequest): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
            )
        problemDetail.title = "Internal Server Error"
        problemDetail.type = URI.create("/problems/server-error")
        problemDetail.setProperty("timestamp", Instant.now())
        problemDetail.setProperty("path", request.getDescription(false))

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericError(request: WebRequest): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An error occurred while processing the request",
            )
        problemDetail.title = "Server Error"
        problemDetail.type = URI.create("/problems/generic-error")
        problemDetail.setProperty("timestamp", Instant.now())
        problemDetail.setProperty("path", request.getDescription(false))

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail)
    }
}
