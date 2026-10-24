package dev.pov.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import dev.pov.tv.data.GitHubUpdates
import java.util.concurrent.Executors

/** User-confirmed, signed GitHub release updates for sideloaded TVs. */
class UpdateActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var updates: GitHubUpdates
    private lateinit var status: TextView
    private lateinit var notes: TextView
    private lateinit var progress: ProgressBar
    private lateinit var action: Button
    private var release: GitHubUpdates.Release? = null
    private var ready = false
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updates = GitHubUpdates(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(60, 35, 60, 35); setBackgroundColor(Color.rgb(12, 19, 29))
        }
        fun label(value: String, size: Float) = TextView(this).apply { text = value; textSize = size; setTextColor(Color.WHITE) }
        content.addView(label("POV Rajpoot · App updates", 26f))
        status = label("Checking published GitHub releases…", 18f)
        content.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20; bottomMargin = 12 })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; isIndeterminate = true }
        content.addView(progress, LinearLayout.LayoutParams(-1, 16))
        notes = label("", 16f)
        val scroll = ScrollView(this).apply { addView(notes, ViewGroup.LayoutParams(-1, -2)) }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 16 })
        action = Button(this).apply { text = "Check again"; isEnabled = false; setOnClickListener {
            when { ready -> install(); release != null -> download(); else -> checkUpdates() }
        } }
        content.addView(action, LinearLayout.LayoutParams(-1, -2))
        content.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(-1, -2))
        setContentView(content)
        checkUpdates()
    }

    private fun checkUpdates() {
        if (busy) return
        ready = false; release = null; notes.text = ""; status.text = "Checking published GitHub releases…"
        work({ updates.latest() }) { latest ->
            release = latest
            if (latest == null) {
                status.text = "You’re up to date. No newer compatible release is available."
                notes.text = "Installed: ${packageManager.getPackageInfo(packageName, 0).versionName}\nUpdates come from ${GitHubUpdates.REPOSITORY}."
                action.text = "Check again"
            } else {
                status.text = "Update available: ${latest.version}"
                notes.text = latest.candidate.notes.ifBlank { "A new version of POV Rajpoot is available." }
                action.text = "Download and install"
            }
            action.requestFocus()
        }
    }

    private fun download() {
        val selected = release ?: return
        status.text = "Downloading ${selected.version}…"; progress.isIndeterminate = false; progress.progress = 0
        work({ updates.download(selected) { bytes, total -> runOnUiThread {
            if (!isDestroyed && !isFinishing) {
                progress.progress = (bytes * 100 / total).toInt().coerceIn(0, 100)
                status.text = "Downloading ${selected.version} · ${progress.progress}%"
            }
        } } }) {
            ready = true; action.text = "Install update"; status.text = "Download verified. Ready to install ${selected.version}."
            install()
        }
    }

    private fun install() {
        val selected = release ?: return
        if (busy) return
        if (!packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this).setTitle("Allow app updates")
                .setMessage("Allow POV Rajpoot to install apps on the next screen. Return here and select Install update. Android will ask you to confirm the installation.")
                .setPositiveButton("Open settings") { _, _ ->
                    try { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
                    catch (_: android.content.ActivityNotFoundException) { status.text = "Open Android Settings → Security → Install unknown apps and allow POV Rajpoot, then return here." }
                }.setNegativeButton("Later", null).show()
            return
        }
        status.text = "Verifying update before installation…"
        work({ updates.validate(updates.apkFile, selected) }) {
            val uri = FileProvider.getUriForFile(this, "$packageName.updates", updates.apkFile)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri("POV Rajpoot update", uri)
            try { startActivity(intent); status.text = "Confirm Install on Android’s installer. If you cancelled, select Install update to try again." }
            catch (_: android.content.ActivityNotFoundException) { status.text = "No APK installer is available on this device. Install the signed APK manually from GitHub Releases." }
        }
    }

    private fun <T> work(task: () -> T, done: (T) -> Unit) {
        busy = true; action.isEnabled = false; progress.visibility = android.view.View.VISIBLE
        if (!ready) progress.isIndeterminate = release == null
        worker.execute {
            try {
                val result = task()
                runOnUiThread {
                    if (!isDestroyed && !isFinishing) {
                        busy = false; action.isEnabled = true; progress.visibility = android.view.View.GONE; done(result)
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isDestroyed && !isFinishing) {
                        busy = false; action.isEnabled = true; progress.visibility = android.view.View.GONE
                        status.text = if (error is IllegalStateException) error.message else "Could not complete the update request. Check your connection and try again."
                        action.text = if (ready) "Install update" else if (release != null) "Retry download" else "Check again"
                        action.requestFocus()
                    }
                }
            }
        }
    }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}
