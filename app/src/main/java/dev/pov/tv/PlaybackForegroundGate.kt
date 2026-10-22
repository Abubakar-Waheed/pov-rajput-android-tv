package dev.pov.tv

/** Coalesces Activity transitions so fullscreen/browse handoffs never count as leaving the app. */
internal class PlaybackForegroundGate(
    private val schedule: (Runnable) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val onBackground: () -> Unit,
    private val onForeground: () -> Unit,
) {
    private val hosts = mutableSetOf<Any>()
    private var background = false
    private val check = Runnable {
        if (hosts.isEmpty() && !background) {
            background = true
            onBackground()
        }
    }

    fun started(host: Any) {
        hosts += host
        cancel(check)
        if (background) {
            background = false
            onForeground()
        }
    }

    fun stopped(host: Any) {
        if (!hosts.remove(host)) return
        if (hosts.isEmpty()) {
            cancel(check)
            schedule(check)
        }
    }
}

