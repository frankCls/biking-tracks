import gpx.*
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.noise.Random
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.math.Vector2
import org.openrndr.math.mod
import geotools.communes
import geotools.downloadAerialView
import org.openrndr.draw.*
import org.openrndr.math.map
import pixels.*
import config.AppConstants
import config.AppConstants.Colors
import java.io.File
import java.time.Duration


data class Tour(
    val coordinates: List<Point>,
    val totalTime: Long = 0L,
    val totalDistance: Double = 0.0,
    val maxElevation: Double = 0.0,
    val minElevation: Double = 0.0,
    val averageSpeed: Double = 0.0,
    val distance: Double = 0.0,
    val color: ColorRGBa = ColorRGBa.WHITE
)

data class Elevation(val distance: Double, val height: Double)

fun main() {

    application {
        configure {
            width = AppConstants.DEFAULT_WIDTH
            height = AppConstants.DEFAULT_HEIGHT
        }

        oliveProgram {
            val image = loadImage(AppConstants.TEST_IMAGE_PATH)
            //define colors
            val colors = Colors.ROUTE_PALETTE

            // define counter to help keeping track of which tour is running
            var counter = seconds.toInt()

            val font = loadFont(AppConstants.DEFAULT_FONT_PATH, AppConstants.STATISTICS_FONT_SIZE)

            // load gpx file and convert to coordinates
            val gpxFile = File(AppConstants.GPX_DIRECTORY)
            val scale = AppConstants.SCALE_FACTOR
            val points = readWayPoints(gpxFile.absolutePath)
            val gpsPoints =
                points
                    .mapIndexed { i, it ->
                        "$i" to it.map { wayPoint ->
                            GpsPoint(
                                latitude = wayPoint.latitude.toDouble(),
                                longitude = wayPoint.longitude.toDouble(),
                                time = wayPoint.time.get().toEpochSecond(),
                                elevation = wayPoint.elevation.get().toDouble()
                            )
                        }
                    }.toMap()

            val conversion = transformToPixelCoordinates(gpsPoints, scale, width, height)

            // calculate min and max latitudes and longitudes
            val minX = conversion.routes.minOf { it.points.minOf { point -> point.realCoordinates.x } }
            val maxX = conversion.routes.maxOf { it.points.maxOf { point -> point.realCoordinates.x } }
            val minY = conversion.routes.minOf { it.points.minOf { point -> point.realCoordinates.y } }
            val maxY = conversion.routes.maxOf { it.points.maxOf { point -> point.realCoordinates.y } }

            // get map


            downloadAerialView(minX, minY, maxX, maxY, conversion.width.toInt() * AppConstants.MAP_SCALE_MULTIPLIER, conversion.height.toInt() * AppConstants.MAP_SCALE_MULTIPLIER)
            // load shapefile and convert to coordinates
            val shapefile = File(AppConstants.SHAPEFILE_PATH)
            val communes = communes(shapefile, left = minX, top = minY, right = maxX, bottom = maxY)
            val communesGpsPoints = communes.associate { commune ->
                commune.name to
                        commune.geometry.map { coordinate -> GpsPoint(coordinate.x, coordinate.y, 0, 0.0) }
            }
            val communesCoordinates = transformToPixelCoordinates(communesGpsPoints, scale, width, height).routes

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
                        averageSpeed = totalDistance / (tour.totalTime / AppConstants.SPEED_CONVERSION_FACTOR),
                        color = Random.pick(colors)
                    )
                }

            val totalDistanceOverTours = allTours.sumOf { it.totalDistance }
            println(totalDistanceOverTours / AppConstants.DISTANCE_CONVERSION_FACTOR)
            var runningTour: Int = 0
            var tour: Tour

            val backgroundAerialView = renderTarget(width, height) {
                colorBuffer()
            }

            val communesRenderTarget = renderTarget(width, height) {
                colorBuffer()
            }

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



            drawer.isolatedWithTarget(communesRenderTarget) {
                drawer.translate(Vector2((width - conversion.width) / 2, (conversion.height - height) / 2))
                drawer.clear(Colors.TRANSPARENT)
                drawer.stroke = Colors.WHITE.opacify(AppConstants.COMMUNE_OPACITY)
                drawer.strokeWeight = AppConstants.THIN_STROKE_WEIGHT
                communesCoordinates.forEach {
                    drawer.lineSegments(
                        it.points.map { point ->
                            point.position
                        })
                }
            }


            drawer.isolatedWithTarget(routesRenderTarget) {
                drawer.translate(Vector2((width - conversion.width) / 2, (conversion.height - height) / 2))
                drawer.clear(Colors.TRANSPARENT)
                allTours.forEachIndexed { i, it ->
                    drawer.stroke = it.color.opacify(AppConstants.ROUTE_OPACITY)
                    drawer.strokeWeight = AppConstants.DEFAULT_STROKE_WEIGHT
                    drawer.lineSegments(it.coordinates.map { coord -> coord.position })
                }
            }

            var elapsedTime = 0L

            extend {

                drawer.clear(Colors.BLACK)

                // calculate which tour is running
                runningTour = mod(counter, allTours.size)
                tour = allTours[runningTour]

                val coordinates = tour.coordinates

                // calculate which points are done
                val done =
                    coordinates.filter { point -> point.time < (seconds.toLong() - elapsedTime) * AppConstants.SPEED_UP_FACTOR }

                // if all points are done, increase counter and add time to elapsed time
                if (done.size == coordinates.size) {
                    counter++
                    elapsedTime += tour.totalTime.toInt() / AppConstants.SPEED_UP_FACTOR
                }

                val d = width / tour.totalDistance
                val elevations = List(coordinates.size) {
                    Elevation(
                        coordinates[it].distance,
                        coordinates[it].elevation * AppConstants.ELEVATION_SMOOTHING
                    )
                }

                drawer.isolatedWithTarget(backgroundAerialView) {
                    drawer.clear(Colors.TRANSPARENT)
//                drawer.drawStyle.colorMatrix = grayscale(1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0)
                    drawer.drawStyle.colorMatrix =
                        tint(
                            Colors.WHITE.opacify(
                                map(height.toDouble(), 0.0, 0.0, 1.0, mouse.position.y)
                            )
                        )
                    drawer.image(
                        image,
                        (width - conversion.width) / 2,
                        (height - conversion.height) / 2,
                        conversion.width,
                        conversion.height
                    )
                }

                drawer.isolatedWithTarget(liveRoutesRenderTarget) {
                    drawer.translate(Vector2((width - conversion.width) / 2, (conversion.height - height) / 2))
                    drawer.clear(Colors.TRANSPARENT)
//                    drawer.stroke = hsl(counter * 90.0 / allTours.size, 0.5, 0.3, 0.3).toRGBa().opacify(1.0)
                    drawer.stroke = allTours[runningTour].color.opacify(1.0)
                    drawer.strokeWeight = AppConstants.THICK_STROKE_WEIGHT
                    drawer.lineSegments(done.map { coord -> coord.position })
                }

                drawer.isolatedWithTarget(elevationRenderTarget) {
                    drawer.clear(Colors.TRANSPARENT)
                    if (done.isNotEmpty()) {
                        val elevationProfile = elevations.mapIndexed { index, it ->
                            Vector2(
                                x = if (index == 0) 0.0 else it.distance * d,
                                y = height - it.height
                            )
                        }

                        // draw elevation height lines on profile
                        for (i in 0..tour.maxElevation.toInt() step AppConstants.ELEVATION_GRID_STEP) {
                            drawer.stroke = Colors.WHITE.opacify(AppConstants.COMMUNE_OPACITY)
                            drawer.strokeWeight = AppConstants.THIN_STROKE_WEIGHT
                            drawer.lineSegment(
                                Vector2(0.0, height - i * AppConstants.ELEVATION_SMOOTHING),
                                Vector2(width.toDouble(), height - i * AppConstants.ELEVATION_SMOOTHING)
                            )
                        }

                        // draw elevation profile of points done
                        drawer.stroke = allTours[runningTour].color
                        drawer.strokeWeight = AppConstants.THICK_STROKE_WEIGHT
                        drawer.lineSegments(elevationProfile.take(if (done.isEmpty()) 0 else done.size - 1))

                        // draw elevation profile of remaining points
                        drawer.stroke = Colors.WHITE.opacify(AppConstants.REMAINING_ROUTE_OPACITY)
                        drawer.strokeWeight = AppConstants.DEFAULT_STROKE_WEIGHT
                        drawer.lineSegments(elevationProfile.takeLast(coordinates.size - done.size))
                        val elevationsDone = elevations.take(if (done.isEmpty()) 0 else done.size - 1)

                        // draw a dot on the actual point
                        if (elevationsDone.isNotEmpty()) {
                            drawer.circle(
                                Vector2(elevationsDone.last().distance * d, height - elevationsDone.last().height),
                                AppConstants.CIRCLE_RADIUS
                            )
                        }
                    }
                }

                drawer.isolatedWithTarget(statisticsRenderTarget) {
                    // calculate average speed between last x points
                    var speed: Double
                    if (done.isNotEmpty()) {
                        val takeLast = done.takeLast(AppConstants.SPEED_CALCULATION_SAMPLE_SIZE)
                        takeLast
                            .map { it.length * d }
                            .fold(0.0) { acc, d -> acc + d }
                            .let {
                                speed =
                                    (takeLast.sumOf { it.length } / (takeLast.last().time - takeLast.first().time)) * AppConstants.SPEED_CONVERSION_FACTOR
                            }

                        // display speed
                        drawer.clear(Colors.TRANSPARENT)
                        drawer.fill = Colors.WHITE.opacify(AppConstants.STATISTICS_OPACITY)
                        drawer.fontMap = font

                        listOf(
                            "${textPadded("total distance:")}${String.format("%.2f", tour.totalDistance / AppConstants.DISTANCE_CONVERSION_FACTOR)} km",
                            "${textPadded("average speed:")}${String.format("%.2f", tour.averageSpeed)} km/h",
                            "${textPadded("total time:")}${formatDuration(tour.totalTime)}",
                            "${textPadded("distance:")}${String.format("%.2f", done.last().distance / AppConstants.DISTANCE_CONVERSION_FACTOR)} km",
                            "${textPadded("time:")}${formatDuration(done.last().time)}",
                            "${textPadded("speed:")}${String.format("%.2f", speed)} km/h",
                            "${textPadded("height:")}${
                                String.format(
                                    "%.2f",
                                    done.last().elevation / AppConstants.ELEVATION_SMOOTHING
                                )
                            } ",
                        ).forEachIndexed { index, text ->
                            drawer.text(text, AppConstants.TEXT_X_POSITION, AppConstants.TEXT_Y_POSITION + index * AppConstants.LINE_HEIGHT)
                        }
                    }
                }

                drawer.image(backgroundAerialView.colorBuffer(0))
                drawer.image(communesRenderTarget.colorBuffer(0))
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
        duration.seconds / AppConstants.SECONDS_PER_HOUR,
        (duration.seconds % AppConstants.SECONDS_PER_HOUR) / AppConstants.SECONDS_PER_MINUTE,
        duration.seconds % AppConstants.SECONDS_PER_MINUTE
    )
}

fun textPadded(text: String): String {
    return text.padEnd(AppConstants.TEXT_PADDING_LENGTH)
}