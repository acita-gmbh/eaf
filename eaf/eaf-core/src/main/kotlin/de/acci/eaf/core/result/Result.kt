package de.acci.eaf.core.result

/**
 * Minimal functional result type for domain and application layers.
 * Avoids unchecked exceptions; callers must handle success/failure explicitly.
 */
public sealed interface Result<out T, out E> {
    /** Success branch carrying a value. */
    public data class Success<T>(
        /** The success value */
        public val value: T
    ) : Result<T, Nothing>

    /** Failure branch carrying a domain-specific error. */
    public data class Failure<E>(
        /** The error details */
        public val error: E
    ) : Result<Nothing, E>
}

/**
 * Map the success branch; failures pass through unchanged.
 *
 * @param transform mapper applied only when this is [Result.Success]
 */
public fun <T, R, E> Result<T, E>.map(transform: (T) -> R): Result<R, E> =
    when (this) {
        is Result.Success -> Result.Success(transform(value))
        is Result.Failure -> this
    }

/**
 * FlatMap to another [Result], allowing chaining without exceptions.
 *
 * @param transform returns next [Result] when current is [Result.Success]
 */
public fun <T, R, E> Result<T, E>.flatMap(transform: (T) -> Result<R, E>): Result<R, E> =
    when (this) {
        is Result.Success -> transform(value)
        is Result.Failure -> this
    }

/**
 * Fold into a single value.
 *
 * @param onSuccess handler for [Result.Success]
 * @param onFailure handler for [Result.Failure]
 */
public fun <T, E, R> Result<T, E>.fold(
    onSuccess: (T) -> R,
    onFailure: (E) -> R
): R = when (this) {
        is Result.Success -> onSuccess(value)
        is Result.Failure -> onFailure(error)
    }

/**
 * Return contained value or compute fallback.
 *
 * @param defaultValue value produced when this is [Result.Failure]
 */
public fun <T, E> Result<T, E>.getOrElse(defaultValue: () -> T): T =
    when (this) {
        is Result.Success -> value
        is Result.Failure -> defaultValue()
    }

/**
 * Run side effect on success; returns original result.
 *
 * @param block side effect executed only on [Result.Success]
 */
public fun <T, E> Result<T, E>.onSuccess(block: (T) -> Unit): Result<T, E> =
    also { if (this is Result.Success) block(value) }

/**
 * Run side effect on failure; returns original result.
 *
 * @param block side effect executed only on [Result.Failure]
 */
public fun <T, E> Result<T, E>.onFailure(block: (E) -> Unit): Result<T, E> =
    also { if (this is Result.Failure) block(error) }

/**
 * Builder for the success branch.
 */
public fun <T> T.success(): Result<T, Nothing> = Result.Success(this)

/**
 * Builder for the failure branch.
 */
public fun <E> E.failure(): Result<Nothing, E> = Result.Failure(this)
