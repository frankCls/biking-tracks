# Performance Optimization Plan: Speed Calculations

## Problem Statement

**Current Performance Issues** in `TemplateLiveProgram.kt` (lines 326-361):

1. **Expensive Collections Operations**: `takeLast()`, `map()`, `fold()`, `sumOf()` called every render frame
2. **Redundant Calculations**: Speed recalculated even when `done` list hasn't changed
3. **Memory Allocations**: New collections created every frame via `takeLast()`
4. **String Operations**: `String.format()` calls in render loop (7 calls per frame)

**Performance Impact**: These operations run at 60+ FPS, creating unnecessary CPU load.

## Current Code Analysis

```kotlin
// PERFORMANCE BOTTLENECK - runs every frame
val takeLast = done.takeLast(AppConstants.SPEED_CALCULATION_SAMPLE_SIZE)
takeLast
    .map { it.length * d }                    // New collection
    .fold(0.0) { acc, d -> acc + d }         // Iteration 1
    .let {
        speed = (takeLast.sumOf { it.length } / // Iteration 2
                (takeLast.last().time - takeLast.first().time)) * 
                AppConstants.SPEED_CONVERSION_FACTOR
    }

// STRING FORMATTING - 7 calls per frame
listOf(
    "${textPadded("total distance:")}${String.format("%.2f", tour.totalDistance / AppConstants.DISTANCE_CONVERSION_FACTOR)} km",
    "${textPadded("average speed:")}${String.format("%.2f", tour.averageSpeed)} km/h",
    // ... 5 more String.format() calls
)
```

## Implementation Plan

### Step 1: Design Caching Strategy

**Cache Key Strategy:**
- Track `done` list size and last point timestamp
- Only recalculate when cache is invalid
- Separate caches for speed vs. statistics strings

**Cache Structure:**
```kotlin
data class SpeedCalculationCache(
    val lastDoneSize: Int,
    val lastTimestamp: Long,
    val cachedSpeed: Double,
    val cachedStatistics: List<String>
)
```

### Step 2: Implement Change Detection

**Efficient Change Detection:**
- Compare `done.size` (O(1) operation)
- Compare `done.lastOrNull()?.time` for timestamp changes
- Skip all calculations if no changes detected

### Step 3: Optimize Collection Operations

**Replace Expensive Operations:**
- **Before**: `takeLast()` → creates new list
- **After**: Direct array access with indices
- **Before**: Multiple collection iterations (`map`, `fold`, `sumOf`)
- **After**: Single pass calculation

### Step 4: Cache String Formatting

**Pre-compute Static Strings:**
- Cache formatted strings until values change
- Use StringBuilder for complex formatting
- Separate dynamic vs. static string parts

### Step 5: Add Performance Monitoring

**Metrics Collection:**
- Track cache hit/miss ratio
- Measure calculation time before/after
- Monitor memory allocation reduction

## Expected Performance Gains

| **Optimization** | **Current Cost** | **Optimized Cost** | **Improvement** |
|------------------|------------------|--------------------|-----------------|
| Speed Calculation | Every frame | Cache miss only | **90% reduction** |
| Collection Ops | 4 operations/frame | 1 operation/cache miss | **75% reduction** |
| String Formatting | 7 calls/frame | Cache miss only | **95% reduction** |
| Memory Allocation | New lists/frame | Minimal allocation | **80% reduction** |

**Overall Expected Gain**: **60-80% faster statistics rendering**

## Implementation Steps

### Phase 1: Cache Infrastructure ✅
1. Create `SpeedCalculationCache` data class
2. Add cache state variables to main program
3. Implement change detection logic

### Phase 2: Optimize Calculations
1. Replace `takeLast()` with direct indexing
2. Single-pass speed calculation
3. Implement cache invalidation

### Phase 3: String Optimization
1. Cache formatted statistics strings
2. Update only changed values
3. Pre-compute static parts

### Phase 4: Testing & Validation
1. Performance benchmarking
2. Correctness validation
3. Memory usage analysis

## Technical Details

### Current Performance Bottlenecks

```kotlin
// PROBLEM: Multiple expensive operations every frame
val takeLast = done.takeLast(SAMPLE_SIZE)  // O(n) + allocation
takeLast.map { it.length * d }             // O(n) + allocation
         .fold(0.0) { acc, d -> acc + d }   // O(n)
         .let { 
             speed = (takeLast.sumOf { it.length } /  // O(n) again!
                     (takeLast.last().time - takeLast.first().time)) * FACTOR
         }
```

### Optimized Approach

```kotlin
// SOLUTION: Cache + direct indexing + single pass
if (needsRecalculation(done)) {
    val startIdx = maxOf(0, done.size - SAMPLE_SIZE)
    val endIdx = done.size - 1
    
    // Single pass calculation
    var totalDistance = 0.0
    var totalTime = 0L
    
    for (i in startIdx..endIdx) {
        totalDistance += done[i].length
        if (i == startIdx) totalTime -= done[i].time
        if (i == endIdx) totalTime += done[i].time
    }
    
    speed = (totalDistance / totalTime) * SPEED_CONVERSION_FACTOR
    updateCache(done.size, done.last().time, speed)
}
```

## Files to Modify

1. **`TemplateLiveProgram.kt`**: Main optimization target
2. **New: `performance/SpeedCalculationCache.kt`**: Cache infrastructure
3. **`config/AppConstants.kt`**: Add performance-related constants

## Success Metrics

- [ ] Cache hit rate > 90%
- [ ] Speed calculation time reduced by 60%+
- [ ] String formatting time reduced by 80%+
- [ ] Memory allocation reduced by 70%+
- [ ] No functional regressions
- [ ] Smooth 60+ FPS maintained

## Notes

This optimization addresses **Point 2** from the performance analysis: "Redundant Speed Calculations" in the render loop. The goal is to eliminate unnecessary computations while maintaining exact same functionality and visual output.