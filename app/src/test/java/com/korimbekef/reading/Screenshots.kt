package com.korimbekef.reading

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import java.io.File
import java.io.FileOutputStream

/**
 * Saves PNG screenshots from Robolectric (native graphics) tests into build/screenshots,
 * so CI can publish them for visual review. Dialog windows are saved as extra files.
 */
object Screenshots {
    private val dir: File by lazy {
        File(System.getProperty("screenshot.dir") ?: "build/screenshots").apply { mkdirs() }
    }

    fun capture(rule: ComposeTestRule, name: String, activity: Activity? = null) {
        rule.waitForIdle()
        val roots = runCatching { rule.onAllNodes(isRoot()).fetchSemanticsNodes().size }.getOrDefault(0)
        var saved = false
        for (i in 0 until roots) {
            val suffix = if (i == 0) "" else "_window$i"
            runCatching { rule.onAllNodes(isRoot())[i].captureToImage().asAndroidBitmap() }
                .onSuccess {
                    save(it, name + suffix)
                    saved = true
                }
                .onFailure { System.err.println("captureToImage failed for $name$suffix: $it") }
        }
        if (!saved && activity != null) captureView(activity.window.decorView, name)
    }

    fun captureView(view: View, name: String) {
        if (view.width <= 0 || view.height <= 0) return
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        save(bitmap, name)
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Saved screenshot ${file.absolutePath}")
    }
}
