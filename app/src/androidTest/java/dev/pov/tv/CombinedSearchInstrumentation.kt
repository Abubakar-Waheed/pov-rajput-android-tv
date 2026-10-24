package dev.pov.tv

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.pov.tv.data.CatalogApi
import java.util.concurrent.atomic.AtomicReference

/** Public catalog and TV filter audit; does not change account preferences. */
class CombinedSearchInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun <T> onMain(task: () -> T): T {
        val result = AtomicReference<Result<T>>()
        runOnMainSync { result.set(runCatching(task)) }
        return result.get().getOrThrow()
    }
    private fun views(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) for (index in 0 until root.childCount) addAll(views(root.getChildAt(index)))
    }
    override fun onStart() {
        val report = Bundle()
        var activity: MainActivity? = null
        var passed = false
        try {
            val result = CatalogApi().searchAll("Batman")
            check(result.unavailableTypes.isEmpty())
            check(result.items.any { it.type == "movie" } && result.items.any { it.type == "series" })
            report.putBoolean("public_query_returns_movies_and_shows", true)
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val main = activity
            val theme = MainActivity::class.java.getDeclaredField("theme").apply { isAccessible = true }
            val preview = MainActivity::class.java.getDeclaredField("publicPreview").apply { isAccessible = true }
            val shell = MainActivity::class.java.getDeclaredMethod("shell").apply { isAccessible = true }
            val search = MainActivity::class.java.getDeclaredMethod("searchResults", String::class.java, String::class.java, CatalogApi.SearchResults::class.java).apply { isAccessible = true }
            val originalTheme = onMain { theme.get(main) }
            val originalPreview = onMain { preview.getBoolean(main) }
            try {
                for (spec in ThemeSpec.entries) {
                    onMain {
                        preview.setBoolean(main, true); theme.set(main, spec); shell.invoke(main)
                        search.invoke(main, "Batman", "all", result)
                    }
                    waitForIdleSync()
                    fun captions() = onMain { views(main.window.decorView).filterIsInstance<TextView>().map { it.text.toString() } }
                    check(captions().any { it.startsWith("Movie ·") })
                    check(captions().any { it.startsWith("TV show ·") })
                    for ((label, prefix, excluded) in listOf(Triple("Movies", "Movie ·", "TV show ·"), Triple("TV shows", "TV show ·", "Movie ·"), Triple("All", "Movie ·", ""))) {
                        onMain { views(main.window.decorView).filterIsInstance<TextView>().first { it.tag?.toString()?.startsWith("action:") == true && it.text.toString().removePrefix("✓ ") == label }.performClick() }
                        waitForIdleSync()
                        val texts = captions()
                        check(texts.any { it.startsWith(prefix) })
                        if (excluded.isNotEmpty()) check(texts.none { it.startsWith(excluded) })
                        else check(texts.any { it.startsWith("TV show ·") })
                    }
                }
                report.putInt("themes_with_working_combined_search_filters", ThemeSpec.entries.size)
            } finally { onMain { theme.set(main, originalTheme); preview.setBoolean(main, originalPreview) } }
            passed = true
        } catch (error: Throwable) { report.putString("error_type", error.javaClass.simpleName) }
        finally {
            onMain { activity?.finish() }
            report.putBoolean("passed", passed)
            finish(if (passed) -1 else 0, report)
        }
    }
}
