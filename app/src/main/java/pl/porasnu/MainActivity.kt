package pl.porasnu

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ustawienia, uprawnienia, statystyki i historia. */
class MainActivity : Activity() {

    private val gray = Color.parseColor("#9AA4C0")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        if (Store.enabled(this)) GuardService.start(this)
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        root.add(label("🌙 Pora snu", 26f, bold = true))
        root.add(label("Wyświetla pełnoekranową blokadę, gdy odblokujesz telefon w nocy.", 14f, gray), 4)

        // --- Włącznik ---
        val sw = root.add(Switch(this).apply {
            text = "Ochrona włączona"
            textSize = 18f
            isChecked = Store.enabled(this@MainActivity)
        }, 24)
        sw.setOnClickListener {
            if (sw.isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    sw.isChecked = false
                    toast("Najpierw nadaj uprawnienie „Wyświetlanie nad innymi aplikacjami”.")
                    return@setOnClickListener
                }
                Store.setEnabled(this, true)
                GuardService.start(this)
                render()
            } else {
                sw.isChecked = true // wyłączenie dopiero po podaniu powodu
                askReason("Wyłączyć ochronę?") { r ->
                    Store.add(this, Store.WYLACZENIE, r)
                    Store.setEnabled(this, false)
                    GuardService.stop(this)
                    render()
                }
            }
        }

        // --- Godziny ---
        val s = Store.startMin(this)
        val e = Store.endMin(this)
        root.add(button("Cisza nocna: ${Store.fmt(s)} – ${Store.fmt(e)}  (zmień)") { pickHours() }, 12)

        // --- Uprawnienia ---
        root.add(label("Uprawnienia", 18f, bold = true), 28)
        val overlayOk = Settings.canDrawOverlays(this)
        root.add(button((if (overlayOk) "✅" else "❌") + " Wyświetlanie nad innymi aplikacjami") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }, 4)
        val usageOk = Apps.hasUsageAccess(this)
        root.add(button((if (usageOk) "✅" else "❌") + " Dostęp do danych o użyciu (dozwolone aplikacje)") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }, 4)
        val batteryOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        root.add(button((if (batteryOk) "✅" else "⚠️") + " Bez optymalizacji baterii") {
            @Suppress("BatteryLife")
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }, 4)

        // --- Dozwolone aplikacje ---
        root.add(label("Dozwolone w nocy (bez powodu)", 18f, bold = true), 28)
        val allowed = Store.allowed(this).sortedBy { Apps.label(this, it).lowercase() }
        if (allowed.isEmpty()) root.add(label("Brak – wszystko jest blokowane.", 14f, gray), 6)
        for (pkg in allowed) {
            root.add(button("✕  ${Apps.label(this, pkg)}") {
                Store.setAllowed(this, Store.allowed(this) - pkg)
                render()
            }, 4)
        }
        root.add(button("+ Dodaj aplikację") { pickAllowed() }, 8)
        if (!usageOk) {
            root.add(label("Bez „dostępu do danych o użyciu” blokada zakryje też dozwolone aplikacje.", 13f, gray), 4)
        }

        // --- Statystyki ---
        val week = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        root.add(label("Statystyki (7 dni)", 18f, bold = true), 28)
        root.add(
            label(
                "Próby odblokowania: ${Store.count(this, Store.PROBA, week)}\n" +
                    "Obejścia blokady: ${Store.count(this, Store.OBEJSCIE, week)}\n" +
                    "Telefony: ${Store.count(this, Store.TELEFON, week)}\n" +
                    "Wyłączenia ochrony: ${Store.count(this, Store.WYLACZENIE, week)}\n" +
                    "Obejścia łącznie: ${Store.count(this, Store.OBEJSCIE)}",
                15f
            ), 6
        )

        // --- Historia (bez samych prób, żeby nie zaśmiecać) ---
        root.add(label("Historia", 18f, bold = true), 28)
        val fmt = SimpleDateFormat("EEE dd.MM HH:mm", Locale("pl", "PL"))
        val list = Store.events(this).filter { it.type != Store.PROBA }.reversed().take(60)
        if (list.isEmpty()) root.add(label("Brak wpisów.", 14f, gray), 6)
        for (ev in list) {
            val dur = if (ev.minutes > 0) " (${ev.minutes} min)" else ""
            val why = if (ev.reason.isNotBlank()) "\n„${ev.reason}”" else ""
            root.add(label("${fmt.format(Date(ev.ts))} • ${ev.type}$dur$why", 14f), 10)
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun pickHours() {
        val oldS = Store.startMin(this)
        val oldE = Store.endMin(this)
        TimePickerDialog(this, { _, h1, m1 ->
            TimePickerDialog(this, { _, h2, m2 ->
                val ns = h1 * 60 + m1
                val ne = h2 * 60 + m2
                val commit = {
                    Store.setHours(this, ns, ne)
                    if (Store.enabled(this)) GuardService.start(this) // odśwież powiadomienie
                    render()
                }
                if (Store.enabled(this)) {
                    askReason("Zmienić godziny ciszy?") { r ->
                        Store.add(this, Store.ZMIANA, "${Store.fmt(oldS)}–${Store.fmt(oldE)} → ${Store.fmt(ns)}–${Store.fmt(ne)}: $r")
                        commit()
                    }
                } else commit()
            }, oldE / 60, oldE % 60, true).apply { setTitle("Koniec ciszy") }.show()
        }, oldS / 60, oldS % 60, true).apply { setTitle("Początek ciszy") }.show()
    }

    private fun pickAllowed() {
        val current = Store.allowed(this)
        val apps = Apps.launchable(this).filter { it.first !in current }
        AlertDialog.Builder(this)
            .setTitle("Dodaj dozwoloną aplikację")
            .setItems(apps.map { it.second }.toTypedArray()) { _, which ->
                val (pkg, name) = apps[which]
                val commit = {
                    Store.setAllowed(this, Store.allowed(this) + pkg)
                    render()
                }
                // Dodanie wyjątku przy włączonej ochronie = też podajesz powód
                if (Store.enabled(this)) {
                    askReason("Dodać „$name”?") { r ->
                        Store.add(this, Store.DODANIE, "$name: $r")
                        commit()
                    }
                } else commit()
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun askReason(title: String, onOk: (String) -> Unit) {
        val et = EditText(this).apply { hint = "Powód (min. 15 znaków, pełne zdanie)"; minLines = 2 }
        val box = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(et) }
        val d = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Powód zostanie zapisany w historii.")
            .setView(box)
            .setPositiveButton("Potwierdź", null)
            .setNegativeButton("Anuluj", null)
            .create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val err = Store.validateReason(et.text.toString())
                if (err != null) et.error = err else { d.dismiss(); onOk(et.text.toString().trim()) }
            }
        }
        d.show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
