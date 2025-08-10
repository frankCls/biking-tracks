package validation

import pixels.GpsPoint

/**
 * Result class for validation operations using the Result<T> pattern
 * @param T The type of the validated value
 */
sealed class ValidationResult<out T> {
    data class Success<T>(val value: T) : ValidationResult<T>()
    data class Failure(val error: String) : ValidationResult<Nothing>()
    
    val isValid: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
    
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }
    
    fun getErrorOrNull(): String? = when (this) {
        is Success -> null
        is Failure -> error
    }
}

/**
 * Comprehensive coordinate validation utility
 * Provides validation for GPS coordinates, bounds, scale factors, and other numeric inputs
 * used in coordinate transformation functions.
 */
object CoordinateValidator {
    
    // Constants for validation ranges
    const val MIN_LATITUDE = -90.0
    const val MAX_LATITUDE = 90.0
    const val MIN_LONGITUDE = -180.0
    const val MAX_LONGITUDE = 180.0
    const val MIN_SCALE = 0.000001 // Very small positive number
    const val MAX_SCALE = 1000000.0 // Very large scale factor
    const val MIN_DIMENSION = 1
    const val MAX_DIMENSION = 100000
    
    /**
     * Validates latitude coordinate
     * @param lat Latitude value to validate
     * @return ValidationResult with validated latitude or error message
     */
    fun validateLatitude(lat: Double): ValidationResult<Double> {
        return when {
            !lat.isFinite() -> ValidationResult.Failure("Latitude must be a finite number, got: $lat")
            lat < MIN_LATITUDE -> ValidationResult.Failure("Latitude must be >= $MIN_LATITUDE degrees, got: $lat")
            lat > MAX_LATITUDE -> ValidationResult.Failure("Latitude must be <= $MAX_LATITUDE degrees, got: $lat")
            else -> ValidationResult.Success(lat)
        }
    }
    
    /**
     * Validates longitude coordinate
     * @param lng Longitude value to validate
     * @return ValidationResult with validated longitude or error message
     */
    fun validateLongitude(lng: Double): ValidationResult<Double> {
        return when {
            !lng.isFinite() -> ValidationResult.Failure("Longitude must be a finite number, got: $lng")
            lng < MIN_LONGITUDE -> ValidationResult.Failure("Longitude must be >= $MIN_LONGITUDE degrees, got: $lng")
            lng > MAX_LONGITUDE -> ValidationResult.Failure("Longitude must be <= $MAX_LONGITUDE degrees, got: $lng")
            else -> ValidationResult.Success(lng)
        }
    }
    
    /**
     * Validates bounding box coordinates
     * @param left Left boundary (longitude)
     * @param top Top boundary (latitude) 
     * @param right Right boundary (longitude)
     * @param bottom Bottom boundary (latitude)
     * @return ValidationResult with success or error message
     */
    fun validateBounds(left: Double, top: Double, right: Double, bottom: Double): ValidationResult<Unit> {
        // First validate individual coordinates
        validateLongitude(left).let { if (it.isFailure) return ValidationResult.Failure("Left bound invalid: ${it.getErrorOrNull()}") }
        validateLatitude(top).let { if (it.isFailure) return ValidationResult.Failure("Top bound invalid: ${it.getErrorOrNull()}") }
        validateLongitude(right).let { if (it.isFailure) return ValidationResult.Failure("Right bound invalid: ${it.getErrorOrNull()}") }
        validateLatitude(bottom).let { if (it.isFailure) return ValidationResult.Failure("Bottom bound invalid: ${it.getErrorOrNull()}") }
        
        // Then validate logical relationships
        return when {
            left >= right -> ValidationResult.Failure("Left bound ($left) must be less than right bound ($right)")
            top >= bottom -> ValidationResult.Failure("Top bound ($top) must be less than bottom bound ($bottom)")
            else -> ValidationResult.Success(Unit)
        }
    }
    
    /**
     * Validates scale factor
     * @param scale Scale factor to validate
     * @return ValidationResult with validated scale or error message
     */
    fun validateScale(scale: Double): ValidationResult<Double> {
        return when {
            !scale.isFinite() -> ValidationResult.Failure("Scale must be a finite number, got: $scale")
            scale <= 0.0 -> ValidationResult.Failure("Scale must be positive, got: $scale")
            scale < MIN_SCALE -> ValidationResult.Failure("Scale too small, minimum: $MIN_SCALE, got: $scale")
            scale > MAX_SCALE -> ValidationResult.Failure("Scale too large, maximum: $MAX_SCALE, got: $scale")
            else -> ValidationResult.Success(scale)
        }
    }
    
