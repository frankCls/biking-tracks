package config

import org.openrndr.color.ColorRGBa

/**
 * Centralized configuration and constants for the biking tracks visualization application
 * Organized by functional category for better maintainability
 */
object AppConstants {
    
    // =====================
    // Visualization Constants
    // =====================
    const val SPEED_UP_FACTOR = 20
    const val ELEVATION_SMOOTHING = 0.5
    const val SCALE_FACTOR = 0.015
    const val MAP_SCALE_MULTIPLIER = 2
    
    // =====================
    // Geographic Constants
    // =====================
    const val EARTH_RADIUS = 6_371_000.0 // meters
    
    // =====================
    // UI and Layout Constants
    // =====================
    const val DEFAULT_WIDTH = 1400
    const val DEFAULT_HEIGHT = 900
    const val TEMPLATE_WIDTH = 768
    const val TEMPLATE_HEIGHT = 576
    
    // =====================
    // Rendering Constants
    // =====================
    const val DEFAULT_STROKE_WEIGHT = 0.5
    const val THICK_STROKE_WEIGHT = 3.0
    const val THIN_STROKE_WEIGHT = 0.1
    const val ELEVATION_GRID_STEP = 10
    const val CIRCLE_RADIUS = 4.0
    const val LARGE_CIRCLE_RADIUS = 140.0
    
    // =====================
    // Font and Text Constants
    // =====================
    const val STATISTICS_FONT_SIZE = 24.0
    const val TEMPLATE_FONT_SIZE = 64.0
    const val TEXT_PADDING_LENGTH = 20
    const val TEXT_X_POSITION = 30.0
    const val TEXT_Y_POSITION = 30.0
    const val LINE_HEIGHT = 20
    
    // =====================
    // Opacity Constants
    // =====================
    const val COMMUNE_OPACITY = 0.3
    const val ROUTE_OPACITY = 0.3
    const val STATISTICS_OPACITY = 0.5
    const val REMAINING_ROUTE_OPACITY = 0.2
    const val TEMPLATE_SHADE = 0.2
    
    // =====================
    // Animation and Timing Constants
    // =====================
    const val SPEED_CALCULATION_SAMPLE_SIZE = 10
    const val SPEED_CONVERSION_FACTOR = 3.6 // m/s to km/h
    const val DISTANCE_CONVERSION_FACTOR = 1000 // meters to km
    const val SECONDS_PER_HOUR = 3600
    const val SECONDS_PER_MINUTE = 60
    const val ANIMATION_TIME_FACTOR = 0.5
    
    // =====================
    // File and Path Constants
    // =====================
    const val DEFAULT_FONT_PATH = "data/fonts/default.otf"
    const val TEST_IMAGE_PATH = "data/images/test.png"
    const val PM5544_IMAGE_PATH = "data/images/pm5544.png"
    const val GPX_DIRECTORY = "data/gpx"
    const val SHAPEFILE_PATH = "data/shapefile/belgium-communes/communes_L08.shp"
    
    // =====================
    // Web Map Service Constants
    // =====================
    const val WMS_SERVICE_URL = "https://wms.ngi.be/inspire/ortho/service?request=GetCapabilities&service=WMS&version=1.3.0"
    const val SRS_EPSG_4326 = "EPSG:4326"
    const val SRS_EPSG_3812 = "EPSG:3812"
    const val IMAGE_FORMAT_PNG = "image/png"
    const val WMS_WIDTH = "2326"
    const val WMS_HEIGHT = "1680"
    
    // =====================
    // Map Projection Constants
    // =====================
    const val SAMPLE_MIN_X = 205921.31170791126
    const val SAMPLE_MIN_Y = 5640958.54765713
    const val SAMPLE_MAX_X = 279963.7620163895
    const val SAMPLE_MAX_Y = 5692233.975375663
    
    // =====================
    // Color Palette
    // =====================
    object Colors {
        // Main color palette - carefully chosen for visual distinction and accessibility
        val DEEP_TEAL = ColorRGBa.fromHex(0x005f73)          // Deep blue-green for primary routes
        val TURQUOISE = ColorRGBa.fromHex(0x0a9396)          // Vibrant teal for secondary elements
        val SAGE_GREEN = ColorRGBa.fromHex(0x94d2bd)         // Light mint green for backgrounds
        val CREAM = ColorRGBa.fromHex(0xe9d8a6)              // Warm neutral cream
        val AMBER = ColorRGBa.fromHex(0xee9b00)              // Bright orange-yellow
        val BURNT_ORANGE = ColorRGBa.fromHex(0xca6702)       // Rich orange-brown
        val BRICK_RED = ColorRGBa.fromHex(0xbb3e03)          // Bold red-orange
        val DARK_RED = ColorRGBa.fromHex(0xae2012)           // Deep red
        val CRIMSON = ColorRGBa.fromHex(0x9b2226)            // Dark red-burgundy
        
        // Commented out - originally used but removed from active palette
        // val CHARCOAL = ColorRGBa.fromHex(0x001219)        // Very dark blue-black
        
        // Standard colors for UI elements
        val WHITE = ColorRGBa.WHITE
        val BLACK = ColorRGBa.BLACK
        val TRANSPARENT = ColorRGBa.TRANSPARENT
        val PINK = ColorRGBa.PINK
        
        // Complete color palette for route visualization
        val ROUTE_PALETTE = listOf(
            DEEP_TEAL,
            TURQUOISE,
            SAGE_GREEN,
            CREAM,
            AMBER,
            BURNT_ORANGE,
            BRICK_RED,
            DARK_RED,
            CRIMSON
        )
    }
}