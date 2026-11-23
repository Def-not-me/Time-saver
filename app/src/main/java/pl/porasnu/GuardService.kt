package pl.porasnu

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.telecom.TelecomManager

/**
 * Usługa pierwszoplanowa: co 2 s sprawdza, czy trwa cisza nocna i czy telefon
 * jest odblokowany – jeśli tak, wyświetla BlockActivity.
 * Dzięki temu "Home"/"Ostatnie aplikacje" nie pozwalają uciec z blokady.
 */
class GuardService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            check(fresh = false)
            checkPip()
            handler.postDelayed(this, 2000)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_USER_PRESENT) check(fresh = true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Ochrona nocna", NotificationManager.IMPORTANCE_MIN)
        )
        registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        if (!Store.enabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Pora snu czuwa")
            .setContentText("Cisza nocna ${Store.fmt(Store.startMin(this))}–${Store.fmt(Store.endMin(this))}")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun check(fresh: Boolean) {
        if (!Store.shouldBlock(this)) return
        if (!getSystemService(PowerManager::class.java).isInteractive) return
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return

        // Nie przeszkadzamy w trakcie rozmowy / dzwonienia
        val mode = getSystemService(AudioManager::class.java).mode
        if (mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE
        ) return

        // Chwila po otwarciu dozwolonej aplikacji z ekranu blokady
        if (System.currentTimeMillis() < graceUntil) return

        // Dozwolona aplikacja na ekranie -> nie blokujemy
        val fg = foregroundApp()
        if (fg != null && (fg in Store.allowed(this) || fg in phoneApps())) return

        if (fresh) Store.add(this, Store.PROBA)
        if (BlockActivity.visible) return

        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    /** Domyślna aplikacja telefonu + ekran rozmowy (tylko gdy są na ekranie). */
    private fun phoneApps(): Set<String> {
        val s = mutableSetOf("com.android.incallui", "com.android.server.telecom")
        getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { s += it }
        return s
    }

    /** Ostatni stan każdej aktywności: (typ zdarzenia, czas). */
    private val activityState = HashMap<String, Pair<Int, Long>>()
    private val pipLogged = mutableSetOf<String>()

    /**
     * Pływające okno (PiP) innej aplikacji nad blokadą.
     * Pod pełnoekranową blokadą każda inna aktywność powinna być ZATRZYMANA.
     * Aktywność, która jest tylko WSTRZYMANA dłużej niż 3 s, jest nadal widoczna,
     * czyli najpewniej pływa w PiP.
     */
    private fun pipApp(): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val now = System.currentTimeMillis()
        val ignore = Store.allowed(this) + phoneApps() + packageName
        return activityState.entries.firstOrNull { (key, v) ->
            val pkg = key.substringBefore('/')
            v.first == UsageEvents.Event.ACTIVITY_PAUSED && now - v.second in 3000..12 * 3600_000L && pkg !in ignore
        }?.key?.substringBefore('/')
    }

    private fun checkPip() {
        if (!BlockActivity.visible) { BlockActivity.pipWarning = null; return }
        foregroundApp() // odśwież zdarzenia
        val pkg = pipApp()
        if (pkg == null) {
            BlockActivity.pipWarning = null
            pipLogged.clear()
            return
        }
        val name = Apps.label(this, pkg)
        BlockActivity.pipWarning = name
        if (pipLogged.add(pkg)) Store.add(this, Store.PIP_INNE, name)
    }

    private var lastFg: String? = null
    private var lastQuery = 0L

    /** Pakiet aplikacji aktualnie na pierwszym planie (wymaga dostępu do danych o użyciu). */
    private fun foregroundApp(): String? {
        val usm = getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val from = if (lastQuery == 0L) now - 6 * 3600_000L else lastQuery - 1000
        val events = usm.queryEvents(from, now) ?: return lastFg
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) lastFg = e.packageName
            if (Build.VERSION.SDK_INT >= 29) {
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED ->
                        activityState["${e.packageName}/${e.className}"] = e.eventType to e.timeStamp
                }
            }
        }
        lastQuery = now
        return lastFg
    }

    companion object {
        private const val CHANNEL = "guard"

        @Volatile
        var graceUntil = 0L

        fun start(c: Context) {
            c.startForegroundService(Intent(c, GuardService::class.java))
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, GuardService::class.java))
        }
    }
}
