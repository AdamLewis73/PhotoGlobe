package com.photoglobe.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photos: List<PhotoEntity>)

    @Query("SELECT COUNT(*) FROM photos WHERE isHidden = 0")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM photos")
    suspend fun count(): Int

    /**
     * Every visible point. Fine for M1 - the whole library is a few MB of coordinates and
     * MapLibre wants one GeoJSON source anyway.
     *
     * The viewport-bounded query in DESIGN.md section 5 replaces this only if the numbers
     * demand it (hard rule 9 - measure first, and Q-010 has not been answered on real
     * hardware yet).
     */
    @Query("SELECT * FROM photos WHERE isHidden = 0 ORDER BY dateTakenUtc DESC")
    fun observeAll(): Flow<List<PhotoEntity>>

    /** Viewport query, ready for when it is needed. Uses the geohash index. */
    @Query(
        """
        SELECT * FROM photos
        WHERE isHidden = 0
          AND lat BETWEEN :minLat AND :maxLat
          AND lng BETWEEN :minLng AND :maxLng
        ORDER BY dateTakenUtc DESC
        LIMIT :limit
        """
    )
    suspend fun inBounds(
        minLat: Double, maxLat: Double,
        minLng: Double, maxLng: Double,
        limit: Int = 5000
    ): List<PhotoEntity>


    @Query("SELECT * FROM photos WHERE id IN (:ids) ORDER BY dateTakenUtc DESC")
    suspend fun byIds(ids: List<Long>): List<PhotoEntity>

    @Query("SELECT mediaStoreId FROM photos")
    suspend fun knownMediaStoreIds(): List<Long>

    @Query("DELETE FROM photos WHERE mediaStoreId IN (:ids)")
    suspend fun deleteByMediaStoreIds(ids: List<Long>)
}

@Dao
interface ScanStateDao {
    @Query("SELECT * FROM scan_state WHERE id = 1")
    suspend fun get(): ScanStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: ScanStateEntity)
}
