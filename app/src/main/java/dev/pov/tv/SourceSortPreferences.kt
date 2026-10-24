package dev.pov.tv

import android.app.AlertDialog
import android.content.Context
import dev.pov.tv.data.SourceSortOrder

internal class SourceSortPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("pov_sources", Context.MODE_PRIVATE)
    var order: SourceSortOrder
        get() = SourceSortOrder(prefs.getBoolean("quality_descending", true), prefs.getBoolean("size_ascending", true))
        set(value) { prefs.edit().putBoolean("quality_descending", value.qualityDescending).putBoolean("size_ascending", value.sizeAscending).apply() }
    val label: String get() = "${if (order.qualityDescending) "Highest quality" else "Lowest quality"} · ${if (order.sizeAscending) "Smallest first" else "Largest first"}"

    companion object {
        fun show(context: Context, changed: () -> Unit) {
            val preferences = SourceSortPreferences(context)
            AlertDialog.Builder(context).setTitle("Source sorting")
                .setItems(arrayOf("Quality · ${if (preferences.order.qualityDescending) "Highest first" else "Lowest first"}",
                    "File size within each quality · ${if (preferences.order.sizeAscending) "Smallest first" else "Largest first"}")) { _, choice ->
                    val quality = choice == 0
                    val selected = if (quality) !preferences.order.qualityDescending else !preferences.order.sizeAscending
                    AlertDialog.Builder(context).setTitle(if (quality) "Quality order" else "File size order")
                        .setSingleChoiceItems(if (quality) arrayOf("Highest first · 4K, 1080p, 720p…", "Lowest first · 720p, 1080p, 4K…")
                            else arrayOf("Smallest first", "Largest first"), if (selected) 1 else 0) { dialog, index ->
                            val current = preferences.order
                            preferences.order = if (quality) current.copy(qualityDescending = index == 0) else current.copy(sizeAscending = index == 0)
                            dialog.dismiss(); changed()
                        }.setNegativeButton("Cancel", null).show()
                }.setNegativeButton("Close", null).show()
        }
    }
}
