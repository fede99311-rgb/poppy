package it.venturini.poppy

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Calendar

object Reminders {
    private const val CH = "reminders"
    private const val ACTION = "it.venturini.poppy.REMINDER"
    private const val HOUR = 3_600_000L

    private fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH, ctx.getString(R.string.rem_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun pi(ctx: Context, kind: String, code: Int): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION).putExtra("kind", kind)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Promemoria "è ora del prossimo pasto": X ore dopo l'ultimo pasto noto. */
    fun scheduleFeed(ctx: Context, lastFeed: Long?, hours: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pi(ctx, "feed", 1)
        if (hours <= 0 || lastFeed == null) {
            am.cancel(p)
            return
        }
        val at = lastFeed + hours * HOUR
        if (at <= nowMs() + 30_000L) {
            am.cancel(p)
            return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
    }

    /** Promemoria giornaliero vitamina D (solo se non ancora data oggi). */
    fun scheduleVitD(ctx: Context, on: Boolean, hour: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pi(ctx, "vitd", 2)
        if (!on) {
            am.cancel(p)
            return
        }
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= nowMs() + 30_000L) c.add(Calendar.DAY_OF_YEAR, 1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, p)
    }

    private fun notify(ctx: Context, id: Int, title: Int, text: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        channel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(title))
            .setContentText(ctx.getString(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    fun handle(ctx: Context, kind: String, done: () -> Unit) {
        val p = ctx.getSharedPreferences("poppy", Context.MODE_PRIVATE)
        val babyId = p.getString("babyId", null)
        if (FirebaseAuth.getInstance().currentUser == null || babyId == null) {
            done()
            return
        }
        val since = nowMs() - 48 * HOUR
        FirebaseFirestore.getInstance().collection("babies").document(babyId).collection("entries")
            .whereGreaterThanOrEqualTo("t", since)
            .orderBy("t", Query.Direction.DESCENDING)
            .limit(80)
            .get()
            .addOnCompleteListener { task ->
                try {
                    val snap = task.result
                    val list = if (task.isSuccessful && snap != null)
                        snap.documents.map { parseEntry(it.id, it.data ?: emptyMap()) } else emptyList()
                    if (kind == "feed") {
                        val hours = p.getInt("rem_feed_h", 0)
                        val last = list.filter { it.type == "seno" || it.type == "biberon" }.maxOfOrNull { it.t }
                        if (hours > 0) {
                            if (last != null && last + hours * HOUR > nowMs() + 60_000L) {
                                scheduleFeed(ctx, last, hours)      // nel frattempo ha mangiato (es. dall'altro telefono)
                            } else {
                                notify(ctx, 1, R.string.rem_feed_title, R.string.rem_feed_text)
                            }
                        }
                    } else {
                        if (p.getBoolean("rem_vitd", false)) {
                            val sod = startOfDay(nowMs())
                            val given = list.any { it.type == "farmaco" && it.name == VITD && it.t >= sod }
                            if (!given) notify(ctx, 2, R.string.rem_vitd_title, R.string.rem_vitd_text)
                            scheduleVitD(ctx, true, p.getInt("vitd_hour", 9))
                        }
                    }
                } finally {
                    done()
                }
            }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val kind = intent.getStringExtra("kind") ?: "feed"
        Reminders.handle(context.applicationContext, kind) { pending.finish() }
    }
}

/** Widget: ultimo pasto, lato consigliato e due pulsanti per avviare la poppata. */
class PoppyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, build(context))
    }

    companion object {
        private fun startIntent(ctx: Context, side: String?, code: Int): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java)
            if (side != null) i.putExtra("start_side", side)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun build(ctx: Context): RemoteViews {
            val p = ctx.getSharedPreferences("poppy", Context.MODE_PRIVATE)
            val rv = RemoteViews(ctx.packageName, R.layout.widget_poppy)
            rv.setTextViewText(R.id.w_name, p.getString("babyName", ctx.getString(R.string.app_name)))
            val last = p.getLong("lastFeed", 0L)
            rv.setTextViewText(
                R.id.w_last,
                if (last > 0) ctx.getString(R.string.w_last, timeStr(last)) else ctx.getString(R.string.dash)
            )
            val next = p.getString("nextSide", "sx")
            val nextName = ctx.getString(if (next == "sx") R.string.side_left else R.string.side_right)
            rv.setTextViewText(R.id.w_next, ctx.getString(R.string.next_side, nextName))
            rv.setTextViewText(R.id.w_sx, ctx.getString(R.string.side_left))
            rv.setTextViewText(R.id.w_dx, ctx.getString(R.string.side_right))
            rv.setOnClickPendingIntent(R.id.w_sx, startIntent(ctx, "sx", 11))
            rv.setOnClickPendingIntent(R.id.w_dx, startIntent(ctx, "dx", 12))
            rv.setOnClickPendingIntent(R.id.w_root, startIntent(ctx, null, 13))
            return rv
        }

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PoppyWidget::class.java))
            for (id in ids) mgr.updateAppWidget(id, build(ctx))
        }
    }
}
