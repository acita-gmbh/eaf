# Story 3.1.2: VCF SDK 9.0 Adapter (OBSOLETE)

Status: obsolete

## Superseded By

**Story 3.1.1** pivoted to use VCF SDK 9.0 directly from Maven Central, making this story obsolete.

### Original Plan (Pre-Pivot)

1. **Story 3.1.1:** Use vSphere Automation SDK 8.0.3 (supports vSphere 7.x/8.x)
2. **Story 3.1.2:** Add VCF SDK 9.0 adapter when vSphere 9.x market share >15%

### Actual Implementation (Post-Pivot)

1. **Story 3.1.1:** Uses VCF SDK 9.0 from Maven Central (Apache 2.0 license)
   - VCF SDK 9.0's `vim25` artifact provides SOAP API backwards compatibility
   - Works with vSphere 7.x, 8.x, and future 9.x (SOAP API is version-agnostic)
   - No private Maven repository needed (available on Maven Central)

### Why This Story Is Obsolete

The original rationale for Story 3.1.2 was:
- SDK 8.0.3 would be used initially (supports vSphere 7.x/8.x)
- VCF SDK 9.0 would be added later for vSphere 9.x

Since Story 3.1.1 now uses VCF SDK 9.0 directly:
- No second SDK adapter is needed
- Single SDK (VCF SDK 9.0) covers all vSphere versions via SOAP API
- Runtime version detection/adapter selection is unnecessary

### Future Considerations

If vSphere 9.x introduces **breaking changes** to the SOAP API (unlikely but possible), a new story should be created at that time to address specific compatibility issues. This story should NOT be reactivated - create a fresh story with current context.

## Story Completion Status

**Status:** obsolete (superseded by Story 3.1.1 pivot to VCF SDK 9.0)

**Action:** No further work required. This document retained for historical context.
