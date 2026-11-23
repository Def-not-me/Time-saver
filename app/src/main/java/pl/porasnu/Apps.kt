package pl.porasnu

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.AlarmClock

/** Pomocnicze funkcje do listy aplikacji dozwolonych w nocy. */
object Apps {

    /** Znane pakiety, które domyślnie są dozwolone (jeśli są zainstalowane). */
    private val KNOWN_DEFAULTS = listOf(
        "com.google.android.deskclock",       // Zegar Google
        "com.sec.android.app.clockpackage",   // Zegar Samsung
        "com.android.deskclock",              // Zegar Xiaomi / AOSP
        "com.google.android.calendar",        // Kalendarz Google
        "com.anthropic.claude",               // Claude
    )

    /** Wszystkie aplikacje z ikoną w launcherze: (pakiet, nazwa). */
    fun launchable(c: Context): List<Pair<String, String>> {
        val pm = c.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != c.packageName }
            .sortedBy { it.second.lowercase() }
    }

    fun label(c: Context, pkg: String): String = try {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    fun launchIntent(c: Context, pkg: String): Intent? =
        c.packageManager.getLaunchIntentForPackage(pkg)

    fun defaults(c: Context): Set<String> {
        val s = mutableSetOf<String>()
        // Domyślna aplikacja budzika w tym telefonie
        c.packageManager.resolveActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)
            ?.activityInfo?.packageName
            ?.takeIf { it != "android" }
            ?.let { s += it }
        KNOWN_DEFAULTS.filter { launchIntent(c, it) != null }.forEach { s += it }
        // USOS (np. USOS PWr / USOSmobile) – szukamy po nazwie
        launchable(c).filter { it.second.contains("USOS", ignoreCase = true) }.forEach { s += it.first }
        return s
    }

    /** Czy nadano "Dostęp do danych o użyciu" (potrzebne, by wiedzieć, jaka aplikacja jest na ekranie). */
    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
