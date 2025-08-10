package geotools

import config.AppConstants
import org.geotools.ows.wms.WMSUtils
import org.geotools.ows.wms.WebMapServer
import org.geotools.ows.wms.response.GetMapResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import javax.imageio.ImageIO

fun downloadAerialView(minX: Double, minY: Double, maxX: Double, maxY: Double, width: Int, height: Int): Unit {

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
    //A string representing a bounding box in the format "minx,miny,maxx,maxy"
    request.setBBox("$minX,$minY,$maxX,$maxY")
//    request.setBBox(layer.boundingBoxes["EPSG:4326"])
    request.setTransparent(true)
    request.setDimensions(width.toString(), height.toString())
    request.addLayer(layers[0])
    request.addLayer(layers[1])

    val response = wms.issueRequest(request) as GetMapResponse
    val bytes = response.inputStream.readAllBytes()
    val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
    ImageIO.write(bufferedImage, "png", File(AppConstants.TEST_IMAGE_PATH))
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