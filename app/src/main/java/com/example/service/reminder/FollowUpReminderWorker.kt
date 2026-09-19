package com.example.service.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class FollowUpReminderWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        ensureChannel(applicationContext)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val id = inputData.getLong(KEY_NOTE_ID, 0L)
        val title = inputData.getString(KEY_TITLE).orEmpty().ifBlank { "Tnote follow-up" }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.aistudio.voicenotes.vnapp.R.drawable.ic_launcher_foreground)
            .setContentTitle("Follow up")
            .setContentText(title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id.hashCode(), notification)
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "follow_up_reminders"
        private const val KEY_NOTE_ID = "note_id"
        private const val KEY_TITLE = "title"
        private fun workName(id: Long) = "follow_up_$id"

        fun schedule(context: Context, noteId: Long, title: String, atMillis: Long) {
            val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            val data = Data.Builder().putLong(KEY_NOTE_ID, noteId).putString(KEY_TITLE, title).build()
            val request = OneTimeWorkRequestBuilder<FollowUpReminderWorker>().setInputData(data).setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(noteId), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, noteId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(noteId))
            NotificationManagerCompat.from(context).cancel(noteId.hashCode())
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Follow-up reminders", NotificationManager.IMPORTANCE_HIGH))
            }
        }
    }
}
