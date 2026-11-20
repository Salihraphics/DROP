package app.drop

import android.app.Application
import app.drop.data.AppSettings
import app.drop.data.BackupManager
import app.drop.data.DropDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds DROP's services. Each is created on first use. */
class DropApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: DropDatabase by lazy { DropDatabase.create(this) }
    val settings: AppSettings by lazy { AppSettings(this) }
    val backups: BackupManager by lazy { BackupManager(database) }

    val versionName: String by lazy {
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
    }

    override fun onCreate() {
        super.onCreate()
        recordLaunchState()
    }

    /** Decides once per installation whether this is someone new or someone who already used DROP. */
    private fun recordLaunchState() {
        val info = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        settings.initLaunchState(
            databaseExisted = getDatabasePath(DropDatabase.FILE_NAME).exists(),
            updatedInPlace = info != null && info.lastUpdateTime > info.firstInstallTime,
            currentVersion = versionName,
        )
    }
}
