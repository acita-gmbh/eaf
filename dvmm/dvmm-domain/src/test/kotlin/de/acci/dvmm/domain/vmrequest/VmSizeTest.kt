package de.acci.dvmm.domain.vmrequest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class VmSizeTest {

    @ParameterizedTest
    @CsvSource(
        "S, 2, 4, 50",
        "M, 4, 8, 100",
        "L, 8, 16, 200",
        "XL, 16, 32, 500"
    )
    fun `should have correct resource specifications`(
        sizeName: String,
        expectedCpu: Int,
        expectedMemory: Int,
        expectedDisk: Int
    ) {
        val size = VmSize.valueOf(sizeName)
        assertEquals(expectedCpu, size.cpuCores)
        assertEquals(expectedMemory, size.memoryGb)
        assertEquals(expectedDisk, size.diskGb)
    }

    @ParameterizedTest
    @ValueSource(strings = ["S", "M", "L", "XL", "s", "m", "l", "xl", " S ", " xl "])
    fun `should parse valid size codes case insensitively`(code: String) {
        val result = VmSize.fromCode(code)
        assertTrue(result.isSuccess, "Expected '$code' to be valid")
    }

    @ParameterizedTest
    @ValueSource(strings = ["XXL", "SMALL", "MEDIUM", "", "X", "LL"])
    fun `should reject invalid size codes`(code: String) {
        val result = VmSize.fromCode(code)
        assertTrue(result.isFailure, "Expected '$code' to be invalid")
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid VM size") == true)
    }

    @Test
    fun `should have exactly four sizes`() {
        assertEquals(4, VmSize.entries.size)
    }

    @Test
    fun `sizes should be ordered by resources ascending`() {
        val sizes = VmSize.entries
        for (i in 0 until sizes.size - 1) {
            assertTrue(
                sizes[i].cpuCores < sizes[i + 1].cpuCores,
                "CPU cores should increase: ${sizes[i].name} (${sizes[i].cpuCores}) < ${sizes[i + 1].name} (${sizes[i + 1].cpuCores})"
            )
            assertTrue(
                sizes[i].memoryGb < sizes[i + 1].memoryGb,
                "Memory should increase: ${sizes[i].name} < ${sizes[i + 1].name}"
            )
            assertTrue(
                sizes[i].diskGb < sizes[i + 1].diskGb,
                "Disk should increase: ${sizes[i].name} < ${sizes[i + 1].name}"
            )
        }
    }

    @Test
    fun `error message should list valid sizes`() {
        val result = VmSize.fromCode("INVALID")
        val errorMessage = result.exceptionOrNull()?.message
        assertTrue(errorMessage?.contains("S") == true)
        assertTrue(errorMessage?.contains("M") == true)
        assertTrue(errorMessage?.contains("L") == true)
        assertTrue(errorMessage?.contains("XL") == true)
    }
}
