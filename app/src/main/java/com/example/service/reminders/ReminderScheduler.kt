package com.example.service.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    fun schedule(context: Context, noteId: Long, title: String, atMillis: Long) {
        val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<NoteReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong("note_id", noteId).putString("title", title).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "note_reminder_$noteId",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, noteId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("note_reminder_$noteId")
    }
}

class NoteReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val noteId = inputData.getLong("note_id", 0L)
        val title = inputData.getString("title").orEmpty().ifBlank { "Tnote reminder" }
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Capture reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("You asked Tnote to remind you about this capture.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(noteId.toInt(), notification)
        return Result.success()
    }

    companion object { private const val CHANNEL_ID = "capture_reminders" }
}
