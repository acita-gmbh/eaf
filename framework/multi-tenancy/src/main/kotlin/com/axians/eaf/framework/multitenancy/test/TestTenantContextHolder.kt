package com.axians.eaf.framework.multitenancy.test

/**
 * Thread-safe holder for test tenant ID, enabling @BeforeEach pattern to work with MockMvc.
 *
 * **Problem:** @BeforeEach sets TenantContext in TEST thread, but MockMvc runs in REQUEST thread.
 * ThreadLocal doesn't cross thread boundaries, and InheritableThreadLocal doesn't work with thread pools!
 *
 * **Solution:** Uses a simple volatile variable that both test thread and request thread can access.
 * Thread-safe for test isolation (tests run sequentially by default in Gradle).
 *
 * **Usage:**
 * ```kotlin
 * @BeforeEach
 * fun beforeEach() {
 *     TestTenantContextHolder.setTestTenantId("test-tenant")
 * }
 *
 * @AfterEach
 * fun afterEach() {
 *     TestTenantContextHolder.clearTestTenantId()
 * }
 * ```
 *
 * **How It Works:**
 * 1. Test calls TestTenantContextHolder.setTestTenantId() in test thread
 * 2. Value stored in volatile variable (visible across all threads)
 * 3. TestTenantContextPropagationFilter reads value in request thread
 * 4. Filter sets actual TenantContext in request thread
 * 5. Query Handlers see TenantContext and work correctly!
 *
 * **Thread Safety:** Tests run sequentially, so no concurrent access to this holder.
 *
 * **Story 4.6 AC7:** Enables @BeforeEach pattern to work with MockMvc (zero test changes!).
 */
object TestTenantContextHolder {
    /**
     * Volatile ensures visibility across threads (test thread → request thread).
     * Simple static variable works because tests run sequentially.
     */
    @Volatile
    private var testTenantId: String? = null

    fun setTestTenantId(tenantId: String) {
        testTenantId = tenantId
    }

    fun getTestTenantId(): String? = testTenantId

    fun clearTestTenantId() {
        testTenantId = null
    }
}
