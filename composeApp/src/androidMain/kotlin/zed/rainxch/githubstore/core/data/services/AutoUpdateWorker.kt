package zed.rainxch.githubstore.core.data.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import zed.rainxch.githubstore.core.data.local.db.entities.InstalledApp
import zed.rainxch.githubstore.core.data.services.installer.AndroidInstaller
import zed.rainxch.githubstore.core.domain.repository.InstalledAppsRepository
import java.util.concurrent.TimeUnit

class AutoUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val installedAppsRepository: InstalledAppsRepository by inject()
    private val downloader: Downloader by inject()
    private val installer: AndroidInstaller by inject()

    override suspend fun doWork(): Result {
        Logger.d { "AutoUpdateWorker started" }

        if (!installer.isShizukuAvailable()) {
            Logger.d { "Shizuku not available, skipping auto-update" }
            return Result.success()
        }

        return try {
            val appsToUpdate = installedAppsRepository.getAllInstalledApps()
                .first()
                .filter { it.isUpdateAvailable && it.updateCheckEnabled && it.autoUpdateEnabled }

            if (appsToUpdate.isEmpty()) {
                Logger.d { "No apps need updates" }
                return Result.success()
            }

            Logger.d { "Found ${appsToUpdate.size} apps to auto-update" }

            var successCount = 0
            var failCount = 0

            appsToUpdate.forEach { app ->
                try {
                    updateApp(app)
                    successCount++
                } catch (e: Exception) {
                    Logger.e(e) { "Failed to auto-update ${app.appName}" }
                    failCount++
                }
            }

            Logger.d { "Auto-update completed: $successCount success, $failCount failed" }

            showUpdateNotification(successCount, failCount)

            Result.success()
        } catch (e: Exception) {
            Logger.e(e) { "AutoUpdateWorker failed" }
            Result.retry()
        }
    }

    private suspend fun updateApp(app: InstalledApp) {
        Logger.d { "Auto-updating ${app.appName}" }

        val assetUrl = app.latestAssetUrl ?: throw IllegalStateException("No asset URL")
        val assetName = app.latestAssetName ?: throw IllegalStateException("No asset name")

        var filePath: String? = null
        downloader.download(assetUrl, assetName).collect { progress ->
            if (progress.percent == 100) {
                filePath = downloader.getDownloadedFilePath(assetName)
            }
        }

        if (filePath == null) {
            throw IllegalStateException("Download failed")
        }

        installer.installViaShizukuWithProgress(java.io.File(filePath!!)).collect { progress ->
            when (progress) {
                is AndroidInstaller.InstallationProgress.Success -> {
                    Logger.d { "Auto-update successful for ${app.appName}" }

                    installedAppsRepository.updateAppVersion(
                        packageName = app.packageName,
                        newTag = app.latestVersion,
                        newAssetName = app.latestAssetName ?: "",
                        newAssetUrl = app.latestAssetUrl ?: "",
                        newVersionName = app.latestVersionName ?: "",
                    )
                }
                is AndroidInstaller.InstallationProgress.Error -> {
                    throw IllegalStateException(progress.message)
                }
                else -> { /* Progress updates */ }
            }
        }
    }

    private fun showUpdateNotification(successCount: Int, failCount: Int) {
    }

    companion object {
        private const val WORK_NAME = "auto_update_worker"

        /**
         * Schedules periodic auto-update checks.
         */
        fun schedule(context: Context, intervalHours: Long = 6) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<AutoUpdateWorker>(
                intervalHours, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Logger.d { "AutoUpdateWorker scheduled (every $intervalHours hours)" }
        }

        /**
         * Cancels the auto-update worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Logger.d { "AutoUpdateWorker cancelled" }
        }
    }
}