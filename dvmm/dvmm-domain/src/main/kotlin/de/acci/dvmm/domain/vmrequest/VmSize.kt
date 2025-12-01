package de.acci.dvmm.domain.vmrequest

/**
 * Predefined VM size configurations.
 *
 * Each size maps to specific resource allocations and provides
 * consistent pricing/resource tiers across all VM requests.
 *
 * @property cpuCores Number of virtual CPU cores
 * @property memoryGb Amount of RAM in gigabytes
 * @property diskGb Default disk size in gigabytes
 */
public enum class VmSize(
    public val cpuCores: Int,
    public val memoryGb: Int,
    public val diskGb: Int
) {
    /** Small: 2 vCPU, 4 GB RAM, 50 GB disk - Development/Testing */
    S(cpuCores = 2, memoryGb = 4, diskGb = 50),

    /** Medium: 4 vCPU, 8 GB RAM, 100 GB disk - Standard workloads */
    M(cpuCores = 4, memoryGb = 8, diskGb = 100),

    /** Large: 8 vCPU, 16 GB RAM, 200 GB disk - Heavy workloads */
    L(cpuCores = 8, memoryGb = 16, diskGb = 200),

    /** Extra Large: 16 vCPU, 32 GB RAM, 500 GB disk - Database/High-performance */
    XL(cpuCores = 16, memoryGb = 32, diskGb = 500);

    public companion object {
        /**
         * Safely parse a size code string.
         *
         * @param code The size code (case-insensitive)
         * @return Result containing the VmSize or an error
         */
        public fun fromCode(code: String): Result<VmSize> {
            val upperCode = code.uppercase().trim()
            return entries.find { it.name == upperCode }
                ?.let { Result.success(it) }
                ?: Result.failure(
                    IllegalArgumentException(
                        "Invalid VM size: '$code'. Valid sizes are: ${entries.joinToString { it.name }}"
                    )
                )
        }
    }
}
