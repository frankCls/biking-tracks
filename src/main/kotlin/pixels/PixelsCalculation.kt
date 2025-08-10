package pixels

import org.openrndr.math.Vector2
import kotlin.math.cos
import kotlin.math.sqrt

import config.AppConstants
import validation.CoordinateValidator
import validation.ValidationResult

private const val EARTH_RADIUS = AppConstants.EARTH_RADIUS

/**
 * calculates a latitude and longitude from a pixel coordinate given the translation, the center coordinates of the map, the center coordinate,
 * the aspect ratio and the scale.
 */
fun calculateCoordinates(
    x: Double,
    y: Double,
    translation: Vector2,
    centerLatitude: Double,
    centerLongitude: Double,
    aspectRatio: Double,
    scale: Double
): ValidationResult<Pair<Double, Double>> {
    // Validate all input parameters
    CoordinateValidator.validateFiniteNumber(x, "x coordinate").let {
        if (it.isFailure) return ValidationResult.Failure("Input validation failed: ${it.getErrorOrNull()}")
    }
    
    CoordinateValidator.validateFiniteNumber(y, "y coordinate").let {
        if (it.isFailure) return ValidationResult.Failure("Input validation failed: ${it.getErrorOrNull()}")
    }
    
    CoordinateValidator.validateTransformationParameters(centerLatitude, centerLongitude, aspectRatio, scale).let {
        if (it.isFailure) return ValidationResult.Failure("Parameter validation failed: ${it.getErrorOrNull()}")
    }
    
    try {
        val x0 = x / scale + translation.x
        val y0 = y / scale + translation.y
        val longitude = centerLongitude + x0 / (EARTH_RADIUS * aspectRatio)
        val latitude = centerLatitude + y0 / EARTH_RADIUS
        
        val latDegrees = Math.toDegrees(latitude)
        val lngDegrees = Math.toDegrees(longitude)
        
        // Validate resulting coordinates
        CoordinateValidator.validateLatitude(latDegrees).let {
            if (it.isFailure) return ValidationResult.Failure("Calculated latitude invalid: ${it.getErrorOrNull()}")
        }
        
        CoordinateValidator.validateLongitude(lngDegrees).let {
            if (it.isFailure) return ValidationResult.Failure("Calculated longitude invalid: ${it.getErrorOrNull()}")
        }
        
        return ValidationResult.Success(Pair(latDegrees, lngDegrees))
    } catch (e: Exception) {
        return ValidationResult.Failure("Calculation error: ${e.message}")
    }
}






fun calculatePixelCoordinate(latitude: Double, longitude: Double, aspectRatio: Double): ValidationResult<Pair<Double, Double>> {
    // Validate input coordinates
    CoordinateValidator.validateLatitude(latitude).let {
        if (it.isFailure) return ValidationResult.Failure("Latitude validation failed: ${it.getErrorOrNull()}")
    }
    
    CoordinateValidator.validateLongitude(longitude).let {
        if (it.isFailure) return ValidationResult.Failure("Longitude validation failed: ${it.getErrorOrNull()}")
    }
    
    CoordinateValidator.validateAspectRatio(aspectRatio).let {
        if (it.isFailure) return ValidationResult.Failure("Aspect ratio validation failed: ${it.getErrorOrNull()}")
    }
    
    try {
        val x = EARTH_RADIUS * Math.toRadians(longitude) * aspectRatio
        val y = EARTH_RADIUS * Math.toRadians(latitude)
        
        // Validate resulting pixel coordinates are finite
        CoordinateValidator.validateFiniteNumber(x, "calculated x coordinate").let {
            if (it.isFailure) return ValidationResult.Failure("Result validation failed: ${it.getErrorOrNull()}")
        }
        
        CoordinateValidator.validateFiniteNumber(y, "calculated y coordinate").let {
            if (it.isFailure) return ValidationResult.Failure("Result validation failed: ${it.getErrorOrNull()}")
        }
        
        return ValidationResult.Success(Pair(x, y))
    } catch (e: Exception) {
        return ValidationResult.Failure("Pixel coordinate calculation error: ${e.message}")
    }
}

data class GpsPoint(val latitude: Double, val longitude: Double, val time: Long, val elevation: Double)

