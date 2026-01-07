package zed.rainxch.githubstore.feature.details.domain.use_case

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.githubstore.core.data.services.Downloader
import java.util.concurrent.atomic.AtomicBoolean

class CleanupStaleDownloadsUseCase(
    private val downloader: Downloader
) {
    private val hasRunThisSession = AtomicBoolean(false)
    
    suspend operator fun invoke() {
        if (!hasRunThisSession.compareAndSet(false, true)) {
            Logger.d { "Stale downloads cleanup already ran this session" }
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val allFiles = downloader.listDownloadedFiles()
                val staleThreshold = System.currentTimeMillis() - (24 * 60 * 60 * 1000) // 24 hours
                val staleFiles = allFiles.filter { it.downloadedAt < staleThreshold }
                
                if (staleFiles.isNotEmpty()) {
                    Logger.d { "Cleaning up ${staleFiles.size} stale files (older than 24h)" }
                    
                    staleFiles.forEach { file ->
                        try {
                            val deleted = downloader.cancelDownload(file.fileName)
                            if (deleted) {
                                Logger.d { "✓ Cleaned up stale file: ${file.fileName}" }
                            } else {
                                Logger.w { "✗ Failed to delete stale file: ${file.fileName}" }
                            }
                        } catch (e: Exception) {
                            Logger.e { "✗ Error deleting ${file.fileName}: ${e.message}" }
                        }
                    }
                    
                    Logger.d { "Stale files cleanup complete" }
                } else {
                    Logger.d { "No stale files to clean up" }
                }
            } catch (t: Throwable) {
                Logger.e { "Failed to cleanup stale files: ${t.message}" }
            }
        }
    }
}