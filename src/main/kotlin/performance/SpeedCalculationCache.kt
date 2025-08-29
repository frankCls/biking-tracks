package performance

import kotlin.math.abs

/**
 * Cache for speed calculations and formatted statistics strings to avoid
 * redundant computations in the render loop
 */
data class SpeedCalculationCache(
    val lastDoneSize: Int = 0,
    val lastTimestamp: Long = 0L,
    val cachedSpeed: Double = 0.0,
    val cachedStatistics: List<String> = emptyList(),
    val cacheTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Check if the cache is still valid for the current done list state
     */
    fun isValid(currentDoneSize: Int, currentTimestamp: Long): Boolean {
        return lastDoneSize == currentDoneSize && lastTimestamp == currentTimestamp
    }
    
    /**
     * Check if recalculation is needed based on done list changes
     */
    fun needsRecalculation(currentDoneSize: Int, currentTimestamp: Long): Boolean {
        return !isValid(currentDoneSize, currentTimestamp)
    }
}

/**
 * Statistics calculation cache for formatted strings
 */
data class StatisticsStringCache(
    val totalDistanceStr: String = "",
    val averageSpeedStr: String = "",
    val totalTimeStr: String = "",
    val currentDistanceStr: String = "",
    val currentTimeStr: String = "",
    val currentSpeedStr: String = "",
    val currentHeightStr: String = "",
    val lastUpdateTime: Long = 0L
) {
    /**
     * Create a list of formatted statistics strings
     */
    fun toStatisticsList(): List<String> {
        return listOf(
            totalDistanceStr,
            averageSpeedStr,
            totalTimeStr,
            currentDistanceStr,
            currentTimeStr,
            currentSpeedStr,
            currentHeightStr
        )
    }
}

/**
 * Performance metrics for monitoring cache effectiveness
 */
data class CachePerformanceMetrics(
    var cacheHits: Long = 0,
    var cacheMisses: Long = 0,
    var totalCalculations: Long = 0,
    var averageCalculationTime: Double = 0.0,
    private var totalCalculationTime: Long = 0
) {
    /**
     * Record a cache hit
     */
    fun recordHit() {
        cacheHits++
    }
    
    /**
     * Record a cache miss and calculation time
     */
    fun recordMiss(calculationTimeNs: Long) {
        cacheMisses++
        totalCalculations++
        totalCalculationTime += calculationTimeNs
        averageCalculationTime = totalCalculationTime.toDouble() / totalCalculations
    }
    
    /**
     * Get cache hit ratio as percentage
     */
    fun getHitRatio(): Double {
        val total = cacheHits + cacheMisses
        return if (total > 0) (cacheHits.toDouble() / total) * 100 else 0.0
    }
    
    /**
     * Get performance summary
     */
    fun getSummary(): String {
        return "Cache Performance: ${String.format("%.1f", getHitRatio())}% hit rate " +
               "(${cacheHits} hits, ${cacheMisses} misses), " +
               "avg calc time: ${String.format("%.2f", averageCalculationTime / 1_000_000)}ms"
    }
}

/**
 * Main speed calculation optimization manager
 */
class SpeedCalculationOptimizer {
    private var speedCache = SpeedCalculationCache()
    private var stringCache = StatisticsStringCache()
    private val metrics = CachePerformanceMetrics()
    
    /**
     * Calculate speed with caching - main optimization entry point
     */
    fun calculateSpeedOptimized(
        done: List<Any>, // Using Any to match the current structure
        sampleSize: Int,
        speedConversionFactor: Double,
        getCurrentTimestamp: (Any) -> Long,
        getCurrentDistance: (Any) -> Double
    ): Double {
        if (done.isEmpty()) return 0.0
        
        val currentSize = done.size
        val currentTimestamp = getCurrentTimestamp(done.last())
        
        // Check cache validity
        if (speedCache.isValid(currentSize, currentTimestamp)) {
            metrics.recordHit()
            return speedCache.cachedSpeed
        }
        
        // Cache miss - recalculate
        val startTime = System.nanoTime()
        val speed = calculateSpeedDirect(done, sampleSize, speedConversionFactor, getCurrentTimestamp, getCurrentDistance)
        val endTime = System.nanoTime()
        
        // Update cache
        speedCache = SpeedCalculationCache(
            lastDoneSize = currentSize,
            lastTimestamp = currentTimestamp,
            cachedSpeed = speed
        )
        
        metrics.recordMiss(endTime - startTime)
        return speed
    }
    
    /**
     * Direct speed calculation without collection operations
     * Single-pass algorithm replacing takeLast().map().fold().sumOf()
     */
    private fun calculateSpeedDirect(
        done: List<Any>,
        sampleSize: Int,
        speedConversionFactor: Double,
        getCurrentTimestamp: (Any) -> Long,
        getCurrentDistance: (Any) -> Double
    ): Double {
        if (done.size < 2) return 0.0
        
        val startIdx = maxOf(0, done.size - sampleSize)
        val endIdx = done.size - 1
        
        // Single pass calculation
        var totalDistance = 0.0
        val startTime = getCurrentTimestamp(done[startIdx])
        val endTime = getCurrentTimestamp(done[endIdx])
        
        // Sum distances in single pass
        for (i in startIdx..endIdx) {
            totalDistance += getCurrentDistance(done[i])
        }
        
        val totalTime = endTime - startTime
        return if (totalTime > 0) {
            (totalDistance / totalTime) * speedConversionFactor
        } else 0.0
    }
    
    /**
     * Get cache performance metrics
     */
    fun getMetrics(): CachePerformanceMetrics = metrics
    
    /**
     * Reset cache and metrics (useful for testing)
     */
    fun reset() {
        speedCache = SpeedCalculationCache()
        stringCache = StatisticsStringCache()
        metrics.cacheHits = 0
        metrics.cacheMisses = 0
        metrics.totalCalculations = 0
        metrics.averageCalculationTime = 0.0
    }
}