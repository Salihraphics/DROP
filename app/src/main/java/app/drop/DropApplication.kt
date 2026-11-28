package app.drop

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import app.drop.attach.AttachmentRepository
import app.drop.attach.AttachmentStore
import app.drop.data.AppSettings
import app.drop.data.BackupManager
import app.drop.data.DropDatabase
import app.drop.echo.EchoMemory
import app.drop.echo.EchoRepository
import app.drop.history.NoteHistory
import app.drop.links.LinkResolver
import app.drop.ocr.AndroidPageRenderer
import app.drop.ocr.OcrQueue
import app.drop.ocr.TesseractOcrEngine
import app.drop.resurface.AndroidAlarmScheduler
import app.drop.resurface.AndroidResurfaceNotifier
import app.drop.resurface.Resurfacer
import app.drop.templates.TemplateStore
import app.drop.ui.notes.BulkActions
import app.drop.vault.VaultDatabase
import app.drop.vault.VaultRepository
import app.drop.vault.VaultSession
import app.drop.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    val echo: EchoRepository by lazy { EchoRepository(database.echoDao(), settings.echoEnabled, appScope) }
    val echoMemory: EchoMemory by lazy { EchoMemory(this) }
    val templates: TemplateStore by lazy { TemplateStore(database) }
    val history: NoteHistory by lazy { NoteHistory(database) }
    val bulk: BulkActions by lazy { BulkActions(database, onTrashed = { resurfacer.onTrashed(it) }) }
    val ocr: OcrQueue by lazy {
        OcrQueue(database.attachmentDao(), attachments.store, { TesseractOcrEngine(this) }, AndroidPageRenderer(), appScope)
    }
    val vaultRepository: VaultRepository by lazy { VaultRepository(VaultDatabase.create(this)) }
    private val vaultSession = lazy { VaultSession({ vaultRepository }, appScope) }
    val vault: VaultSession by vaultSession
    private var started = false

    val versionName: String by lazy {
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
    }

    override fun onCreate() {
        super.onCreate()
        recordLaunchState()
        WidgetRefresher(this, database, settings, appScope).start()
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = lockVault()
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Work that runs once per process, after the first screen asked for it. */
    fun startBackgroundWorkOnce() {
        if (started) return
        started = true
        appScope.launch { runCatching { attachments.sweep() } }
        appScope.launch { runCatching { resurfacer.sync() } }
        ocr.resume()
    }

    /** Locks the Vault when DROP leaves the screen or the screen turns off. */
    fun lockVault() {
        if (vaultSession.isInitialized()) vault.lock()
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
