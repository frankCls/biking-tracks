import gpx.convertToCoordinates
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.color.rgb
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
            val conversion = convertToCoordinates(file.absolutePath, 0.01, width, height)

            val coordinates = conversion.routes
                .map {
                    it.map { coordinate ->
                        CoordinateWithTime(
                            Vector2(coordinate.x, coordinate.y),
                            coordinate.time
                        )
                    }
                }

            extend {
                drawer.translate(Vector2((width - conversion.width) / 2, (-height + conversion.height) / 2))
                drawer.clear(ColorRGBa.BLACK)

                coordinates
                    .forEach {
//                        drawer.stroke = hsl(i * 180.0 / coordinates.size, 0.5, 0.3, 0.3).toRGBa()
                        it.forEachIndexed { index, coordinate ->
                            if (index > 0) {
                                if (coordinate.time < seconds.toLong() * SPEED_UP_FACTOR) {
                                    drawer.stroke = rgb(1.0, 1.0, 1.0, 0.2)
                                    drawer.strokeWeight = 3.0
                                } else {
                                    drawer.stroke = rgb(1.0, 1.0, 1.0, 0.1)
                                    drawer.strokeWeight = 1.0
                                }
                                drawer.lineSegment(coordinate.position, it[index - 1].position)
                            }

                        }
                    }
            }
        }
    }
}