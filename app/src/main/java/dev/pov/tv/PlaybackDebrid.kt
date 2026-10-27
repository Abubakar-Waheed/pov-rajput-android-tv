package dev.pov.tv

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import dev.pov.tv.data.DebridServices

/** Explicit resolution provenance, never guessed from the URL, account priority or filename. */
internal object PlaybackDebrid {
    fun normalize(service: String?): String? = service?.takeIf { it in DebridServices.IDS }
    private fun logo(id: String) = when (id) {
        "offcloud" -> R.drawable.pov_offcloud_logo
        "torbox" -> R.drawable.pov_torbox_logo
        "realdebrid" -> R.drawable.pov_realdebrid_logo
        "alldebrid" -> R.drawable.pov_alldebrid_logo
        "premiumize" -> R.drawable.pov_premiumize_logo
        else -> R.drawable.pov_debridlink_logo
    }
    /** AllDebrid's mark is a wordmark that already spells the name. */
    private fun wordmark(id: String?) = id == "alldebrid"

    fun bind(root: View, service: String?) {
        val id = normalize(service)
        root.findViewById<View>(R.id.pov_debrid_badge).visibility = if (id == null) View.GONE else View.VISIBLE
        val label = id?.let(DebridServices::name).orEmpty()
        root.findViewById<TextView>(R.id.pov_debrid_name).apply {
            text = label; visibility = if (wordmark(id)) View.GONE else View.VISIBLE
        }
        root.findViewById<ImageView>(R.id.pov_debrid_logo).apply {
            val density = resources.displayMetrics.density
            layoutParams = layoutParams.apply { width = ((if (wordmark(id)) 72 else 20) * density).toInt() }
            setImageResource(if (id == null) 0 else logo(id))
            contentDescription = if (id == null) null else "Streaming with $label"
        }
    }
}
