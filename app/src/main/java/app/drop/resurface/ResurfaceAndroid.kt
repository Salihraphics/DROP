package app.drop.resurface

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.drop.DropApplication
import app.drop.MainActivity
import app.drop.R
import app.drop.widget.runAsync

class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {
    private val manager get() = context.getSystemService(AlarmManager::class.java)

    private fun pending(): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ResurfaceReceiver::class.java).setAction(ResurfaceReceiver.ACTION_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** An inexact alarm: no special permission, and it still fires in Doze. */
    override fun set(atMillis: Long) {
        manager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending())
    }

    override fun cancel() {
        manager?.cancel(pending())
    }
}

class AndroidResurfaceNotifier(private val context: Context) : ResurfaceNotifier {
    override fun canNotify(): Boolean {
        val permitted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    override fun show(noteId: Long, title: String) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, notificationId(noteId), MainActivity.resurfacedIntent(context, noteId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val heading = context.getString(R.string.resurface_notification_title)
        // The lock screen shows only that something came back, never the note's text.
        val public = NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_resurface).setContentTitle(heading).build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_resurface)
            .setContentTitle(heading)
            .setContentText(title.ifBlank { context.getString(R.string.note_empty) })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(noteId), notification)
        } catch (e: SecurityException) {
            // Notifications were turned off: the reminder still shows in DROP.
        }
    }

    override fun cancel(noteId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(noteId))
    }

    companion object {
        const val CHANNEL_ID = "resurface"

        fun notificationId(noteId: Long): Int = (noteId % Int.MAX_VALUE).toInt()

        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.resurface_channel), NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = context.getString(R.string.resurface_channel_description)
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}

/** The alarm went off: show what is due and set the next alarm. */
class ResurfaceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_ALARM) runAsync { (context.applicationContext as DropApplication).resurfacer.sync() }
    }

    companion object {
        const val ACTION_ALARM = "app.drop.resurface.ALARM"
    }
}

/** Alarms don't survive a restart or a clock change: set them again. */
class ResurfaceSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in HANDLED) runAsync { (context.applicationContext as DropApplication).resurfacer.sync() }
    }

    companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED,
        )
    }
}
