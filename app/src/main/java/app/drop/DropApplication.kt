package app.drop

import android.app.Application
import app.drop.attach.AttachmentRepository
import app.drop.attach.AttachmentStore
import app.drop.data.AppSettings
import app.drop.data.BackupManager
import app.drop.data.DropDatabase
import app.drop.links.LinkResolver
import app.drop.resurface.AndroidAlarmScheduler
import app.drop.resurface.AndroidResurfaceNotifier
import app.drop.resurface.Resurfacer
import app.drop.templates.TemplateStore
import app.drop.ui.notes.BulkActions
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds DROP's services. Each is created on first use. */
class DropApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: DropDatabase by lazy { DropDatabase.create(this) }
    val settings: AppSettings by lazy { AppSettings(this) }
    val backups: BackupManager by lazy { BackupManager(database) }
    val attachments: AttachmentRepository by lazy { AttachmentRepository(database, AttachmentStore(filesDir)) }
    val resurfaceNotifier: AndroidResurfaceNotifier by lazy { AndroidResurfaceNotifier(this) }
    val resurfacer: Resurfacer by lazy {
        Resurfacer(database.reminderDao(), database.noteDao(), AndroidAlarmScheduler(this), resurfaceNotifier)
    }
    val links: LinkResolver by lazy { LinkResolver(database.noteDao(), database.linkDao(), database.metaDao()::activeAliases) }
    val templates: TemplateStore by lazy { TemplateStore(database) }
    val bulk: BulkActions by lazy { BulkActions(database, onTrashed = { resurfacer.onTrashed(it) }) }
    private var started = false

    val versionName: String by lazy {
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
    }

    override fun onCreate() {
        super.onCreate()
        recordLaunchState()
    }

    /** Work that runs once per process, after the first screen asked for it. */
    fun startBackgroundWorkOnce() {
        if (started) return
        started = true
        appScope.launch { runCatching { attachments.sweep() } }
        appScope.launch { runCatching { resurfacer.sync() } }
    }

    /** Locks the Vault when DROP leaves the screen. */
    fun lockVault() = Unit

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
