package dev.pov.tv

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.TextView
import dev.pov.tv.data.SecureStore
import dev.pov.tv.data.WeTrakrApi
import java.util.concurrent.atomic.AtomicReference

/** Explicit local linking helper. Only the public client ID and short-lived user code are handled here.
 * No client secret, bearer/refresh token, account history, or private title is printed. */
class WeTrakrLiveLinkInstrumentation : Instrumentation() {
    private var clientId = ""
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); clientId = arguments?.getString("client_id").orEmpty(); start() }
    private fun <T> onMain(task: () -> T): T {
        val result = AtomicReference<Result<T>>()
        runOnMainSync { result.set(runCatching(task)) }; return result.get().getOrThrow()
    }
    override fun onStart() {
        val report = Bundle(); var activity: MainActivity? = null; var passed = false; var stage = "setup"
        try {
            val api = WeTrakrApi(SecureStore(targetContext))
            if (clientId.isNotBlank()) { require(WeTrakrApi.validClientId(clientId)); api.setClientId(clientId) }
            require(api.configured())
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val main = activity
            if (!api.connected()) {
                stage = "device_code"
                onMain { MainActivity::class.java.getDeclaredMethod("startWeTrakr").apply { isAccessible = true }.invoke(main) }
                val started = SystemClock.elapsedRealtime()
                val deadline = started + 600000
                var announced = false
                while (SystemClock.elapsedRealtime() < deadline && !api.connected()) {
                    if (!announced) {
                        val code = onMain {
                            val dialog = MainActivity::class.java.getDeclaredField("authorization").apply { isAccessible = true }.get(main) as? DeviceAuthorizationDialog
                            if (dialog == null) "" else (DeviceAuthorizationDialog::class.java.getDeclaredField("code").apply { isAccessible = true }.get(dialog) as TextView).text.toString()
                        }
                        if (code.isNotBlank()) {
                            sendStatus(1, Bundle().apply { putString("activation_code", code); putString("activation_url", "https://wetrakr.com/activate") })
                            announced = true; stage = "waiting_for_user"
                        }
                    }
                    if (!announced && SystemClock.elapsedRealtime() - started > 45000) error("No activation code received")
                    SystemClock.sleep(500)
                }
                check(api.connected())
            }
            stage = "read_only_account_check"
            stage = "movie_watchlist"; api.watchlist("movie")
            stage = "tv_watchlist"; api.watchlist("series")
            stage = "continue_watching"; api.continueWatching()
            stage = "up_next"; api.nextEpisodes()
            stage = "personal_lists"; api.lists()
            stage = "liked_lists"; val liked = api.likedLists()
            liked.take(1).forEach { (id, _) -> stage = "liked_list_items"; api.likedListPreview(id) }
            report.putBoolean("liked_lists_verified", true)
            report.putBoolean("read_only_library_endpoints_verified", true)
            report.putBoolean("connected", api.connected())
            passed = true
        } catch (error: Throwable) { report.putString("stage", stage); report.putString("error_type", error.javaClass.simpleName); if (error is dev.pov.tv.data.ApiException) report.putInt("http_status", error.code) }
        finally {
            onMain { activity?.finish() }
            report.putBoolean("passed", passed); finish(if (passed) -1 else 0, report)
        }
    }
}
