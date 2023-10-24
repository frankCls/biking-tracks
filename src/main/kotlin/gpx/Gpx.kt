package gpx

import io.jenetics.jpx.GPX
import io.jenetics.jpx.WayPoint
import org.openrndr.math.Vector2
import java.io.File
import java.nio.file.Files
import java.util.stream.Collectors
import kotlin.math.cos
import kotlin.math.sqrt




fun readWayPoints(directory: String): List<List<WayPoint>> {

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

