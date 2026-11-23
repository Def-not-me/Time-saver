package pl.porasnu

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.label(s: CharSequence, size: Float = 16f, color: Int = Color.WHITE, bold: Boolean = false) =
    TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

fun Context.button(s: String, onClick: () -> Unit) = Button(this).apply {
    text = s
    isAllCaps = false
    setOnClickListener { onClick() }
}

fun <T : View> LinearLayout.add(v: T, topDp: Int = 0): T {
    addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = context.dp(topDp) })
    return v
}
