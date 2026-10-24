package dev.pov.tv

import android.view.View
import android.widget.ImageView
import android.widget.TextView

/** Explicit resolution provenance, never guessed from the URL, account priority or filename. */
internal object PlaybackDebrid {
    fun normalize(service: String?): String? = service?.takeIf { it == "offcloud" || it == "torbox" }
    fun bind(root: View, service: String?) {
        val id = normalize(service)
        root.findViewById<View>(R.id.pov_debrid_badge).visibility = if (id == null) View.GONE else View.VISIBLE
        val label = if (id == "offcloud") "Offcloud" else "TorBox"
        root.findViewById<TextView>(R.id.pov_debrid_name).text = if (id == null) "" else label
        root.findViewById<ImageView>(R.id.pov_debrid_logo).apply {
            setImageResource(when (id) { "offcloud" -> R.drawable.pov_offcloud_logo; "torbox" -> R.drawable.pov_torbox_logo; else -> 0 })
            contentDescription = if (id == null) null else "Streaming with $label"
        }
    }
}
