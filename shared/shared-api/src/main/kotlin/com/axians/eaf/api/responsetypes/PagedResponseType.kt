package com.axians.eaf.api.responsetypes

import com.axians.eaf.api.widget.dto.PagedResponse
import org.axonframework.messaging.responsetypes.ResponseType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Custom Axon Framework ResponseType that correctly handles matching for
 * generic PagedResponse<T> types, overcoming JVM type erasure.
 *
 * **Story 9.2 Solution**: This class is essential for querying handlers that return
 * a parameterized PagedResponse, as the default ResponseTypes.instanceOf(PagedResponse::class.java)
 * loses the generic type information and fails to match the handler.
 *
 * **Background**: Axon Framework 4.12 only auto-resolves generics for Collection, Future, and Optional.
 * Custom generic wrappers like PagedResponse<T> require this custom ResponseType implementation.
 *
 * @param T The expected element type within the PagedResponse content list.
 */
class PagedResponseType<T> private constructor(
    private val elementType: Class<T>,
) : ResponseType<PagedResponse<T>> {
    /**
     * Matches the provided handler's responseType against this PagedResponseType.
     * Returns true if the handler's response is a ParameterizedType of
     * PagedResponse with a generic argument that is an exact match or a subtype
     * of our expected response type.
     *
     * @param responseType The Type of the query handler's return value.
     * @return `true` if the types match, `false` otherwise.
     */
    override fun matches(responseType: Type): Boolean =
        when (responseType) {
            is ParameterizedType -> matchesParameterizedType(responseType)
            is Class<*> -> PagedResponse::class.java.isAssignableFrom(responseType)
            else -> false
        }

    private fun matchesParameterizedType(responseType: ParameterizedType): Boolean {
        val rawType = responseType.rawType
        val isRawTypeValid =
            rawType == PagedResponse::class.java ||
                (rawType is Class<*> && PagedResponse::class.java.isAssignableFrom(rawType))

        val typeArguments = responseType.actualTypeArguments
        return isRawTypeValid && typeArguments.isNotEmpty() && matchesElementType(typeArguments[0])
    }

    private fun matchesElementType(argType: Type): Boolean =
        when (argType) {
            is Class<*> -> elementType.isAssignableFrom(argType)
            is ParameterizedType -> elementType.isAssignableFrom(argType.rawType as Class<*>)
            else -> false
        }

    override fun responseMessagePayloadType(): Class<PagedResponse<T>> {
        @Suppress("UNCHECKED_CAST")
        return PagedResponse::class.java as Class<PagedResponse<T>>
    }

    override fun getExpectedResponseType(): Class<*> = PagedResponse::class.java

    /**
     * Converts the response from a query handler into the strongly-typed PagedResponse<T>.
     * This method should only be called after a successful matches() check.
     *
     * @param response The raw response object from the handler.
     * @return A safely casted PagedResponse<T>, or null if the input is null.
     */
    @Suppress("UNCHECKED_CAST")
    override fun convert(response: Any?): PagedResponse<T>? = response as PagedResponse<T>?

    override fun toString(): String = "PagedResponseType{$expectedResponseType}"

    companion object {
        /**
         * Factory method to create an instance of PagedResponseType.
         * This provides a clean, expressive API for use at the query dispatch site.
         *
         * **Usage**: `PagedResponseType.pagedInstanceOf(WidgetResponse::class.java)`
         *
         * @param T The element type of the paged response.
         * @param elementType The class of the element type.
         * @return A new instance of PagedResponseType.
         */
        @JvmStatic
        fun <T> pagedInstanceOf(elementType: Class<T>): PagedResponseType<T> = PagedResponseType(elementType)
    }
}
