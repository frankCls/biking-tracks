package gpx

import io.jenetics.jpx.GPX
import io.jenetics.jpx.WayPoint
import org.openrndr.math.Vector2
import java.io.File
import java.nio.file.Files
import java.text.FieldPosition
import java.util.stream.Collectors
import kotlin.math.cos
import kotlin.math.sqrt


private const val EARTH_RADIUS = 6_371_000.0 // meters

/**
 * Converts a list of points to a list of coordinates (x, y) in meters.
 * @param files the list of files to convert
 * @return the list of coordinates (x, y) in meters.
 *
 *  x = r λ cos(φ0)
 *  y = r φ
 *  where r is the radius of the globe, λ is the longitude, and φ is the latitude.
 *  see https://en.wikipedia.org/wiki/Equirectangular_projection
 */
fun convertToCoordinates(directory: String, scale: Double, width: Int, height: Int): Conversion {

    val points = readWayPoints(directory)
    val flattenPoints = points.flatten()
    val latitudes = flattenPoints.map { it.latitude.toRadians() }
    val centerLatitude = latitudes.average() // latitude close to the center of the map (φ0).
    val aspectRatio = cos(centerLatitude)

    val r = EARTH_RADIUS
    val coordinates = points.map { tour ->
        var tempDistance = 0.0
        val startTime = tour.first().time.get().toEpochSecond()
        tour.mapIndexed { index, wayPoint ->
            val x = r * wayPoint.longitude.toRadians() * aspectRatio
            val y = r * wayPoint.latitude.toRadians()
            val time = wayPoint.time.get()
            val length = if(index == 0) {
                0.0
            } else {
                val previous = tour[index - 1]
                val previousX = r * previous.longitude.toRadians() * aspectRatio
                val previousY = r * previous.latitude.toRadians()
                val dx = x - previousX
                val dy = y - previousY
                sqrt(dx * dx + dy * dy)
            }
            tempDistance += length
            Point(
                position = Vector2(x, y),
                time=time.toEpochSecond() - startTime,
                length = length,
                distance = tempDistance,
                realCoordinates = Vector2(x, y),
                elevation = wayPoint.elevation.get().toDouble()
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

    return Conversion(
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
                        coordinate.elevation)
                },
                totalTime = it.last().time - it.first().time
            )
        },
        width = (right - left) * scale,
        height = (bottom - top) * scale,

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
class Conversion(val routes: List<Route>, val width: Double, val height: Double)

private fun readWayPoints(directory: String): List<List<WayPoint>> {

    val dir = Files.walk(File(directory).toPath())
    val files = dir
        .filter(Files::isRegularFile)
        .map { it.toFile().absolutePath }.toList()

    val wayPoints: MutableList<List<WayPoint>> = mutableListOf()
    for (file in files) {
        try {
            val read = GPX.read(file)
            wayPoints.add(
                read.tracks()
                    .flatMap { it.segments() }
                    .flatMap { it.points() }
                    .collect(Collectors.toList()).toList())
        } catch (e: Exception) {
            println("Error reading file: $file, ${e.message}")
            continue
        }

    }
    return wayPoints.toList()
}

