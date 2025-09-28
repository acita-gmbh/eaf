# CodeRabbit Fix 3: JPA Dirty Checking - SKIPPED

## Nitpick
Lines 138-147: Prefer in-place mutation with JPA dirty checking instead of creating new instance via update()

## Analysis
WidgetProjection is implemented as immutable `data class` with `val` fields:
- Follows functional programming principles
- Uses copy() for updates (standard Kotlin pattern)
- update() method creates new instance with updated timestamp

## Why Skipped
1. **Intentional Design**: Immutability is a deliberate architectural choice
2. **Migration Scope**: This is migrated code, not greenfield rewrite
3. **Breaking Change**: Converting to mutable would require:
   - Changing all `val` to `var`
   - Removing data class pattern
   - Rewriting all projection handlers
   - Updating all tests
4. **Trade-offs**: 
   - Immutable: Thread-safe, predictable, functional
   - Mutable: Slightly less GC, JPA dirty checking
   
## Recommendation
Keep current immutable pattern. The performance difference is negligible for projection handlers (not high-frequency updates). Immutability provides better guarantees for event sourcing read models.

## Future Consideration
If performance profiling shows GC pressure from projection updates, reconsider as separate optimization story.
