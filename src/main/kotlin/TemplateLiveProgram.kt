import gpx.*
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.noise.Random
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.math.Vector2
import org.openrndr.math.mod
import geotools.communes
import org.openrndr.draw.*
import org.openrndr.math.map
import org.openrndr.shape.Segment
import org.openrndr.shape.ShapeContour
import pixels.*
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
    val distance: Double = 0.0,
    val color: ColorRGBa = ColorRGBa.WHITE
)

data class Elevation(val distance: Double, val height: Double)

fun main() {

    application {
        configure {
            width = 1400
            height = 900
        }

        oliveProgram {
            fun translation(conversion: PixelsTransformation): Vector2 {
                return if (width / height > conversion.aspectRatio) {
                    Vector2(
                        (width - conversion.width) / 2,
                        0.0
                    )
                } else {
                    Vector2(
                        0.0,
                        (height - (height - conversion.height)) / 2
                    )
                }
            }

            fun translate(conversion: PixelsTransformation) {
                val (first, second) = translation(conversion)
                drawer.translate(first, second)
            }

            val image = loadImage("data/images/test.png")
            //define colors
            val colors = listOf(
                //        ColorRGBa.fromHex(0x001219),
                ColorRGBa.fromHex(0x005f73),
                ColorRGBa.fromHex(0x0a9396),
                ColorRGBa.fromHex(0x94d2bd),
                ColorRGBa.fromHex(0xe9d8a6),
                ColorRGBa.fromHex(0xee9b00),
                ColorRGBa.fromHex(0xca6702),
                ColorRGBa.fromHex(0xbb3e03),
                ColorRGBa.fromHex(0xae2012),
                ColorRGBa.fromHex(0x9b2226)
            )

            // define counter to help keeping track of which tour is running
            var counter = seconds.toInt()

            val font = loadFont("data/fonts/default.otf", 24.0)

            // load gpx file and convert to coordinates
            val gpxFile = File("data/gpx")
//            val scale = 0.015
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

            val conversion = transformToPixelCoordinates(gpsPoints, width, height, border = 20.0)

            // calculate min and max latitudes and longitudes
            val minLat = conversion.segments.minOf { it.points.minOf { point -> point.realCoordinates.x } }
            val maxLat = conversion.segments.maxOf { it.points.maxOf { point -> point.realCoordinates.x } }
            val minLong = conversion.segments.minOf { it.points.minOf { point -> point.realCoordinates.y } }
            val maxLong = conversion.segments.maxOf { it.points.maxOf { point -> point.realCoordinates.y } }


            val x1 = (width - conversion.width) / 2
            val y1 = (height - conversion.height) / 2
            val minX = conversion.segments.minOf { it.points.minOf { point -> point.position.x } } + x1
            val maxX = conversion.segments.maxOf { it.points.maxOf { point -> point.position.x } } + x1
            val minY = conversion.segments.minOf { it.points.minOf { point -> point.position.y } } + y1
            val maxY = conversion.segments.maxOf { it.points.maxOf { point -> point.position.y } } + y1

//            downloadAerialView(minLat, minLong, maxLat, maxLong, conversion.width.toInt() * 2, conversion.height.toInt() * 2)

            // load shapefile and convert to coordinates
            val shapefile = File("data/shapefile/belgium-communes/communes_L08.shp")
            val communes = communes(shapefile, left = minLat, top = minLong, right = maxLat, bottom = maxLong)
            val communesGpsPoints = communes.associate { commune ->
                commune.name to
                        commune.geometry.map { coordinate -> GpsPoint(coordinate.x, coordinate.y, 0, 0.0) }
            }
            val communesCoordinates = transformToPixelCoordinates(communesGpsPoints, width, height).segments

            val allTours = conversion.segments
                .shuffled()
                .map { tour ->
                    val totalDistance = tour.points.sumOf { it.length }
                    Tour(
                        coordinates = tour.points,
                        totalTime = tour.totalTime,
                        totalDistance = totalDistance,
                        maxElevation = tour.points.maxOf { it.elevation },
                        minElevation = tour.points.minOf { it.elevation },
                        averageSpeed = totalDistance / (tour.totalTime / 3.6),
                        color = Random.pick(colors)
                    )
                }

            val totalDistanceOverTours = allTours.sumOf { it.totalDistance }
            println(totalDistanceOverTours / 1000)
            var runningTour: Int = 0
            var tour: Tour

            val backgroundAerialView = renderTarget(width, height) {
                colorBuffer()
            }

            val communesRenderTarget = renderTarget(width, height) {
                colorBuffer()
                depthBuffer()
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
                drawer.clear(ColorRGBa.TRANSPARENT)
                translate(conversion)
                drawer.stroke = ColorRGBa.WHITE.opacify(0.3)
                drawer.fill = null
                drawer.strokeWeight = 0.5
                val contours = communesCoordinates.map {
                    ShapeContour(
                        it.points.zipWithNext { a, b ->
                            Segment(
                                Vector2(
                                    a.position.x,
                                    a.position.y
                                ),
                                Vector2(
                                    b.position.x,
                                    b.position.y
                                )
                            )
                        }, false
                    ).shape
                }
                drawer.shapes(contours)
            }

            drawer.isolatedWithTarget(routesRenderTarget) {
                translate(conversion)
                drawer.clear(ColorRGBa.TRANSPARENT)
                allTours.forEachIndexed { i, it ->
                    drawer.stroke = it.color.opacify(0.6)
                    drawer.strokeWeight = 0.5
                    drawer.lineSegments(it.coordinates.map { coord -> coord.position })
                }
            }

            var elapsedTime = 0L

            extend {

                drawer.clear(ColorRGBa.BLACK)

                // calculate which tour is running
                runningTour = mod(counter, allTours.size)
                tour = allTours[runningTour]

                val coordinates = tour.coordinates

                // calculate which points are done
                val done =
                    coordinates.filter { point -> point.time < (seconds.toLong() - elapsedTime) * SPEED_UP_FACTOR }

                // if all points are done, increase counter and add time to elapsed time
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

                drawer.isolatedWithTarget(backgroundAerialView) {
                    drawer.clear(ColorRGBa.TRANSPARENT)
                    drawer.drawStyle.colorMatrix =
                        tint(
                            ColorRGBa.WHITE.opacify(
                                map(height.toDouble(), 0.0, 0.0, 1.0, mouse.position.y)
                            )
                        )
                    val (first, second) = translation(conversion)
                    drawer.image(
                        image,
                        first,
                        second,
                        conversion.width,
                        conversion.height
                    )
                }

                drawer.isolatedWithTarget(liveRoutesRenderTarget) {
                    translate(conversion)
                    drawer.clear(ColorRGBa.TRANSPARENT)
                    drawer.stroke = allTours[runningTour].color.opacify(1.0)
                    drawer.strokeWeight = 3.0
                    drawer.lineSegments(done.map { coord -> coord.position })
                }

                drawer.isolatedWithTarget(elevationRenderTarget) {
                    drawer.clear(ColorRGBa.TRANSPARENT)
                    if (done.isNotEmpty()) {
                        val elevationProfile = elevations.mapIndexed { index, it ->
                            Vector2(
                                x = if (index == 0) 0.0 else it.distance * d,
                                y = height - it.height
                            )
                        }

                        // draw elevation height lines on profile
                        for (i in 0..tour.maxElevation.toInt() step 10) {
                            drawer.stroke = ColorRGBa.WHITE.opacify(0.3)
                            drawer.strokeWeight = 0.1
                            drawer.lineSegment(
                                Vector2(0.0, height - i * ELEVATION_SMOOTHING),
                                Vector2(width.toDouble(), height - i * ELEVATION_SMOOTHING)
                            )
                        }

                        // draw elevation profile of points done
                        drawer.stroke = allTours[runningTour].color
                        drawer.strokeWeight = 3.0
                        drawer.lineSegments(elevationProfile.take(if (done.isEmpty()) 0 else done.size - 1))

                        // draw elevation profile of remaining points
                        drawer.stroke = ColorRGBa.WHITE.opacify(0.2)
                        drawer.strokeWeight = 0.5
                        drawer.lineSegments(elevationProfile.takeLast(coordinates.size - done.size))
                        val elevationsDone = elevations.take(if (done.isEmpty()) 0 else done.size - 1)

                        // draw a dot on the actual point
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


//                drawer.image(backgroundAerialView.colorBuffer(0))
//                drawer.image(communesRenderTarget.colorBuffer(0))
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

