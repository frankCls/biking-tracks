package gpx

import io.jenetics.jpx.GPX
import io.jenetics.jpx.WayPoint
import java.io.File
import java.nio.file.Files
import java.util.stream.Collectors
import kotlin.math.cos


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
    val coordinates = points.mapIndexed { index, it ->
        val startTime = it.first().time.get().toEpochSecond()
        it.map { wayPoint ->
            val x = r * wayPoint.longitude.toRadians() * aspectRatio
            val y = r * wayPoint.latitude.toRadians()
            Coordinate(x, y, wayPoint.time.get().toEpochSecond() - startTime)
        }
    }

    val flattenedCoordinates = coordinates.flatten()
    val xCoordinates = flattenedCoordinates.map { it.x }
    val left = xCoordinates.minOrNull()
    val right = xCoordinates.maxOrNull()
    val xOffset = (width - (right!! * scale)) / 2

    val yCoordinates = flattenedCoordinates.map { it.y }
    val top = yCoordinates.minOrNull()
    val bottom = yCoordinates.maxOrNull()
    val yOffset = (height - ((top!! - (top)) * scale)) / 2

    println("width: $width, height: $height")
    println("left: 0, right: ${(right - left!!) * scale}, top: 0, bottom: ${(bottom!! - top) * scale}")
    println("xOffset: $xOffset, yOffset: $yOffset")

    return Conversion(
        coordinates.map {
            it.map { coordinate ->
                val x = (coordinate.x - left/* + xOffset*/) * scale
                val y = height - (coordinate.y - top /*+ yOffset*/) * scale
                Coordinate(x, y, coordinate.time)
            }
        },
        (right - left) * scale,
        (bottom - top) * scale
    )
}

class Coordinate(val x: Double, val y: Double, val time: Long = 0L)

class Conversion(val routes: List<List<Coordinate>>, val width: Double, val height: Double)

private fun readWayPoints(directory: String): List<List<WayPoint>> {

    val dir = Files.walk(File(directory).toPath())
    val files = dir
        .filter(Files::isRegularFile)
        .map { it.toFile().absolutePath }.toList()

    val points: MutableList<List<WayPoint>> = mutableListOf()
    for (file in files) {
        try {
            val read = GPX.read(file)
            points.add(
                read.tracks()
                    .flatMap { it.segments() }
                    .flatMap { it.points() }
                    .collect(Collectors.toList()).toList())
        } catch (e: Exception) {
            println("Error reading file: $file, ${e.message}")
            continue
        }

    }
    return points.toList()
}

