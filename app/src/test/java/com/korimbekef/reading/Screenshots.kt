package com.korimbekef.reading

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.junit4.ComposeTestRule
import java.io.File
import java.io.FileOutputStream

/**
 * Saves PNG screenshots from Robolectric (native graphics) tests into build/screenshots,
 * so CI can publish them for visual review. All windows (activity, dialogs, bottom sheets)
 * are drawn on top of each other, like on a real screen.
 */
object Screenshots {
    private val dir: File by lazy {
        File(System.getProperty("screenshot.dir") ?: "build/screenshots").apply { mkdirs() }
    }

    fun capture(rule: ComposeTestRule, name: String, activity: Activity? = null) {
        rule.waitForIdle()
        val windows = rootViews().filter { it.isShown && it.width > 0 && it.height > 0 }
        val base = activity?.window?.decorView ?: windows.firstOrNull() ?: return
        if (base.width <= 0 || base.height <= 0) return
        val bitmap = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val toDraw = if (windows.isEmpty()) listOf(base) else windows
        for (view in toDraw) {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            canvas.save()
            canvas.translate(location[0].toFloat(), location[1].toFloat())
            runCatching { view.draw(canvas) }.onFailure { System.err.println("draw failed for $name: $it") }
            canvas.restore()
        }
        save(bitmap, name)
    }

    /** Root views of all windows, in the order they were added (activity first). */
    private fun rootViews(): List<View> = runCatching {
        val cls = Class.forName("android.view.WindowManagerGlobal")
        val instance = cls.getMethod("getInstance").invoke(null)
        val field = cls.getDeclaredField("mViews").apply { isAccessible = true }
        (field.get(instance) as List<*>).filterIsInstance<View>()
    }.getOrElse {
        System.err.println("could not list windows: $it")
        emptyList()
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Saved screenshot ${file.absolutePath}")
    }
}
