package com.photoglobe.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodic background sync, so the map is already current before the app is opened (D-006).
 *
 * This is the half of the sync story the user never sees. The other half runs on resume.
 * Between them, a cold start after the first scan is a database read rather than a scan -
 * which is the entire point of D-020.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = PhotoGlobeDatabase.get(applicationContext)
            LibrarySync(applicationContext, db.photoDao(), db.scanStateDao()).sync()
            Result.success()
        } catch (t: Throwable) {
            // Nothing is lost by failing - the next resume syncs anyway.
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "photoglobe-library-sync"

        /**
         * Idempotent - safe to call on every launch. KEEP means an existing schedule is left
         * alone rather than restarted, so the interval does not drift with app launches.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
