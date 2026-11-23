package pl.porasnu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Po restarcie telefonu / aktualizacji aplikacji wznawia ochronę. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Store.enabled(c)) GuardService.start(c)
    }
}
