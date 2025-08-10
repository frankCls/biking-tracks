package geotools

import config.AppConstants
import validation.CoordinateValidator
import validation.ValidationResult
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
val sourceCrs: CoordinateReferenceSystem = CRS.decode(AppConstants.SRS_EPSG_3812)
val targetCrs: CoordinateReferenceSystem = CRS.decode(AppConstants.SRS_EPSG_4326)

val mathTransform: MathTransform = CRS.findMathTransform(sourceCrs, targetCrs, false)

data class Commune(
    val name: String,
    val geometry: List<Coordinate>,
    val nisCode: String
)

fun communes(
    shapeFile: File,
    left: Double, top: Double, right: Double, bottom: Double
): ValidationResult<List<Commune>> {
    
    // Validate bounds parameters
    val boundsValidation = CoordinateValidator.validateBounds(left, top, right, bottom)
    if (boundsValidation.isFailure) {
        return ValidationResult.Failure("Bounds validation failed: ${boundsValidation.getErrorOrNull()}")
    }
    
    if (!shapeFile.exists()) {
        return ValidationResult.Failure("Shapefile does not exist: ${shapeFile.absolutePath}")
    }
    
    if (!shapeFile.canRead()) {
        return ValidationResult.Failure("Cannot read shapefile: ${shapeFile.absolutePath}")
    }
    
    try {

        val dataStore = FileDataStoreFinder.getDataStore(shapeFile)
            ?: return ValidationResult.Failure("Could not create data store for shapefile: ${shapeFile.absolutePath}")
        
        if (dataStore.typeNames.isEmpty()) {
            return ValidationResult.Failure("No feature types found in shapefile: ${shapeFile.absolutePath}")
        }
        
        val typeName = dataStore.typeNames[0]

        val source: FeatureSource<SimpleFeatureType, SimpleFeature> = dataStore.getFeatureSource(typeName)
        val filter: Filter = Filter.INCLUDE // ECQL.toFilter("BBOX(THE_GEOM, 10,20,30,40)")
        val collection: FeatureCollection<SimpleFeatureType, SimpleFeature> = source.getFeatures(filter)
        val communes: MutableList<Commune> = mutableListOf()
        
        collection.features().use { features ->
            while (features.hasNext()) {
                try {
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
                    
                    // Validate that we have essential data
                    if (name.isNotBlank() && geometry.isNotEmpty()) {
                        communes.add(Commune(name, geometry, ncisCode.toString()))
                    } else {
                        println("Warning: Skipping commune with missing data - name: '$name', geometry points: ${geometry.size}")
                    }
                } catch (e: Exception) {
                    println("Warning: Error processing feature: ${e.message}")
                    // Continue processing other features
                }
            }
        }
        
        if (communes.isEmpty()) {
            return ValidationResult.Failure("No valid communes found in shapefile")
        }
        
        val filteredCommunes = communes
            .mapNotNull { commune ->
                try {
                    val transformedGeometry = transform(commune.geometry)
                        .filter { coordinate ->
                            // Validate coordinates before filtering
                            val latValid = CoordinateValidator.validateLatitude(coordinate.y).isValid
                            val lngValid = CoordinateValidator.validateLongitude(coordinate.x).isValid
                            
                            if (!latValid || !lngValid) {
                                false // Skip invalid coordinates
                            } else {
                                coordinate.x in left..right && coordinate.y in top..bottom
                            }
                        }
                    
                    if (transformedGeometry.isNotEmpty()) {
                        Commune(
                            name = commune.name,
                            geometry = transformedGeometry,
                            nisCode = commune.nisCode
                        )
                    } else {
                        null // Skip communes with no valid geometry in bounds
                    }
                } catch (e: Exception) {
                    println("Warning: Error transforming commune '${commune.name}': ${e.message}")
                    null // Skip problematic communes
                }
            }
            
        return ValidationResult.Success(filteredCommunes)
        
    } catch (e: Exception) {
        return ValidationResult.Failure("Error processing shapefile: ${e.message}")
    }
}

fun main() {
    // https://epsg.io/3812  ESPG:3812
    // https://8thlight.com/insights/geographic-coordinate-systems-101#:~:text=EPSG%3A4326%2C%20also%20known%20as,issues%20of%20the%20Web%20Mercator.
    // https://epsg.io/4326  EPSG:4326
    val shapeFile = File("data/shapefile/belgium-communes/communes_L08.shp")
//    val projectFile = File("data/shapefile/belgium-communes/communes_L08.prj")
//    val lines = projectFile.readLines()

//    val parseWKT = CRS.parseWKT(lines[0])
//    println(parseWKT)

    val communesResult = communes(shapeFile, AppConstants.SAMPLE_MIN_X, AppConstants.SAMPLE_MIN_Y, AppConstants.SAMPLE_MAX_X, AppConstants.SAMPLE_MAX_Y)
    
    if (communesResult is ValidationResult.Success) {
        val communes = communesResult.value
        println("Successfully loaded ${communes.size} communes")
        communes.forEach {
            if (it.geometry.isNotEmpty()) {
                println("${it.name} ${it.nisCode} ${it.geometry[0]}")
            }
        }
    } else {
        println("Error loading communes: ${(communesResult as ValidationResult.Failure).error}")
    }
}


fun transform(coordinates: List<Coordinate>): List<Coordinate> {
    if (coordinates.isEmpty()) {
        return emptyList()
    }
    
    return coordinates.mapNotNull { coordinate ->
        try {
            // Validate input coordinate
            if (!coordinate.x.isFinite() || !coordinate.y.isFinite()) {
                println("Warning: Skipping invalid coordinate: (${coordinate.x}, ${coordinate.y})")
                return@mapNotNull null
            }
            
            val point = geometryFactory.createPoint(coordinate)
            val transformedPoint = JTS.transform(point, mathTransform)
            val resultCoordinate = transformedPoint.coordinate
            
            // Validate output coordinate
            if (!resultCoordinate.x.isFinite() || !resultCoordinate.y.isFinite()) {
                println("Warning: Transformation produced invalid coordinate: (${resultCoordinate.x}, ${resultCoordinate.y})")
                return@mapNotNull null
            }
            
            resultCoordinate
        } catch (e: Exception) {
            println("Warning: Error transforming coordinate (${coordinate.x}, ${coordinate.y}): ${e.message}")
            null
        }
    }
}