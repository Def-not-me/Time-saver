package pl.porasnu

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pełnoekranowy komunikat „To nie jest pora korzystania z telefonu”. */
class BlockActivity : Activity() {

    companion object {
        @Volatile
        var visible = false
        private const val WAIT_SECONDS = 10 // "chwila namysłu" przed odblokowaniem
    }

    private val handler = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private lateinit var clock: TextView
    private lateinit var stats: TextView
    private lateinit var askBtn: Button
    private lateinit var form: LinearLayout
    private lateinit var reason: EditText
    private lateinit var error: TextView
    private lateinit var unlockBtn: Button
    private val durationBtns = mutableListOf<Pair<Int, Button>>()

    private var minutes = 15
    private var waitLeft = 0

    private val tick = object : Runnable {
        override fun run() {
            if (!Store.shouldBlock(this@BlockActivity)) { finish(); return }
            if (checkFullscreen()) return
            clock.text = timeFmt.format(Date())
            if (waitLeft > 0) waitLeft--
            updateUnlock()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        visible = true
        refreshStats()
        handler.post(tick)
    }

    override fun onResume() {
        super.onResume()
        checkFullscreen()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        checkFullscreen()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        checkFullscreen()
    }

    private var escapeLogged = false

    /**
     * Wykrywa obejście przez okno pływające, PiP albo podzielony ekran.
     * Zwraca true, jeśli ekran blokady został zamknięty – usługa w ciągu ~2 s
     * otworzy go ponownie, tym razem na pełnym ekranie.
     */
    private fun checkFullscreen(): Boolean {
        var shrunk = isInMultiWindowMode || isInPictureInPictureMode
        if (!shrunk && Build.VERSION.SDK_INT >= 30) {
            val cur = windowManager.currentWindowMetrics.bounds
            val max = windowManager.maximumWindowMetrics.bounds
            shrunk = cur.width() < max.width() * 0.9 || cur.height() < max.height() * 0.9
        }
        if (!shrunk) return false
        if (!escapeLogged) {
            escapeLogged = true
            Store.add(this, Store.OKNO)
        }
        visible = false
        handler.removeCallbacks(tick)
        finishAndRemoveTask()
        return true
    }

    override fun onStop() {
        visible = false
        handler.removeCallbacks(tick)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Deprecated("Blokujemy przycisk Wstecz")
    override fun onBackPressed() { /* celowo nic */ }

    // ---------- UI ----------

    private fun buildUi(): View {
        val gray = Color.parseColor("#9AA4C0")
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(56), dp(28), dp(40))
        }

        col.add(label("🌙", 64f).center())
        col.add(label("To nie jest pora\nkorzystania z telefonu", 28f, bold = true).center(), 12)
        clock = col.add(label(timeFmt.format(Date()), 56f).center(), 16)
        col.add(
            label("Cisza nocna trwa do ${Store.fmt(Store.endMin(this))}.\nOdłóż telefon i odpocznij.", 17f, gray).center(),
            8
        )
        stats = col.add(label("", 14f, gray).center(), 24)

        // Dozwolone aplikacje – bez podawania powodu
        val allowed = Store.allowed(this).filter { Apps.launchIntent(this, it) != null }
            .sortedBy { Apps.label(this, it).lowercase() }
        if (allowed.isNotEmpty()) {
            col.add(label("Możesz bez powodu otworzyć:", 14f, gray).center(), 32)
            for (pkg in allowed) {
                col.add(button(Apps.label(this, pkg)) { openAllowed(pkg) }, 4)
            }
        }

        askBtn = col.add(button("Muszę skorzystać…") { showForm() }, 40)

        form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        form.add(label("Dlaczego musisz teraz użyć telefonu?", 16f, bold = true))
        reason = form.add(EditText(this).apply {
            hint = "np. Muszę pilnie odpisać na wiadomość od mamy"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#667090"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
        }, 8)
        form.add(label("Na jak długo?", 14f, gray), 12)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (m in listOf(5, 15, 30)) {
            val b = button("$m min") { minutes = m; updateUnlock() }
            row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            durationBtns += m to b
        }
        form.add(row, 4)
        error = form.add(label("", 14f, Color.parseColor("#FF7A7A")), 8)
        unlockBtn = form.add(button("") { tryUnlock() }, 8)
        col.add(form, 24)

        col.add(button("📞 Muszę zadzwonić") { call() }, 32)

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1020"))
            isFillViewport = true
            addView(col)
        }
    }

    private fun TextView.center() = apply { gravity = Gravity.CENTER }

    private fun showForm() {
        askBtn.visibility = View.GONE
        form.visibility = View.VISIBLE
        waitLeft = WAIT_SECONDS
        updateUnlock()
        reason.requestFocus()
    }

    private fun updateUnlock() {
        if (!::unlockBtn.isInitialized) return
        durationBtns.forEach { (m, b) -> b.alpha = if (m == minutes) 1f else 0.45f }
        unlockBtn.isEnabled = waitLeft == 0
        unlockBtn.text = if (waitLeft > 0) "Odblokuj za $waitLeft s" else "Odblokuj na $minutes min"
    }

    private fun refreshStats() {
        val night = Store.lastQuietStart(this)
        val week = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        stats.text = "Tej nocy: ${Store.count(this, Store.PROBA, night)} prób odblokowania, " +
            "${Store.count(this, Store.OBEJSCIE, night)} obejść\n" +
            "Ostatnie 7 dni: ${Store.count(this, Store.OBEJSCIE, week)} obejść"
    }

    private fun tryUnlock() {
        val text = reason.text.toString()
        val err = Store.validateReason(text)
        if (err != null) { error.text = err; return }
        Store.add(this, Store.OBEJSCIE, text.trim(), minutes)
        Store.setBypass(this, minutes)
        finish()
    }

    private fun openAllowed(pkg: String) {
        val i = Apps.launchIntent(this, pkg) ?: return
        GuardService.graceUntil = System.currentTimeMillis() + 5000
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun call() {
        // Telefon jest dozwolony tylko na czas korzystania z niego –
        // wyjście do ekranu głównego lub innej aplikacji = blokada wraca.
        Store.add(this, Store.TELEFON)
        GuardService.graceUntil = System.currentTimeMillis() + 5000
        startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
    }
}
