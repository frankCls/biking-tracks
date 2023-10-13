import gpx.convertToCoordinates
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.color.hsl
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.loadFont
import org.openrndr.draw.renderTarget
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.math.Vector2
import org.openrndr.math.mod
import java.io.File
import java.time.Duration

const val SPEED_UP_FACTOR = 20

data class TourCoordinate(
    val position: Vector2 = Vector2.ZERO,
    val time: Long = 0L,
    val realCoordinates: Vector2 = Vector2.ZERO,
    val elevation: Double = 0.0,
    val distance: Double = 0.0
)

data class Tour(
    val coordinates: List<TourCoordinate>,
    val totalTime: Long = 0L,
    val totalDistance: Double = 0.0,
    val maxElevation: Double = 0.0,
    val minElevation: Double = 0.0,
    val averageSpeed: Double = 0.0,
    val distance: Double = 0.0
)

data class Elevation(val position: Vector2, val elevation: Double)

fun main() {

    application {
        configure {
            width = 1400
            height = 900
        }

        oliveProgram {
            var counter = seconds.toInt()

            val font = loadFont("data/fonts/default.otf", 24.0)
            val file = File("data/gpx")
            val conversion = convertToCoordinates(file.absolutePath, 0.015, width, height)

            val allTours = conversion.routes
//                .shuffled()
                .map { tour ->
                    val vectors = tour.coordinates.map { Vector2(it.x, it.y) }
                    val coordinates = tour.coordinates.mapIndexed { index, coordinate ->
                        val lengths = vectors
                            .mapIndexed { idx, coord ->
                                if (idx == 0) {
                                    0.0
                                } else {
                                    coord.distanceTo(vectors[idx - 1])
                                }
                            }
                        TourCoordinate(
                            position = Vector2(coordinate.x, coordinate.y),
                            time = coordinate.time,
                            realCoordinates = Vector2(
                                coordinate.realCoordinates.first,
                                coordinate.realCoordinates.second
                            ),
                            elevation = coordinate.elevation,
                            distance = lengths.sum()
                        )
                    }
                    val distances = coordinates
                        .mapIndexed { index, coordinate ->
                            if (index == 0) {
                                0.0
                            } else {
                                coordinate.realCoordinates.distanceTo(coordinates[index - 1].realCoordinates)
//                                coordinate.realCoordinates.length + coordinates[index - 1].realCoordinates.length
                            }
                        }
                    val totalDistance = distances.fold(0.0) { acc, d -> acc + d }
                    Tour(
                        coordinates = coordinates,
                        totalTime = tour.totalTime,
                        totalDistance = totalDistance,
                        maxElevation = coordinates.maxOf { it.elevation },
                        minElevation = coordinates.minOf { it.elevation },
                        averageSpeed = totalDistance / (tour.totalTime / 3.6)
                    )
                }

            var runningTour: Int = 0
            var tour: Tour

            val routesRenderTarget = renderTarget(width, height) {
                colorBuffer()
            }
            val liveRoutesRenderTarget = renderTarget(width, height) {
                colorBuffer()
            }
            val elevationRenderTarget = renderTarget(width, height) {
                colorBuffer()
            }
            val statisticsRenderTarget = renderTarget(width, height) {
                colorBuffer()
            }

            drawer.isolatedWithTarget(routesRenderTarget) {
                drawer.translate(Vector2((width - conversion.width) / 2, (-height + conversion.height) / 2))
                drawer.clear(ColorRGBa.TRANSPARENT)
                allTours.forEachIndexed { i, it ->
                    drawer.stroke = hsl(i * 40.0 / allTours.size, 0.5, 0.3, 0.3).toRGBa().opacify(0.7)
                    drawer.strokeWeight = 0.7
                    drawer.lineSegments(it.coordinates.map { coord -> coord.position })
                }
            }


            var elapsedTime = 0L

            extend {

                drawer.clear(ColorRGBa.BLACK)
                runningTour = mod(counter, allTours.size)
                tour = allTours[runningTour]

                val coordinates = tour.coordinates
                val done =
                    coordinates.filter { point -> point.time < (seconds.toLong() - elapsedTime) * SPEED_UP_FACTOR }
                if (done.size == coordinates.size) {
                    counter++
                    elapsedTime += tour.totalTime.toInt() / SPEED_UP_FACTOR
                }


                val d = width / tour.totalDistance
//                val d = tour.totalDistance / width
                val elevations = List(coordinates.size) {
                    Elevation(
//                        Vector2(coordinates[it].realCoordinates.x * d, coordinates[it].realCoordinates.y * d),
                        coordinates[it].position,
                        coordinates[it].elevation * 2
                    )
                }

                drawer.isolatedWithTarget(liveRoutesRenderTarget) {
                    drawer.translate(Vector2((width - conversion.width) / 2, (-height + conversion.height) / 2))
                    drawer.clear(ColorRGBa.TRANSPARENT)
                    drawer.stroke = hsl(counter * 90.0 / allTours.size, 0.5, 0.3, 0.3).toRGBa().opacify(1.0)
                    drawer.strokeWeight = 4.0
                    drawer.lineSegments(done.map { coord -> coord.position })
                }

                drawer.isolatedWithTarget(elevationRenderTarget) {
                    drawer.clear(ColorRGBa.TRANSPARENT)
                    drawer.stroke = ColorRGBa.WHITE.opacify(0.2)
                    drawer.strokeWeight = 0.5

                    if (done.isNotEmpty()) {
                        val vectors = elevations.mapIndexed { index, it ->
                            if (index == 0) {
                                Vector2(0.0, height - it.elevation)
                            } else {
                                Vector2(
                                    elevations[0].position.distanceTo(elevations[index - 1].position) * d,
                                    height - it.elevation
                                )
                            }
                        }
                        drawer.lineSegments(vectors)

                        val elevationsDone = elevations.take(if (done.isEmpty()) 0 else done.size - 1)
                        val elevationProgress = elevationsDone.sumOf { it.position.length }

                        drawer.circle(
                            Vector2(elevationProgress, height - elevationsDone.last().elevation),
                            5.0
                        )
                    }
                }

                drawer.isolatedWithTarget(statisticsRenderTarget) {
                    // calculate average speed between last x points
                    var speed: Double
                    if (done.isNotEmpty()) {
                        val takeLast = done.takeLast(10)
//                        println(takeLast)
                        takeLast
                            .mapIndexed { index, tourCoordinate -> // calculate distance between points
                                if (index == 0) {
                                    0.0
                                } else {
                                    tourCoordinate.realCoordinates.distanceTo(takeLast[index - 1].realCoordinates)
                                }
                            }
                            .fold(0.0) { acc, d -> acc + d }
                            .let {
                                speed = (it / (takeLast.last().time - takeLast.first().time)) * 3.6
                            }
                        // display speed
                        drawer.clear(ColorRGBa.TRANSPARENT)
                        drawer.fill = ColorRGBa.WHITE.opacify(0.5)
                        drawer.fontMap = font

                        drawer.text("${textPadded("speed:")}${String.format("%.2f", speed)} km/h", 30.0, 30.0)
                        drawer.text(
                            "${textPadded("total distance:")}${
                                String.format(
                                    "%.2f",
                                    tour.totalDistance / 1000
                                )
                            } ",
                            30.0,
                            45.0
                        )
                        drawer.text(
                            "${textPadded("average speed:")}${String.format("%.2f", tour.averageSpeed)} km/h",
                            30.0,
                            60.0
                        )
                        val duration = Duration.ofSeconds(tour.totalTime)
                        val formattedDuration = String.format(
                            "%02d:%02d:%02d",
                            duration.seconds / 3600,
                            (duration.seconds % 3600) / 60,
                            duration.seconds % 60
                        )
                        drawer.text(
                            "${textPadded("total time:")}$formattedDuration", 30.0, 75.0
                        )
                    }
                }

                drawer.image(liveRoutesRenderTarget.colorBuffer(0))
                drawer.image(routesRenderTarget.colorBuffer(0))
                drawer.image(elevationRenderTarget.colorBuffer(0))
                drawer.image(statisticsRenderTarget.colorBuffer(0))


            }
        }
    }
}

fun textPadded(text: String): String {
    return text.padEnd(20)
}