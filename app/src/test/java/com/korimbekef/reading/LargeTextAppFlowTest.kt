package com.korimbekef.reading

import org.junit.Before
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * All the flows again on a small phone with the device font set larger, as many parents use it:
 * nothing may be cut off, squeezed into a vertical column or pushed off the screen.
 */
@Config(sdk = [35], application = TestApp::class, qualifiers = "w360dp-h740dp-xxhdpi")
class LargeTextAppFlowTest : AppFlowTest() {
    override val shotPrefix: String = "large_"

    @Before
    fun largeFont() {
        RuntimeEnvironment.setFontScale(1.3f)
    }
}
