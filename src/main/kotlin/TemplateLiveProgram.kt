import gpx.Coordinate
import gpx.convertToCoordinates
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.color.hsl
import org.openrndr.color.rgb
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.math.Vector2
import java.io.File

const val SPEED_UP_FACTOR = 20

class CoordinateWithTime(val position: Vector2, val time: Long)

fun main() {

    application {
        configure {
            width = 1400
            height = 900
        }

        oliveProgram {

            val file = File("data/gpx")
            val conversion = convertToCoordinates(file.absolutePath, 0.015, width, height)

            val coordinates = conversion.routes
                .map {
                    it.map { coordinate ->
                        CoordinateWithTime(
                            Vector2(coordinate.x, coordinate.y),
                            coordinate.time
                        )
                    }
                }

            val rt0 = renderTarget(width, height) {
                colorBuffer()
            }
            val rt1 = renderTarget(width, height) {
                colorBuffer()
            }

//            val coordinates =
//                conversion.routes.map { it.map { coordinate: Coordinate -> Vector2(coordinate.x, coordinate.y) } }

            drawer.isolatedWithTarget(rt0) {
                drawer.translate(Vector2((width - conversion.width) / 2, (-height + conversion.height) / 2))
                drawer.clear(ColorRGBa.TRANSPARENT)
                coordinates.forEachIndexed { i, it ->
                    drawer.stroke = hsl(i * 60.0 / coordinates.size, 0.5, 0.3, 0.3).toRGBa().opacify(0.1)
                    drawer.strokeWeight = 1.0
                    drawer.lineSegments(it.map { coord ->  coord.position})
                }
            }

            extend {
                drawer.clear(ColorRGBa.BLACK)



                drawer.isolatedWithTarget(rt1) {
                    drawer.translate(Vector2((width - conversion.width) / 2, (-height + conversion.height) / 2))
                    drawer.clear(ColorRGBa.TRANSPARENT)
                    coordinates.forEachIndexed { i, it ->
                        val done = it.filter { point -> point.time < seconds.toLong() * SPEED_UP_FACTOR }
                        drawer.stroke = hsl(i * 90.0 / coordinates.size, 0.5, 0.3, 0.3).toRGBa().opacify(0.3)
                        drawer.strokeWeight = 3.0
                        drawer.lineSegments(done.map { coord ->  coord.position})
                    }
                }
//                println(".. running")

//

                drawer.image(rt1.colorBuffer(0))
                drawer.image(rt0.colorBuffer(0))



//                coordinates
//                    .forEachIndexed { i, it ->
////                        drawer.stroke = hsl(i * 180.0 / coordinates.size, 0.5, 0.3, 0.3).toRGBa()
//                        it.forEachIndexed { index, coordinate ->
//                            if (index > 0) {
//                                if (coordinate.time < seconds.toLong() * SPEED_UP_FACTOR) {
//                                    drawer.stroke = hsl(i * 180.0 / coordinates.size, 0.5, 0.3, 0.3).toRGBa().opacify(0.2)
//                                    drawer.strokeWeight = 3.0
//                                } else {
//                                    drawer.stroke = hsl(i * 180.0 / coordinates.size, 0.5, 0.3, 0.3).toRGBa().opacify(0.1)
//                                    drawer.strokeWeight = 1.0
//                                }
//                                drawer.lineSegment(coordinate.position, it[index - 1].position)
//                            }
//                        }
//                    }



            }
        }
    }
}