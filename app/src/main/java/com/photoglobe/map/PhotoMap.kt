package com.photoglobe.map

import android.content.Context
import com.photoglobe.data.PhotoEntity
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * The clustering layer. This is the MVP (D-013).
 *
 * MapLibre clusters a GeoJSON source natively - `withCluster(true)` - and exposes the size
 * of each cluster as a `point_count` property, which the symbol layer below renders as the
 * count badge (D-014, D-018, D-030).
 *
 * Known gap, accepted in D-036: MapLibre recomputes clusters per zoom level with no
 * tweening, so markers pop rather than animating outward. Google's DefaultClusterRenderer
 * animates this for free (D-035). Hand-built tweening is M5 polish, not MVP scope.
 */
object PhotoMap {

    const val SOURCE_ID = "photos"
    const val LAYER_CLUSTERS = "photo-clusters"
    const val LAYER_COUNT = "photo-cluster-count"
    const val LAYER_SINGLE = "photo-single"

    /** Key-free tile style. One URL, trivially swapped - D-037. */
    const val STYLE_URL = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"

    fun toFeatureCollection(photos: List<PhotoEntity>): FeatureCollection =
        FeatureCollection.fromFeatures(
            photos.map { p ->
                Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply {
                    addNumberProperty("id", p.id)
                }
            }
        )

    fun install(style: Style, photos: List<PhotoEntity>) {
        style.addSource(
            GeoJsonSource(
                SOURCE_ID,
                toFeatureCollection(photos),
                GeoJsonOptions()
                    .withCluster(true)
                    .withClusterRadius(55)
                    .withClusterMaxZoom(14)
            )
        )

        // Cluster bubble. Grows in steps with the number of photos inside it.
        style.addLayer(
            CircleLayer(LAYER_CLUSTERS, SOURCE_ID).apply {
                setFilter(Expression.has("point_count"))
                withProperties(
                    PropertyFactory.circleColor(
                        Expression.step(
                            Expression.get("point_count"),
                            Expression.color(android.graphics.Color.parseColor("#4A9DE0")),
                            Expression.stop(25, Expression.color(android.graphics.Color.parseColor("#2F7FC4"))),
                            Expression.stop(100, Expression.color(android.graphics.Color.parseColor("#1A5F9E")))
                        )
                    ),
                    PropertyFactory.circleRadius(
                        Expression.step(
                            Expression.get("point_count"),
                            Expression.literal(17f),
                            Expression.stop(25, Expression.literal(23f)),
                            Expression.stop(100, Expression.literal(30f))
                        )
                    ),
                    PropertyFactory.circleStrokeWidth(3f),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE)
                )
            }
        )

        // The count badge. This is the number people actually read.
        style.addLayer(
            SymbolLayer(LAYER_COUNT, SOURCE_ID).apply {
                setFilter(Expression.has("point_count"))
                withProperties(
                    PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
                    PropertyFactory.textSize(13f),
                    PropertyFactory.textColor(android.graphics.Color.WHITE),
                    PropertyFactory.textFont(arrayOf("Open Sans Bold", "Arial Unicode MS Bold")),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textIgnorePlacement(true)
                )
            }
        )

        // A lone photo, once zoomed in far enough that it is no longer clustered.
        style.addLayer(
            CircleLayer(LAYER_SINGLE, SOURCE_ID).apply {
                setFilter(Expression.not(Expression.has("point_count")))
                withProperties(
                    PropertyFactory.circleColor(android.graphics.Color.parseColor("#E8543F")),
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE)
                )
            }
        )
    }


    /**
     * What sits under a tap: the photo ids of a cluster's members, or of a single point.
     *
     * Clusters do not carry their members - MapLibre stores only the aggregate and a
     * `cluster_id`. `getClusterLeaves` expands that back into the individual features, which
     * is how tapping a bubble can show the photos inside it (D-016).
     *
     * Returns an empty list when the tap misses everything, so the caller can dismiss.
     */
    fun photoIdsAt(map: MapLibreMap, screenPoint: android.graphics.PointF): List<Long> {
        val style = map.style ?: return emptyList()
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return emptyList()

        // Clusters first - they sit on top and are the larger target.
        map.queryRenderedFeatures(screenPoint, LAYER_CLUSTERS).firstOrNull()?.let { cluster ->
            // A generous cap: past a few hundred the grid is unusable anyway and the
            // bottom sheet should be paging instead (see Q-012).
            val leaves = source.getClusterLeaves(cluster, MAX_LEAVES, 0)
            return leaves.features().orEmpty().mapNotNull { it.getNumberProperty("id")?.toLong() }
        }

        map.queryRenderedFeatures(screenPoint, LAYER_SINGLE).firstOrNull()?.let { single ->
            return listOfNotNull(single.getNumberProperty("id")?.toLong())
        }

        return emptyList()
    }

    private const val MAX_LEAVES = 500L

    /** Called whenever Room emits - during the first scan this fires every batch. */
    fun update(style: Style, photos: List<PhotoEntity>) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        source.setGeoJson(toFeatureCollection(photos))
    }

    fun ensureInstalled(context: Context, map: MapLibreMap, photos: List<PhotoEntity>) {
        val style = map.style ?: return
        if (style.getSource(SOURCE_ID) == null) install(style, photos) else update(style, photos)
    }
}
