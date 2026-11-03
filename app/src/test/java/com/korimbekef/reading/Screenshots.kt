package com.korimbekef.reading

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import java.io.File
import java.io.FileOutputStream

/**
 * Saves PNG screenshots from Robolectric (native graphics) tests into build/screenshots,
 * so CI can publish them for visual review.
 */
object Screenshots {
    private val dir: File by lazy {
        File(System.getProperty("screenshot.dir") ?: "build/screenshots").apply { mkdirs() }
    }

    fun capture(rule: ComposeTestRule, name: String) {
        rule.waitForIdle()
        val bitmap = try {
            rule.onRoot().captureToImage().asAndroidBitmap()
        } catch (t: Throwable) {
            System.err.println("captureToImage failed for $name: $t")
            null
        }
        if (bitmap != null) save(bitmap, name)
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
