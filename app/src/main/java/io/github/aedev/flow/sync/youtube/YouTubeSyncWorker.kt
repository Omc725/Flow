package io.github.aedev.flow.sync.youtube

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface YouTubeSyncEntryPoint {
    fun syncManager(): YouTubeSyncManager

    fun syncPreferences(): YouTubeSyncPreferences
}

class YouTubeSyncWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    companion object {
        const val PERIODIC_WORK_NAME = "youtube_sync_periodic_work"
        const val ONE_TIME_WORK_NAME = "youtube_sync_immediate_work"

        fun schedule(
            context: Context,
            intervalMinutes: Long,
        ) {
            val validInterval = maxOf(intervalMinutes, 15L) // WorkManager minimum periodic interval is 15 minutes
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                PeriodicWorkRequestBuilder<YouTubeSyncWorker>(validInterval, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        fun syncNow(context: Context) {
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                OneTimeWorkRequestBuilder<YouTubeSyncWorker>()
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    override suspend fun doWork(): Result {
        val entryPoint =
            EntryPointAccessors.fromApplication(
                appContext,
                YouTubeSyncEntryPoint::class.java,
            )
        val syncManager = entryPoint.syncManager()
        val preferences = entryPoint.syncPreferences()

        val state = preferences.currentState()
        if (!state.isLoggedIn || !state.syncEnabled) {
            return Result.success()
        }

        val result = syncManager.sync()
        return if (result.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
