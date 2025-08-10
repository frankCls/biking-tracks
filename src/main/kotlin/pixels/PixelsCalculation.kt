package pixels

import org.openrndr.math.Vector2
import kotlin.math.cos
import kotlin.math.sqrt

import config.AppConstants

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
): Pair<Double, Double> {
    val x0 = x / scale + translation.x
    val y0 = y / scale + translation.y
    val longitude = centerLongitude + x0 / (EARTH_RADIUS * aspectRatio)
    val latitude = centerLatitude + y0 / EARTH_RADIUS
    return Pair(Math.toDegrees(latitude), Math.toDegrees(longitude))
}






fun calculatePixelCoordinate(latitude: Double, longitude: Double, aspectRatio: Double): Pair<Double, Double> {
    return Pair(
        EARTH_RADIUS * Math.toRadians(longitude) * aspectRatio,
        EARTH_RADIUS * Math.toRadians(latitude)
    )
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
): PixelsTransformation {

    val gpsPointsList = gpsPoints.values.flatten()
    val latitudes = gpsPointsList.map { Math.toRadians(it.latitude) }
    val centerLatitude = latitudes.average() // latitude close to the center of the map (φ0).
    val centerLongitude =
        gpsPointsList.map { Math.toRadians(it.longitude) }.average() // longitude close to the center of the map (λ0).
    val aspectRatio = cos(centerLatitude)

    val coordinates = gpsPoints.values.map { tour ->
        var tempDistance = 0.0
        val startTime = tour.first().time

        tour.zipWithNext { a, b ->
            val (x, y) = calculatePixelCoordinate(b.latitude, b.longitude, aspectRatio)
            val time = b.time
            val (previousX, previousY) = calculatePixelCoordinate(a.latitude, a.longitude, aspectRatio)
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
        }
    }

    val flattenedCoordinates = coordinates.flatten()
    val xCoordinates = flattenedCoordinates.map { it.position.x }
    val left = xCoordinates.min()
    val right = xCoordinates.max()

    val yCoordinates = flattenedCoordinates.map { it.position.y }
    val top = yCoordinates.min()
    val bottom = yCoordinates.max()

    return PixelsTransformation(
        routes = coordinates.map {
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
                totalTime = it.last().time - it.first().time
            )
        },
        width = (right - left) * scale,
        height = (bottom - top) * scale,
        scale = scale,
        aspectRatio = aspectRatio,
        centerLatitude = centerLatitude,
        centerLongitude = centerLongitude
    )
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
