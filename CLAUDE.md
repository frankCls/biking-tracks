# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a biking tracks visualization project that combines GPX data processing with graphical rendering using OPENRNDR (a Kotlin-based creative coding framework). The application reads GPX files from biking routes (downloaded from Komoot) and creates visual representations using OPENRNDR's graphics capabilities.

## Key Technologies

- **OPENRNDR**: Kotlin creative coding framework for graphics and visualization
- **JPX**: Java library for reading and writing GPX files
- **GeoTools**: Geospatial data processing (WMS, Shapefiles, EPSG coordinate systems)
- **Kotlin**: Primary programming language
- **Gradle**: Build system with Kotlin DSL

## Architecture

The codebase is organized into several specialized modules:

### Core Components

- `TemplateProgram.kt`: Main OPENRNDR application entry point
- `TemplateLiveProgram.kt`: Live coding version for development
- `gpx/Gpx.kt`: GPX file reading and waypoint extraction
- `geotools/Shapefile.kt`: Shapefile processing for map data
- `geotools/WebMapServierClient.kt`: Web Map Service integration
- `pixels/PixelsCalculation.kt`: Coordinate and pixel calculations

### Data Structure

- `data/gpx/`: Contains GPX files downloaded from Komoot biking routes
- `data/images/`: Image assets for visualization
- `data/fonts/`: Font files for text rendering
- `data/shapefile/belgium-communes/`: Belgian administrative boundary data

## Development Commands

### Build and Run
```bash
./gradlew run                    # Run main TemplateProgram
./gradlew run -Popenrndr.application=MyProgramKt  # Run specific program
./gradlew build                  # Build project
./gradlew clean                  # Clean build artifacts
```

### Testing
```bash
./gradlew test                   # Run test suite
./gradlew check                  # Run all verification tasks
```

### Packaging
```bash
./gradlew shadowJar              # Create executable JAR with dependencies
./gradlew jpackage               # Create platform-specific executable
./gradlew jpackageZip            # Create zipped standalone executable
```

### GPX Data Management

Download GPX files from Komoot:
```bash
pip install komootgpx
komootgpx -m 'email' -p 'password' -a -f 'recorded' -o './data/gpx'
```

Fix GPX time format issues:
```bash
python fix-gpx.py <gpx-file>     # Converts timestamps to UTC format
```

## OPENRNDR Configuration

The project uses these OPENRNDR/ORX features:
- `orx-camera`: Camera controls
- `orx-color`: Color utilities
- `orx-compositor`: Image composition
- `orx-fx`: Visual effects
- `orx-gui`: User interface components
- `orx-image-fit`: Image fitting utilities
- `orx-noise`: Noise generation
- `orx-panel`: Control panels
- `orx-shade-styles`: Styling
- `orx-shapes`: Shape utilities
- `orx-video-profiles`: Video handling
- `orx-view-box`: Viewport management

## Coordinate System Transformations

The project implements a sophisticated coordinate transformation pipeline to convert GPS data into pixel coordinates:

### Coordinate Reference Systems
- **EPSG:3812** (Belgian Lambert 2008): Used for shapefile data transformation
- **EPSG:4326** (WGS84): Standard GPS coordinate system for GPX data
- **Equirectangular Projection**: Used for meter-to-pixel coordinate conversion

### Transformation Process
1. **GPS to Meters**: Convert lat/lng to x,y coordinates using equirectangular projection
   - `x = EARTH_RADIUS * λ * cos(φ₀)` (longitude with latitude compensation)
   - `y = EARTH_RADIUS * φ` (latitude)
   - Where `EARTH_RADIUS = 6,371,000 meters`

2. **Normalization**: Center coordinates around map bounds and apply scaling factor

3. **Pixel Mapping**: Transform to screen coordinates with proper aspect ratio handling

### Key Implementation Files
- `pixels/PixelsCalculation.kt`: Core transformation functions
- `geotools/Shapefile.kt`: CRS transformations for Belgian administrative data
- `TemplateLiveProgram.kt`: Integration of transformations in rendering pipeline

## Important Notes

- Ensure all GPX files are from the same geographical area to prevent excessive zoom-out when displaying the map
- The application uses Log4j2 for logging with YAML configuration
- Cross-platform builds are supported via `./gradlew jar -PtargetPlatform=<platform>`
- The main class is configurable via `applicationMainClass` in build.gradle.kts
- Coordinate transformations preserve geographic accuracy while fitting routes within display bounds