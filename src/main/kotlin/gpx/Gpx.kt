package gpx

import io.jenetics.jpx.GPX
import io.jenetics.jpx.WayPoint
import validation.CoordinateValidator
import java.io.File
import java.nio.file.Files
import java.util.stream.Collectors

fun readWayPoints(directory: String): List<List<WayPoint>> {

    val dir = Files.walk(File(directory).toPath())
    val files = dir
        .filter(Files::isRegularFile)
        .map { it.toFile().absolutePath }.toList()

    val wayPoints: MutableList<List<WayPoint>> = mutableListOf()
    var successfulFiles = 0
    var failedFiles = 0
    
    for (file in files) {
        try {
            val read = GPX.read(file)
            val trackPoints = read.tracks()
                .flatMap { it.segments() }
                .flatMap { it.points() }
                .collect(Collectors.toList()).toList()
            
            // Filter out waypoints with invalid coordinates
            val validPoints = trackPoints.filter { wayPoint ->
                try {
                    val lat = wayPoint.latitude.toDouble()
                    val lng = wayPoint.longitude.toDouble()
                    CoordinateValidator.validateLatitude(lat).isValid && 
                    CoordinateValidator.validateLongitude(lng).isValid
                } catch (e: Exception) {
                    false // Skip waypoints with conversion errors
                }
            }
            
            if (validPoints.isNotEmpty()) {
                wayPoints.add(validPoints)
                successfulFiles++
                
                if (validPoints.size < trackPoints.size) {
                    println("Warning: File $file had ${trackPoints.size - validPoints.size} invalid waypoints out of ${trackPoints.size}")
                }
            } else {
                println("Warning: No valid waypoints found in file: $file")
                failedFiles++
            }
        } catch (e: Exception) {
            println("Error reading file: $file, ${e.message}")
            failedFiles++
            continue
        }
    }
    
    println("GPX processing complete: $successfulFiles successful files, $failedFiles failed files")
    return wayPoints.toList()
}
