package pl.porasnu

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process

/** Pomocnicze funkcje do listy aplikacji dozwolonych w nocy. */
object Apps {

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

    /**
     * Domyślnie lista jest pusta – telefon jest dozwolony zawsze (GuardService.phoneApps),
     * a resztę użytkownik dodaje sam w ustawieniach.
     */
    @Suppress("UNUSED_PARAMETER")
    fun defaults(c: Context): Set<String> = emptySet()

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
