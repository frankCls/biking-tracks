import gpx.Point
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
const val ELEVATION_SMOOTHING = 0.5

data class Tour(
    val coordinates: List<Point>,
    val totalTime: Long = 0L,
    val totalDistance: Double = 0.0,
    val maxElevation: Double = 0.0,
    val minElevation: Double = 0.0,
    val averageSpeed: Double = 0.0,
    val distance: Double = 0.0
)

data class Elevation(val distance: Double, val height: Double)

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
                .shuffled()
                .map { tour ->
                    val totalDistance = tour.points.sumOf { it.length }
                    Tour(
                        coordinates = tour.points,
                        totalTime = tour.totalTime,
                        totalDistance = totalDistance,
                        maxElevation = tour.points.maxOf { it.elevation },
                        minElevation = tour.points.minOf { it.elevation },
                        averageSpeed = totalDistance / (tour.totalTime / 3.6)
                    )
                }

            val totalDistanceOverTours = allTours.sumOf { it.totalDistance }
            println(totalDistanceOverTours / 1000)
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

            val colors = List(allTours.size) { index ->
                hsl(index * 40.0 / allTours.size, 0.5, 0.3, 0.3).toRGBa().opacify(0.7)
            }


            drawer.isolatedWithTarget(routesRenderTarget) {
                drawer.translate(Vector2((width - conversion.width) / 2, (-height + conversion.height) / 2))
                drawer.clear(ColorRGBa.TRANSPARENT)
                allTours.forEachIndexed { i, it ->
                    drawer.stroke = colors[i]
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
                val elevations = List(coordinates.size) {
                    Elevation(
                        coordinates[it].distance,
                        coordinates[it].elevation * ELEVATION_SMOOTHING
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


                    if (done.isNotEmpty()) {
                        val vectors = elevations.mapIndexed { index, it ->
                            if (index == 0) {
                                Vector2(0.0, height - it.height)
                            } else {
                                Vector2(
                                    it.distance * d,
                                    height - it.height
                                )
                            }
                        }

                        for(i in 0..tour.maxElevation.toInt() step 10) {
                            drawer.stroke = ColorRGBa.WHITE.opacify(0.3)
                            drawer.strokeWeight = 0.2
                            drawer.lineSegment(
                                Vector2(0.0, height - i * ELEVATION_SMOOTHING),
                                Vector2(width.toDouble(), height - i * ELEVATION_SMOOTHING)
                            )
                        }

                        drawer.stroke = colors[runningTour]
                        drawer.strokeWeight = 3.0
                        drawer.lineSegments(vectors.take(if (done.isEmpty()) 0 else done.size - 1))

                        drawer.stroke = ColorRGBa.WHITE.opacify(0.2)
                        drawer.strokeWeight = 0.5
                        drawer.lineSegments(vectors.takeLast(coordinates.size - done.size))
                        val elevationsDone = elevations.take(if (done.isEmpty()) 0 else done.size - 1)

                        if (elevationsDone.isNotEmpty()) {
                            drawer.circle(
                                Vector2(elevationsDone.last().distance * d, height - elevationsDone.last().height),
                                4.0
                            )
                        }


                    }
                }

                drawer.isolatedWithTarget(statisticsRenderTarget) {
                    // calculate average speed between last x points
                    var speed: Double
                    if (done.isNotEmpty()) {
                        val takeLast = done.takeLast(10)
                        takeLast
                            .map { it.length * d }
                            .fold(0.0) { acc, d -> acc + d }
                            .let {
                                speed =
                                    (takeLast.sumOf { it.length } / (takeLast.last().time - takeLast.first().time)) * 3.6
                            }

                        // display speed
                        drawer.clear(ColorRGBa.TRANSPARENT)
                        drawer.fill = ColorRGBa.WHITE.opacify(0.5)
                        drawer.fontMap = font

                        listOf(
                            "${textPadded("total distance:")}${String.format("%.2f", tour.totalDistance / 1000)} km",
                            "${textPadded("average speed:")}${String.format("%.2f", tour.averageSpeed)} km/h",
                            "${textPadded("total time:")}${formatDuration(tour.totalTime)}",
                            "${textPadded("distance:")}${String.format("%.2f", done.last().distance / 1000)} km",
                            "${textPadded("time:")}${formatDuration(done.last().time)}",
                            "${textPadded("speed:")}${String.format("%.2f", speed)} km/h",
                            "${textPadded("height:")}${
                                String.format(
                                    "%.2f",
                                    done.last().elevation / ELEVATION_SMOOTHING
                                )
                            } ",
                        ).forEachIndexed { index, text ->
                            drawer.text(text, 30.0, 30.0 + index * 20)
                        }
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

fun formatDuration(time: Long): String {
    val duration = Duration.ofSeconds(time)
    return String.format(
        "%02d:%02d:%02d",
        duration.seconds / 3600,
        (duration.seconds % 3600) / 60,
        duration.seconds % 60
    )
}

fun textPadded(text: String): String {
    return text.padEnd(20)
}