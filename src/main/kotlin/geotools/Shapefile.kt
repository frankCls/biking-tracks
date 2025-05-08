package geotools

import org.geotools.data.FeatureSource
import org.geotools.data.FileDataStoreFinder
import org.geotools.feature.FeatureCollection
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.MultiPolygon
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType
import org.opengis.filter.Filter
import org.opengis.referencing.crs.CoordinateReferenceSystem
import org.opengis.referencing.operation.MathTransform
import java.io.File

val geometryFactory = GeometryFactory()
val sourceCrs: CoordinateReferenceSystem = CRS.decode("EPSG:3812")
val targetCrs: CoordinateReferenceSystem = CRS.decode("EPSG:4326")

val mathTransform: MathTransform = CRS.findMathTransform(sourceCrs, targetCrs, false)

data class Commune(
    val name: String,
    val geometry: List<Coordinate>,
    val nisCode: String
)

fun communes(
    shapeFile: File,
    left: Double, top: Double, right: Double, bottom: Double
): List<Commune> {

    val dataStore = FileDataStoreFinder.getDataStore(shapeFile)
    val typeName = dataStore.typeNames[0]

    val source: FeatureSource<SimpleFeatureType, SimpleFeature> = dataStore.getFeatureSource(typeName)
    val filter: Filter = Filter.INCLUDE // ECQL.toFilter("BBOX(THE_GEOM, 10,20,30,40)")
    val collection: FeatureCollection<SimpleFeatureType, SimpleFeature> = source.getFeatures(filter)
    val communes: MutableList<Commune> = mutableListOf()
    collection.features().use { features ->
        while (features.hasNext()) {
            val feature = features.next()

            var name = ""
            var geometry = emptyList<Coordinate>()
            var ncisCode = 0
            for (attribute in feature.properties) {
                when (val value = attribute.value) {
                    is MultiPolygon -> geometry = value.coordinates.asList()
                    is String -> name = value
                    is Int -> ncisCode = value
                }
            }
            communes.add(Commune(name, geometry, ncisCode.toString()))
        }
    }
    return communes
        .map { commune ->
            Commune(
                name = commune.name,
                geometry = transform(commune.geometry)
                    .filter {
                        it.x in left..right && it.y in top..bottom
                    },
                nisCode = commune.nisCode
            )
        }
        .filter { it.geometry.isNotEmpty() }
}

fun main() {
    val dir = "data/shapefile/"
    val proj = "belgium-communes/communes_L08"
//    val proj = "belgium-shapefile/be_100km"
    val shapeFile = File("$dir/$proj.shp")
    val projectFile = File("$dir/$proj.prj")
    // https://epsg.io/3812  ESPG:3812
    // https://8thlight.com/insights/geographic-coordinate-systems-101#:~:text=EPSG%3A4326%2C%20also%20known%20as,issues%20of%20the%20Web%20Mercator.
    // https://epsg.io/4326  EPSG:4326
//    val shapeFile = File("data/shapefile/belgium-communes/communes_L08.shp")
//    val shapeFile = File("data/shapefile/belgium-shapefile/be_100km.shp")
//    val projectFile = File("data/shapefile/belgium-shapefile/be_100km.prj")
    val lines = projectFile.readLines()

    val parseWKT = CRS.parseWKT(lines[0])
    println(parseWKT)
    parseWKT.identifiers.forEach {
        println(it.code)
    }

    val communes = communes(shapeFile, 205921.31170791126, 5640958.54765713, 279963.7620163895, 5692233.975375663)
    communes.forEach {
        println("${it.name} ${it.nisCode} ${it.geometry[0]}")
    }
}


fun transform(coordinates: List<Coordinate>): List<Coordinate> {

    return coordinates.map { coordinate ->
        val point = geometryFactory.createPoint(coordinate)
        val transformedPoint = JTS.transform(point, mathTransform)
        transformedPoint.coordinate
    }
}