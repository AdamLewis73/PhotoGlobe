package com.photoglobe.data

/**
 * Geohash encoding. See docs/GLOSSARY.md and DESIGN.md section 5 for why this exists.
 *
 * A latitude/longitude pair becomes one short string where nearby places share a leading
 * prefix, which turns "find photos near here" - normally a two-column range scan a database
 * cannot index well - into an ordinary indexed prefix search.
 *
 * Precision 9 is roughly 5 metres, far finer than needed, but the prefix property means a
 * shorter substring can be used for coarser grouping without re-encoding anything.
 */
object Geohash {

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(lat: Double, lng: Double, precision: Int = 9): String {
        var latMin = -90.0; var latMax = 90.0
        var lngMin = -180.0; var lngMax = 180.0

        val out = StringBuilder(precision)
        var bit = 0
        var index = 0
        var even = true      // even bits carve longitude, odd bits carve latitude

        while (out.length < precision) {
            if (even) {
                val mid = (lngMin + lngMax) / 2
                if (lng > mid) { index = index * 2 + 1; lngMin = mid } else { index *= 2; lngMax = mid }
            } else {
                val mid = (latMin + latMax) / 2
                if (lat > mid) { index = index * 2 + 1; latMin = mid } else { index *= 2; latMax = mid }
            }
            even = !even

            if (bit < 4) {
                bit++
            } else {
                out.append(BASE32[index])
                bit = 0
                index = 0
            }
        }
        return out.toString()
    }
}
