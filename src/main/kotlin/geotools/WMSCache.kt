package geotools

import org.openrndr.draw.ColorBuffer
import java.awt.image.BufferedImage
import kotlin.math.abs

/**
 * Cache entry for WMS aerial view data
 */
data class WMSCacheEntry(
    val bounds: GeographicBounds,
    val dimensions: ImageDimensions,
    val image: BufferedImage,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Geographic bounds for cache key generation
 */
data class GeographicBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {
    /**
     * Check if bounds are similar within tolerance for cache hit
     */
    fun isSimilar(other: GeographicBounds, tolerance: Double = 0.001): Boolean {
        return abs(minX - other.minX) <= tolerance &&
               abs(minY - other.minY) <= tolerance &&
               abs(maxX - other.maxX) <= tolerance &&
               abs(maxY - other.maxY) <= tolerance
    }
    
    /**
     * Generate cache key string
     */
    fun toCacheKey(): String {
        return "bounds_${minX}_${minY}_${maxX}_${maxY}"
    }
}

/**
 * Image dimensions for cache key generation
 */
data class ImageDimensions(
    val width: Int,
    val height: Int
) {
    fun toCacheKey(): String {
        return "dims_${width}x${height}"
    }
}

/**
 * WMS cache manager for aerial view images
 */
class WMSCache {
    private val cache = mutableMapOf<String, WMSCacheEntry>()
    private val maxCacheAge = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
    
    /**
     * Generate complete cache key from bounds and dimensions
     */
    private fun generateCacheKey(bounds: GeographicBounds, dimensions: ImageDimensions): String {
        return "${bounds.toCacheKey()}_${dimensions.toCacheKey()}"
    }
    
    /**
     * Check if cache contains valid entry for given bounds and dimensions
     */
    fun contains(bounds: GeographicBounds, dimensions: ImageDimensions): Boolean {
        cleanExpiredEntries()
        
        // First try exact match
        val exactKey = generateCacheKey(bounds, dimensions)
        if (cache.containsKey(exactKey)) {
            return true
        }
        
        // Try similar bounds match
        return cache.values.any { entry ->
            entry.bounds.isSimilar(bounds) && 
            entry.dimensions == dimensions &&
            !isExpired(entry)
        }
    }
    
    /**
     * Get cached aerial view image
     */
    fun get(bounds: GeographicBounds, dimensions: ImageDimensions): BufferedImage? {
        cleanExpiredEntries()
        
        // First try exact match
        val exactKey = generateCacheKey(bounds, dimensions)
        cache[exactKey]?.let { entry ->
            if (!isExpired(entry)) {
                return entry.image
            }
        }
        
        // Try similar bounds match
        return cache.values.firstOrNull { entry ->
            entry.bounds.isSimilar(bounds) && 
            entry.dimensions == dimensions &&
            !isExpired(entry)
        }?.image
    }
    
    /**
     * Store aerial view image in cache
     */
    fun put(bounds: GeographicBounds, dimensions: ImageDimensions, image: BufferedImage) {
        val key = generateCacheKey(bounds, dimensions)
        val entry = WMSCacheEntry(bounds, dimensions, image)
        cache[key] = entry
        cleanExpiredEntries()
    }
    
    /**
     * Clear all cached entries
     */
    fun clear() {
        cache.clear()
    }
    
    /**
     * Get cache statistics
     */
    fun getStats(): CacheStats {
        cleanExpiredEntries()
        return CacheStats(
            totalEntries = cache.size,
            oldestEntry = cache.values.minByOrNull { it.timestamp }?.timestamp,
            newestEntry = cache.values.maxByOrNull { it.timestamp }?.timestamp
        )
    }
    
    /**
     * Check if cache entry is expired
     */
    private fun isExpired(entry: WMSCacheEntry): Boolean {
        return System.currentTimeMillis() - entry.timestamp > maxCacheAge
    }
    
    /**
     * Remove expired entries from cache
     */
    private fun cleanExpiredEntries() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = cache.filterValues { entry ->
            currentTime - entry.timestamp > maxCacheAge
        }.keys
        
        expiredKeys.forEach { cache.remove(it) }
    }
}

/**
 * Cache statistics data class
 */
data class CacheStats(
    val totalEntries: Int,
    val oldestEntry: Long?,
    val newestEntry: Long?
)

/**
 * Global WMS cache instance
 */
object GlobalWMSCache {
    val instance = WMSCache()
}