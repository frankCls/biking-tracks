package geotools

import org.geotools.ows.wms.WMSUtils
import org.geotools.ows.wms.WebMapServer
import org.geotools.ows.wms.response.GetMapResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import javax.imageio.ImageIO

fun downloadAerialView(minX: Double, minY: Double, maxX: Double, maxY: Double, width: Int, height: Int): Unit {

    val wms =
        WebMapServer(URL("https://wms.ngi.be/inspire/ortho/service?request=GetCapabilities&service=WMS&version=1.3.0"))
    val capabilities = wms.capabilities
    val r = capabilities.request
    val legendGraphic = r.getLegendGraphic
    legendGraphic.formats[0]
    val layers = WMSUtils.getNamedLayers(capabilities)
    layers.forEach { it.name }

    val request = wms.createGetMapRequest()

    val layer = capabilities.layer.children[0]

    request.finalURL
    request.setSRS("EPSG:4326")
    request.setFormat("image/png")
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
    ImageIO.write(bufferedImage, "png", File("data/images/test.png"))
}


fun main() {
    val wms =
        WebMapServer(URL("https://wms.ngi.be/inspire/ortho/service?request=GetCapabilities&service=WMS&version=1.3.0"))
    val capabilities = wms.capabilities
    val r = capabilities.request
    val legendGraphic = r.getLegendGraphic
    legendGraphic.formats[0]
    val layers = WMSUtils.getNamedLayers(capabilities)
    layers.forEach { it.name }

    val request = wms.createGetMapRequest()

    val layer = capabilities.layer.children[0]

    request.finalURL
    request.setSRS("EPSG:4326")
    request.setFormat("image/png")
    request.setBBox(layer.boundingBoxes["EPSG:4326"])
    request.setTransparent(true)
    request.setDimensions("2326", "1680")
    request.addLayer(layers[0])
    request.addLayer(layers[1])

    val response = wms.issueRequest(request) as GetMapResponse
    val bytes = response.inputStream.readAllBytes()
    val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
    ImageIO.write(bufferedImage, "png", File("data/images/test.png"))
}