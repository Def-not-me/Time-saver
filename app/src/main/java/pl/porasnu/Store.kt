package pl.porasnu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Event(val ts: Long, val type: String, val reason: String, val minutes: Int)

/** Ustawienia + dziennik zdarzeń (SharedPreferences). */
object Store {
    const val PROBA = "Próba odblokowania"
    const val OBEJSCIE = "Obejście"
    const val TELEFON = "Telefon"
    const val WYLACZENIE = "Wyłączenie ochrony"
    const val ZMIANA = "Zmiana godzin"
    const val DODANIE = "Nowa dozwolona aplikacja"

    private fun p(c: Context) = c.getSharedPreferences("pora_snu", Context.MODE_PRIVATE)

    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()

    fun startMin(c: Context) = p(c).getInt("start", 22 * 60)
    fun endMin(c: Context) = p(c).getInt("end", 7 * 60)
    fun setHours(c: Context, start: Int, end: Int) =
        p(c).edit().putInt("start", start).putInt("end", end).apply()

    /** Aplikacje dozwolone w nocy (bez podawania powodu). */
    fun allowed(c: Context): Set<String> {
        val pr = p(c)
        if (!pr.contains("allowed")) pr.edit().putStringSet("allowed", Apps.defaults(c)).apply()
        return pr.getStringSet("allowed", emptySet())!!.toSet()
    }

    fun setAllowed(c: Context, s: Set<String>) =
        p(c).edit().putStringSet("allowed", HashSet(s)).apply()

    fun bypassUntil(c: Context) = p(c).getLong("bypass", 0L)
    fun setBypass(c: Context, minutes: Int) =
        p(c).edit().putLong("bypass", System.currentTimeMillis() + minutes * 60_000L).apply()

    fun isQuietNow(c: Context): Boolean {
        val cal = Calendar.getInstance()
        val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val s = startMin(c)
        val e = endMin(c)
        return when {
            s == e -> false
            s < e -> m in s until e
            else -> m >= s || m < e // przez północ, np. 22:00–07:00
        }
    }

    fun shouldBlock(c: Context) =
        enabled(c) && isQuietNow(c) && System.currentTimeMillis() >= bypassUntil(c)

    /** Początek bieżącej (albo ostatniej) nocy – do statystyk "tej nocy". */
    fun lastQuietStart(c: Context): Long {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startMin(c) / 60)
            set(Calendar.MINUTE, startMin(c) % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis > now) cal.add(Calendar.DAY_OF_MONTH, -1)
        return cal.timeInMillis
    }

    fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)

    @Synchronized
    fun add(c: Context, type: String, reason: String = "", minutes: Int = 0) {
        val arr = JSONArray(p(c).getString("log", "[]"))
        arr.put(
            JSONObject()
                .put("ts", System.currentTimeMillis())
                .put("type", type)
                .put("reason", reason)
                .put("min", minutes)
        )
        val out = if (arr.length() > 1000) {
            JSONArray().also { n -> for (i in arr.length() - 1000 until arr.length()) n.put(arr.get(i)) }
        } else arr
        p(c).edit().putString("log", out.toString()).apply()
    }

    fun events(c: Context): List<Event> {
        val arr = JSONArray(p(c).getString("log", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Event(o.getLong("ts"), o.getString("type"), o.optString("reason"), o.optInt("min"))
        }
    }

    fun count(c: Context, type: String, since: Long = 0L) =
        events(c).count { it.type == type && it.ts >= since }

    /** null = powód OK, w przeciwnym razie komunikat błędu. */
    fun validateReason(r: String): String? {
        val t = r.trim()
        if (t.length < 15) return "Powód musi mieć co najmniej 15 znaków."
        if (t.split(Regex("\\s+")).size < 3) return "Napisz pełne zdanie (min. 3 słowa)."
        return null
    }
}
