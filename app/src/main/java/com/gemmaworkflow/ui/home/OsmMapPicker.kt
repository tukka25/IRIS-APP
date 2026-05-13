package com.gemmaworkflow.ui.home

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.cos
import kotlin.math.sin

private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * Returns n GeoPoints that form a circle of given radius around center.
 */
private fun circlePoints(center: GeoPoint, radiusMeters: Double, count: Int = 60): List<GeoPoint> {
    val points = mutableListOf<GeoPoint>()
    for (i in 0 until count) {
        val angle = 2 * Math.PI * i / count
        val dLat = radiusMeters * cos(angle) / EARTH_RADIUS_METERS
        val dLng = radiusMeters * sin(angle) / (EARTH_RADIUS_METERS * cos(Math.toRadians(center.latitude)))
        points.add(GeoPoint(center.latitude + Math.toDegrees(dLat), center.longitude + Math.toDegrees(dLng)))
    }
    points.add(points[0]) // close the polygon
    return points
}

/**
 * In-page OpenStreetMap view where the user taps to set a geofence location.
 * Shows a draggable marker and a circle overlay for the geofence radius.
 */
@Composable
fun OsmMapPicker(
    latitude: Double,
    longitude: Double,
    radiusMeters: Float,
    onLocationSelected: (lat: Double, lng: Double) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var marker by remember { mutableStateOf<Marker?>(null) }
    var circlePoly by remember { mutableStateOf<Polygon?>(null) }

    // Update marker + circle when props change
    LaunchedEffect(latitude, longitude, radiusMeters) {
        val mv = mapView ?: return@LaunchedEffect

        // Remove existing marker and circle
        marker?.let { mv.overlays.remove(it) }
        circlePoly?.let { mv.overlays.remove(it) }

        if (latitude != 0.0 && longitude != 0.0) {
            val center = GeoPoint(latitude, longitude)

            // Add marker
            val m = Marker(mv)
            m.position = center
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            m.title = "Geofence center"
            mv.overlays.add(m)
            marker = m

            // Add circle polygon
            val poly = Polygon()
            poly.points = circlePoints(center, radiusMeters.toDouble())
            poly.fillColor = Color.parseColor("#204A90D9")
            poly.strokeColor = Color.parseColor("#4A90D9")
            poly.strokeWidth = 3f
            mv.overlays.add(poly)
            circlePoly = poly

            mv.controller.animateTo(center)
        } else {
            marker = null
            circlePoly = null
        }

        mv.invalidate()
    }

    DisposableEffect(Unit) {
        onDispose { mapView?.onDetach() }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = if (latitude != 0.0 && longitude != 0.0) "Tap to move pin" else "Tap the map to set geofence location",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(15.0)
                            controller.setCenter(GeoPoint(0.0, 0.0))

                            // Tap overlay to capture location
                            overlays.add(object : Overlay() {
                                override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
                                    if (e == null || mapView == null) return false
                                    val projection = mapView.projection
                                    val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                                    onLocationSelected(geoPoint.latitude, geoPoint.longitude)
                                    return true
                                }
                            })

                            mapView = this
                        }
                    },
                    modifier = Modifier.matchParentSize()
                )
            }

            if (latitude != 0.0 && longitude != 0.0) {
                Text(
                    text = "%.6f, %.6f".format(latitude, longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}