    /**
     * Validates aspect ratio
     * @param aspectRatio Aspect ratio to validate
     * @return ValidationResult with validated aspect ratio or error message
     */
    fun validateAspectRatio(aspectRatio: Double): ValidationResult<Double> {
        return when {
            !aspectRatio.isFinite() -> ValidationResult.Failure("Aspect ratio must be a finite number, got: $aspectRatio")
            aspectRatio <= 0.0 -> ValidationResult.Failure("Aspect ratio must be positive, got: $aspectRatio")
            else -> ValidationResult.Success(aspectRatio)
        }
    }
    
    /**
     * Validates dimension (width or height)
     * @param dimension Dimension value to validate
     * @param dimensionName Name of the dimension for error messages
     * @return ValidationResult with validated dimension or error message
     */
    fun validateDimension(dimension: Int, dimensionName: String = "dimension"): ValidationResult<Int> {
        return when {
            dimension < MIN_DIMENSION -> ValidationResult.Failure("$dimensionName must be >= $MIN_DIMENSION, got: $dimension")
            dimension > MAX_DIMENSION -> ValidationResult.Failure("$dimensionName must be <= $MAX_DIMENSION, got: $dimension")
            else -> ValidationResult.Success(dimension)
        }
    }
    
    /**
     * Validates a GPS point
     * @param point GPS point to validate
     * @return ValidationResult with validated GPS point or error message
     */
    fun validateGpsPoint(point: GpsPoint): ValidationResult<GpsPoint> {
        val latResult = validateLatitude(point.latitude)
        if (latResult.isFailure) {
            return ValidationResult.Failure("GPS point latitude invalid: ${latResult.getErrorOrNull()}")
        }
        
        val lngResult = validateLongitude(point.longitude)
        if (lngResult.isFailure) {
            return ValidationResult.Failure("GPS point longitude invalid: ${lngResult.getErrorOrNull()}")
        }
        
        // Validate elevation (allow negative values for below sea level)
        if (!point.elevation.isFinite()) {
            return ValidationResult.Failure("GPS point elevation must be finite, got: ${point.elevation}")
        }
        
        // Validate time (should be positive)
        if (point.time < 0) {
            return ValidationResult.Failure("GPS point time must be non-negative, got: ${point.time}")
        }
        
        return ValidationResult.Success(point)
    }
    
    /**
     * Validates a list of GPS points
     * @param points List of GPS points to validate
     * @return ValidationResult with validated GPS points or error message
     */
    fun validateGpsPoints(points: List<GpsPoint>): ValidationResult<List<GpsPoint>> {
        if (points.isEmpty()) {
            return ValidationResult.Failure("GPS points list cannot be empty")
        }
        
        val invalidPoints = mutableListOf<String>()
        points.forEachIndexed { index, point ->
            val result = validateGpsPoint(point)
            if (result.isFailure) {
                invalidPoints.add("Point $index: ${result.getErrorOrNull()}")
            }
        }
        
        return if (invalidPoints.isEmpty()) {
            ValidationResult.Success(points)
        } else {
            ValidationResult.Failure("Invalid GPS points found:\n${invalidPoints.joinToString("\n")}")
        }
    }
    
    /**
     * Validates numeric value for NaN and Infinity
     * @param value Numeric value to validate
     * @param valueName Name of the value for error messages
     * @return ValidationResult with validated value or error message
     */
    fun validateFiniteNumber(value: Double, valueName: String): ValidationResult<Double> {
        return when {
            value.isNaN() -> ValidationResult.Failure("$valueName cannot be NaN")
            value.isInfinite() -> ValidationResult.Failure("$valueName cannot be infinite")
            else -> ValidationResult.Success(value)
        }
    }
    
    /**
     * Validates coordinate transformation parameters
     * @param centerLatitude Center latitude for projection
     * @param centerLongitude Center longitude for projection
     * @param aspectRatio Aspect ratio for projection
     * @param scale Scale factor for transformation
     * @return ValidationResult with success or error message
     */
    fun validateTransformationParameters(
        centerLatitude: Double,
        centerLongitude: Double,
        aspectRatio: Double,
        scale: Double
    ): ValidationResult<Unit> {
        validateLatitude(centerLatitude).let { 
            if (it.isFailure) return ValidationResult.Failure("Center latitude invalid: ${it.getErrorOrNull()}") 
        }
        
        validateLongitude(centerLongitude).let { 
            if (it.isFailure) return ValidationResult.Failure("Center longitude invalid: ${it.getErrorOrNull()}") 
        }
        
        validateAspectRatio(aspectRatio).let { 
            if (it.isFailure) return ValidationResult.Failure("Aspect ratio invalid: ${it.getErrorOrNull()}") 
        }
        
        validateScale(scale).let { 
            if (it.isFailure) return ValidationResult.Failure("Scale invalid: ${it.getErrorOrNull()}") 
        }
        
        return ValidationResult.Success(Unit)
    }
}