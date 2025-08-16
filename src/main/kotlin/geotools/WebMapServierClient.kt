package geotools

import config.AppConstants
import org.geotools.ows.wms.WMSUtils
import org.geotools.ows.wms.WebMapServer
import org.geotools.ows.wms.response.GetMapResponse
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import javax.imageio.ImageIO

/**
 * Download aerial view with caching support
 * Returns BufferedImage and saves to file
 */
fun downloadAerialViewCached(minX: Double, minY: Double, maxX: Double, maxY: Double, width: Int, height: Int): BufferedImage? {
    val bounds = GeographicBounds(minX, minY, maxX, maxY)
    val dimensions = ImageDimensions(width, height)
    val cache = GlobalWMSCache.instance
    
    // Check cache first
    cache.get(bounds, dimensions)?.let { cachedImage ->
        println("Using cached aerial view for bounds: $minX,$minY,$maxX,$maxY")
        // Save cached image to file for OPENRNDR loading
        try {
            ImageIO.write(cachedImage, "png", File(AppConstants.TEST_IMAGE_PATH))
        } catch (e: Exception) {
            println("Error saving cached image to file: ${e.message}")
        }
        return cachedImage
    }
    
    println("Downloading new aerial view for bounds: $minX,$minY,$maxX,$maxY")
    
    try {
        val image = downloadAerialViewFromWMS(minX, minY, maxX, maxY, width, height)
        if (image != null) {
            // Cache the downloaded image
            cache.put(bounds, dimensions, image)
            
            // Save to file for OPENRNDR loading
            ImageIO.write(image, "png", File(AppConstants.TEST_IMAGE_PATH))
            println("Aerial view cached and saved to file successfully")
        }
        return image
    } catch (e: Exception) {
        println("Error downloading aerial view: ${e.message}")
        return null
    }
}

/**
 * Direct WMS download without caching (internal use)
 */
private fun downloadAerialViewFromWMS(minX: Double, minY: Double, maxX: Double, maxY: Double, width: Int, height: Int): BufferedImage? {
    return try {
        val wms = WebMapServer(URL(AppConstants.WMS_SERVICE_URL))
        val capabilities = wms.capabilities
        val layers = WMSUtils.getNamedLayers(capabilities)
        
        val request = wms.createGetMapRequest()
        request.setSRS(AppConstants.SRS_EPSG_4326)
        request.setFormat(AppConstants.IMAGE_FORMAT_PNG)
        request.setBBox("$minX,$minY,$maxX,$maxY")
        request.setTransparent(true)
        request.setDimensions(width.toString(), height.toString())
        request.addLayer(layers[0])
        request.addLayer(layers[1])

        val response = wms.issueRequest(request) as GetMapResponse
        val bytes = response.inputStream.readAllBytes()
        ImageIO.read(ByteArrayInputStream(bytes))
    } catch (e: Exception) {
        println("WMS request failed: ${e.message}")
        null
    }
}

/**
 * Legacy function for backward compatibility - saves to file
 */
fun downloadAerialView(minX: Double, minY: Double, maxX: Double, maxY: Double, width: Int, height: Int): Unit {
    val image = downloadAerialViewCached(minX, minY, maxX, maxY, width, height)
    if (image != null) {
        try {
            ImageIO.write(image, "png", File(AppConstants.TEST_IMAGE_PATH))
            println("Aerial view saved to ${AppConstants.TEST_IMAGE_PATH}")
        } catch (e: Exception) {
            println("Error saving aerial view to file: ${e.message}")
        }
    } else {
        println("No aerial view to save - download failed")
    }
}


fun main() {
    val wms =
        WebMapServer(URL(AppConstants.WMS_SERVICE_URL))
    val capabilities = wms.capabilities
    val r = capabilities.request
    val legendGraphic = r.getLegendGraphic
    legendGraphic.formats[0]
    val layers = WMSUtils.getNamedLayers(capabilities)
    layers.forEach { it.name }

    val request = wms.createGetMapRequest()

    val layer = capabilities.layer.children[0]

    request.finalURL
    request.setSRS(AppConstants.SRS_EPSG_4326)
    request.setFormat(AppConstants.IMAGE_FORMAT_PNG)
    request.setBBox(layer.boundingBoxes["EPSG:4326"])
    request.setTransparent(true)
    request.setDimensions(AppConstants.WMS_WIDTH, AppConstants.WMS_HEIGHT)
    request.addLayer(layers[0])
    request.addLayer(layers[1])

    val response = wms.issueRequest(request) as GetMapResponse
    val bytes = response.inputStream.readAllBytes()
    val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
    ImageIO.write(bufferedImage, "png", File(AppConstants.TEST_IMAGE_PATH))
}