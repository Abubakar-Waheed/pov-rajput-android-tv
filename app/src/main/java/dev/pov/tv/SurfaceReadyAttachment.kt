package dev.pov.tv

/**
 * Waits for a usable incoming video target without detaching the outgoing target early.
 * All calls and observer callbacks belong to the UI thread. Session identity plus a request
 * generation fence stale callbacks, including a callback already queued when its observer closes.
 */
internal class SurfaceReadyAttachment<Session : Any, Target : Any>(
    private val currentSession: () -> Session?,
    private val isReady: (Target) -> Boolean,
    private val observeReadiness: (Target, () -> Unit) -> (() -> Unit),
    private val attach: (Session, Target) -> Unit,
) {
    private class Pending<S : Any, T : Any>(val session: S, val target: T, val generation: Long) {
        var closeObserver: (() -> Unit)? = null
    }
    private var generation = 0L
    private var pending: Pending<Session, Target>? = null

    fun request(session: Session, target: Target) {
        if (currentSession() !== session) return
        val existing = pending
        if (existing?.session === session && existing.target === target) {
            attempt(existing)
            return
        }
        cancelAll()
        if (isReady(target)) {
            attach(session, target)
            return
        }
        val request = Pending(session, target, generation)
        pending = request
        val close = observeReadiness(target) { attempt(request) }
        // A readiness source may call back synchronously during registration.
        if (pending !== request) close() else request.closeObserver = close
        attempt(request)
    }

    fun cancel(target: Target) {
        if (pending?.target === target) cancelAll()
    }

    fun cancelAll() {
        generation++
        val previous = pending
        pending = null
        previous?.closeObserver?.invoke()
        previous?.closeObserver = null
    }

    private fun attempt(request: Pending<Session, Target>) {
        if (pending !== request || generation != request.generation) return
        if (currentSession() !== request.session) { cancelAll(); return }
        if (!isReady(request.target)) return
        pending = null
        request.closeObserver?.invoke()
        request.closeObserver = null
        attach(request.session, request.target)
    }
}
