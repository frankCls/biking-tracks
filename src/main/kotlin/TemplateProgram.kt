import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadFont
import org.openrndr.draw.loadImage
import org.openrndr.draw.tint
import config.AppConstants
import config.AppConstants.Colors
import kotlin.math.cos
import kotlin.math.sin

fun main() = application {
    configure {
        width = AppConstants.TEMPLATE_WIDTH
        height = AppConstants.TEMPLATE_HEIGHT
    }

    program {
        val image = loadImage(AppConstants.PM5544_IMAGE_PATH)
        val font = loadFont(AppConstants.DEFAULT_FONT_PATH, AppConstants.TEMPLATE_FONT_SIZE)

        extend {
            drawer.drawStyle.colorMatrix = tint(Colors.WHITE.shade(AppConstants.TEMPLATE_SHADE))
            drawer.image(image)

            drawer.fill = Colors.PINK
            drawer.circle(cos(seconds) * width / 2.0 + width / 2.0, sin(AppConstants.ANIMATION_TIME_FACTOR * seconds) * height / 2.0 + height / 2.0, AppConstants.LARGE_CIRCLE_RADIUS)

            drawer.fontMap = font
            drawer.fill = Colors.WHITE
            drawer.text("OPENRNDR", width / 2.0, height / 2.0)
        }
    }
}
