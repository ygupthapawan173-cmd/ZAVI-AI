package ai.zavi.assistant.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ZaviReminder(
    val id: String,
    val title: String,
    val delayMinutes: Long,
    val daily: Boolean,
    val enabled: Boolean
)

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val title = inputData.getString("title") ?: return Result.failure()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "zavi_reminders"
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "ZAVI reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        if (android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("ZAVI AI Reminder")
                .setContentText(title)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            manager.notify(title.hashCode(), notification)
        }
        return Result.success()
    }
}

object ReminderManager {
    private const val PREFS = "zavi_reminders"
    private const val KEY = "items"

    fun list(context: Context): List<ZaviReminder> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                ZaviReminder(item.getString("id"), item.getString("title"), item.getLong("delay"),
                    item.getBoolean("daily"), item.getBoolean("enabled"))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun save(context: Context, items: List<ZaviReminder>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("title", item.title)
                .put("delay", item.delayMinutes).put("daily", item.daily).put("enabled", item.enabled))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    private fun enqueue(context: Context, item: ZaviReminder) {
        val data = workDataOf("title" to item.title)
        val work = WorkManager.getInstance(context)
        if (item.daily) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(item.delayMinutes.coerceAtLeast(15), TimeUnit.MINUTES)
                .setInputData(data).build()
            work.enqueueUniquePeriodicWork(item.id, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(item.delayMinutes.coerceAtLeast(1), TimeUnit.MINUTES)
                .setInputData(data).build()
            work.enqueueUniqueWork(item.id, ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun add(context: Context, title: String, delayMinutes: Long, daily: Boolean) {
        val item = ZaviReminder(UUID.randomUUID().toString(), title.trim(), delayMinutes.coerceAtLeast(1), daily, true)
        save(context, list(context) + item)
        enqueue(context, item)
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        val updated = list(context).map { if (it.id == id) it.copy(enabled = enabled) else it }
        save(context, updated)
        val item = updated.firstOrNull { it.id == id } ?: return
        val work = WorkManager.getInstance(context)
        if (enabled) enqueue(context, item)
        else {
            work.cancelUniqueWork(id)
        }
    }

    fun delete(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(id)
        save(context, list(context).filterNot { it.id == id })
    }
}
