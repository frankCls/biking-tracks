package pixels

import org.openrndr.math.Vector2
import kotlin.math.cos
import kotlin.math.sqrt

private const val EARTH_RADIUS = 6_371_000.0 // meters

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

// calcalute the opposite as in calculatePixelCoordinate
fun calculateRealCoordinate(x: Double, y: Double, aspectRatio: Double): Pair<Double, Double> {
    return Pair(
        Math.toDegrees(y / EARTH_RADIUS),
        Math.toDegrees(x / (EARTH_RADIUS * aspectRatio))
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
    height: Int,
    border: Double = 0.0
): PixelsTransformation {

    val gpsPointsList = gpsPoints.values.flatten()
    val latitudes = gpsPointsList.map { Math.toRadians(it.latitude) }
    val centerLatitude = latitudes.average() // latitude close to the center of the map (φ0).
    val centerLongitude =
        gpsPointsList.map { Math.toRadians(it.longitude) }.average() // longitude close to the center of the map (λ0).
    val aspectRatio = cos(centerLatitude)

    val coordinates = gpsPoints.entries.map { entry ->
        val tour = entry.value
        var tempDistance = 0.0
        val startTime = tour.first().time
        Pair(
            entry.key,
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
        )
    }

    val flattenedCoordinates = coordinates.map { it.second }.flatten()
    val xCoordinates = flattenedCoordinates.map { it.position.x }
    val left = xCoordinates.min()
    val right = xCoordinates.max()

    val yCoordinates = flattenedCoordinates.map { it.position.y }
    val top = yCoordinates.min()
    val bottom = yCoordinates.max()
//    val newScale = scale(width.toDouble(), height.toDouble(), Vector2(left, top), Vector2(right, bottom))
    val calculatedScale = scale(width.toDouble(), height.toDouble(), Vector2(left, top), Vector2(right, bottom))
    val newScale = calculatedScale
    println("new scale: $calculatedScale")

    return PixelsTransformation(
        segments = coordinates.map {
            val pointList = it.second
            Route(
                points = pointList.map { coordinate ->
                    val x = (coordinate.position.x - (left + border)) * newScale
                    val y = height - (coordinate.position.y - (top + border)) * newScale
                    Point(
                        position = Vector2(x, y),
                        coordinate.time,
                        coordinate.length,
                        coordinate.distance,
                        coordinate.realCoordinates,
                        coordinate.elevation
                    )
                },
                totalTime = pointList.last().time - pointList.first().time,
                name = it.first
            )
        },
        width = (right - left - (border * 2)) * newScale,
        height = (bottom - top - (border * 2)) * newScale,
        scale = newScale,
        aspectRatio = aspectRatio,
        centerLatidude = centerLatitude,
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

class Route(val points: List<Point>, val totalTime: Long = 0L, name: String)
class PixelsTransformation(
    val segments: List<Route>,
    val width: Double,
    val height: Double,
    val scale: Double = 1.0,
    val aspectRatio: Double = width / height,
    val centerLatidude: Double = 0.0,
    val centerLongitude: Double = 0.0
)


//fun scale(width: Double, height: Double, topLeft:Vector2, bottomRight:Vector2):Double {
//    //calculate scaling factor for rectangle with top left and bottom right coordinates that should fit in the given width and height
//    val xScale = width / (bottomRight.x - topLeft.x)
//}

// provide a function that calculates how to scale a rectangle with top left and bottom right coordinates to fit in a rectangle with the given width and height, respecting the aspect ratio of both the rectangles.
fun scale(width: Double, height: Double, topLeft: Vector2, bottomRight: Vector2): Double {
    val aspectRatio = width / height
    val otherAspectRatio = (bottomRight.x - topLeft.x) / (bottomRight.y - topLeft.y)
    return if (aspectRatio > otherAspectRatio) {
        height / (bottomRight.y - topLeft.y)
    } else {
        width / (bottomRight.x - topLeft.x)
    }
}