/**
 * Converts a list of points to a list of coordinates (x, y) in meters.
 * @param gpsPoints a list of points.
 * @return the list of coordinates (x, y) in meters.
 *
 *  x = r λ cos(φ0)
 *  y = r φ
 *  where r is the radius of the globe, λ is the longitude, and φ is the latitude.
 *  see https://en.wikipedia.org/wiki/Equirectangular_projection
 */
fun transformToPixelCoordinates(
    gpsPoints: Map<String, List<GpsPoint>>,
    scale: Double,
    width: Int,
    height: Int
): ValidationResult<PixelsTransformation> {
    
    // Validate input parameters
    CoordinateValidator.validateScale(scale).let {
        if (it.isFailure) return ValidationResult.Failure("Scale validation failed: ${it.getErrorOrNull()}")
    }
    
    CoordinateValidator.validateDimension(width, "width").let {
        if (it.isFailure) return ValidationResult.Failure("Width validation failed: ${it.getErrorOrNull()}")
    }
    
    CoordinateValidator.validateDimension(height, "height").let {
        if (it.isFailure) return ValidationResult.Failure("Height validation failed: ${it.getErrorOrNull()}")
    }
    
    if (gpsPoints.isEmpty()) {
        return ValidationResult.Failure("GPS points map cannot be empty")
    }
    
    try {

        val gpsPointsList = gpsPoints.values.flatten()
        
        if (gpsPointsList.isEmpty()) {
            return ValidationResult.Failure("No GPS points found in input data")
        }
        
        // Validate all GPS points
        val invalidPoints = mutableListOf<String>()
        gpsPointsList.forEachIndexed { index, point ->
            val result = CoordinateValidator.validateGpsPoint(point)
            if (result.isFailure) {
                invalidPoints.add("Point $index: ${result.getErrorOrNull()}")
            }
        }
        
        if (invalidPoints.isNotEmpty()) {
            println("Warning: Found ${invalidPoints.size} invalid GPS points. Using valid points only.")
            // Log first few invalid points for debugging
            invalidPoints.take(5).forEach { println("  $it") }
        }
        
        // Filter to valid points only
        val validPoints = gpsPointsList.filter { point ->
            CoordinateValidator.validateGpsPoint(point).isValid
        }
        
        if (validPoints.isEmpty()) {
            return ValidationResult.Failure("No valid GPS points found after validation")
        }
        
        val latitudes = validPoints.map { Math.toRadians(it.latitude) }
        val centerLatitude = latitudes.average() // latitude close to the center of the map (φ0).
        val centerLongitude =
            validPoints.map { Math.toRadians(it.longitude) }.average() // longitude close to the center of the map (λ0).
        val aspectRatio = cos(centerLatitude)
        
        // Validate calculated parameters
        CoordinateValidator.validateFiniteNumber(centerLatitude, "center latitude").let {
            if (it.isFailure) return ValidationResult.Failure("Center latitude calculation failed: ${it.getErrorOrNull()}")
        }
        
        CoordinateValidator.validateFiniteNumber(centerLongitude, "center longitude").let {
            if (it.isFailure) return ValidationResult.Failure("Center longitude calculation failed: ${it.getErrorOrNull()}")
        }
        
        CoordinateValidator.validateAspectRatio(aspectRatio).let {
            if (it.isFailure) return ValidationResult.Failure("Aspect ratio calculation failed: ${it.getErrorOrNull()}")
        }

        val coordinates = gpsPoints.values.map { tour ->
            var tempDistance = 0.0
            val startTime = tour.firstOrNull()?.time ?: 0L
            val validTourPoints = tour.filter { CoordinateValidator.validateGpsPoint(it).isValid }
            
            if (validTourPoints.isEmpty()) {
                emptyList<Point>()
            } else {
                validTourPoints.zipWithNext { a, b ->
                    val pixelResult = calculatePixelCoordinate(b.latitude, b.longitude, aspectRatio)
                    val previousPixelResult = calculatePixelCoordinate(a.latitude, a.longitude, aspectRatio)
                    
                    if (pixelResult.isValid && previousPixelResult.isValid) {
                        val (x, y) = pixelResult.getOrNull()!!
                        val time = b.time
                        val (previousX, previousY) = previousPixelResult.getOrNull()!!
                        val dx = x - previousX
                        val dy = y - previousY
                        val length = sqrt(dx * dx + dy * dy)
                        tempDistance += length
                        Point(
                            position = Vector2(x, y),
                            time = time - startTime,
                            length = length,
                            distance = tempDistance,
                            realCoordinates = Vector2(a.latitude, a.longitude),
                            elevation = b.elevation
                        )
                    } else {
                        // Skip invalid points, but create a placeholder to maintain structure
                        Point(
                            position = Vector2.ZERO,
                            time = 0L,
                            length = 0.0,
                            distance = tempDistance,
                            realCoordinates = Vector2(a.latitude, a.longitude),
                            elevation = b.elevation
                        )
                    }
                }.filter { it.position != Vector2.ZERO }
            }
        }

        val flattenedCoordinates = coordinates.flatten()
        
        if (flattenedCoordinates.isEmpty()) {
            return ValidationResult.Failure("No valid coordinates found after processing GPS points")
        }
        
        val xCoordinates = flattenedCoordinates.map { it.position.x }
        val left = xCoordinates.min()
        val right = xCoordinates.max()

        val yCoordinates = flattenedCoordinates.map { it.position.y }
        val top = yCoordinates.min()
        val bottom = yCoordinates.max()
        
        // Validate bounds
        CoordinateValidator.validateFiniteNumber(left, "left bound").let {
            if (it.isFailure) return ValidationResult.Failure("Bounds calculation failed: ${it.getErrorOrNull()}")
        }
        CoordinateValidator.validateFiniteNumber(right, "right bound").let {
            if (it.isFailure) return ValidationResult.Failure("Bounds calculation failed: ${it.getErrorOrNull()}")
        }
        CoordinateValidator.validateFiniteNumber(top, "top bound").let {
            if (it.isFailure) return ValidationResult.Failure("Bounds calculation failed: ${it.getErrorOrNull()}")
        }
        CoordinateValidator.validateFiniteNumber(bottom, "bottom bound").let {
            if (it.isFailure) return ValidationResult.Failure("Bounds calculation failed: ${it.getErrorOrNull()}")
        }
        
        if (right <= left) {
            return ValidationResult.Failure("Invalid coordinate bounds: right ($right) must be greater than left ($left)")
        }
        
        if (bottom <= top) {
            return ValidationResult.Failure("Invalid coordinate bounds: bottom ($bottom) must be greater than top ($top)")
        }

        val transformation = PixelsTransformation(
            routes = coordinates.map {
                if (it.isEmpty()) {
                    Route(emptyList(), 0L)
                } else {
                    Route(
                        points = it.map { coordinate ->
                            val x = (coordinate.position.x - left) * scale
                            val y = height - (coordinate.position.y - top) * scale
                            Point(
                                position = Vector2(x, y),
                                coordinate.time,
                                coordinate.length,
                                coordinate.distance,
                                coordinate.realCoordinates,
                                coordinate.elevation
                            )
                        },
                        totalTime = if (it.size > 1) it.last().time - it.first().time else 0L
                    )
                }
            }.filter { it.points.isNotEmpty() }, // Filter out empty routes
            width = (right - left) * scale,
            height = (bottom - top) * scale,
            scale = scale,
            aspectRatio = aspectRatio,
            centerLatitude = centerLatitude,
            centerLongitude = centerLongitude
        )
        
        return ValidationResult.Success(transformation)
        
    } catch (e: Exception) {
        return ValidationResult.Failure("Transformation calculation error: ${e.message}")
    }
}

class Point(
    val position: Vector2 = Vector2.ZERO,
    val time: Long = 0L,
    val length: Double = 0.0,
    val distance: Double = 0.0,
    val realCoordinates: Vector2,
    val elevation: Double
)

class Route(val points: List<Point>, val totalTime: Long = 0L)
class PixelsTransformation(
    val routes: List<Route>,
    val width: Double,
    val height: Double,
    val scale: Double = 1.0,
    val aspectRatio: Double = width / height,
    val centerLatitude: Double = 0.0,
    val centerLongitude: Double = 0.0
)
