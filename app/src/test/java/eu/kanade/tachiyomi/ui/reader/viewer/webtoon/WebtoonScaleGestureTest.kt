package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// KNS -->
class WebtoonScaleGestureTest {

    private fun resolve(doubleTap: Boolean, doubleTapZoom: Boolean, pinch: Boolean, pointers: Int) =
        WebtoonScaleGesture.resolve(doubleTap, doubleTapZoom, pinch, pointers)

    @Test
    fun `a pinch after a double tap is not a quick scale and needs pinch zoom`() {
        // pinch disabled, second tap of a double tap still down, a second finger joins
        resolve(doubleTap = true, doubleTapZoom = true, pinch = false, pointers = 2) shouldBe
            WebtoonScaleGesture(accepted = false, quickScale = false)
        // with pinch enabled it is a pinch, not a quick scale
        resolve(doubleTap = true, doubleTapZoom = true, pinch = true, pointers = 2) shouldBe
            WebtoonScaleGesture(accepted = true, quickScale = false)
    }

    @Test
    fun `a one finger quick scale needs a double tap and double tap zoom`() {
        resolve(doubleTap = true, doubleTapZoom = true, pinch = false, pointers = 1) shouldBe
            WebtoonScaleGesture(accepted = true, quickScale = true)
        resolve(doubleTap = true, doubleTapZoom = true, pinch = true, pointers = 1) shouldBe
            WebtoonScaleGesture(accepted = true, quickScale = true)
        resolve(doubleTap = false, doubleTapZoom = true, pinch = true, pointers = 1) shouldBe
            WebtoonScaleGesture(accepted = false, quickScale = false)
        resolve(doubleTap = true, doubleTapZoom = false, pinch = true, pointers = 1) shouldBe
            WebtoonScaleGesture(accepted = false, quickScale = false)
    }

    @Test
    fun `double tap zoom off keeps a real pinch and rejects quick scale`() {
        resolve(doubleTap = true, doubleTapZoom = false, pinch = true, pointers = 2) shouldBe
            WebtoonScaleGesture(accepted = true, quickScale = false)
        resolve(doubleTap = true, doubleTapZoom = false, pinch = true, pointers = 1).accepted shouldBe false
    }

    @Test
    fun `both disabled rejects both and both enabled allows both`() {
        for (pointers in 1..3) {
            for (doubleTap in listOf(true, false)) {
                resolve(doubleTap, doubleTapZoom = false, pinch = false, pointers = pointers).accepted shouldBe false
            }
        }
        resolve(doubleTap = true, doubleTapZoom = true, pinch = true, pointers = 1).quickScale shouldBe true
        resolve(doubleTap = false, doubleTapZoom = true, pinch = true, pointers = 2).accepted shouldBe true
        resolve(doubleTap = true, doubleTapZoom = true, pinch = true, pointers = 3).accepted shouldBe true
    }

    @Test
    fun `a scale step is held to the pointer count it arrives with`() {
        // armed quick scale, one finger
        WebtoonScaleGesture.allowsScale(pointerCount = 1, pinchZoomEnabled = false, quickScaling = true) shouldBe true
        // not armed: one finger cannot scale
        WebtoonScaleGesture.allowsScale(pointerCount = 1, pinchZoomEnabled = true, quickScaling = false) shouldBe false
        // quick scale that gains a second finger becomes a pinch and needs pinch zoom
        WebtoonScaleGesture.allowsScale(pointerCount = 2, pinchZoomEnabled = false, quickScaling = true) shouldBe false
        WebtoonScaleGesture.allowsScale(pointerCount = 2, pinchZoomEnabled = true, quickScaling = true) shouldBe true
        // a plain pinch
        WebtoonScaleGesture.allowsScale(pointerCount = 2, pinchZoomEnabled = true, quickScaling = false) shouldBe true
        WebtoonScaleGesture.allowsScale(pointerCount = 2, pinchZoomEnabled = false, quickScaling = false) shouldBe false
    }

    @Test
    fun `a quick scale turning into a pinch and back follows the setting at every step`() {
        // finger 1 holds the second tap and drags: armed quick scale with pinch disabled
        val begin = resolve(doubleTap = true, doubleTapZoom = true, pinch = false, pointers = 1)
        begin.quickScale shouldBe true
        val steps = listOf(1, 1, 2, 2, 1)
        steps.map { WebtoonScaleGesture.allowsScale(it, pinchZoomEnabled = false, quickScaling = begin.quickScale) } shouldBe
            listOf(true, true, false, false, true)
    }

    @Test
    fun `the finger being lifted does not count`() {
        WebtoonScaleGesture.effectivePointerCount(pointerCount = 2, isPointerUp = true) shouldBe 1
        WebtoonScaleGesture.effectivePointerCount(pointerCount = 2, isPointerUp = false) shouldBe 2
        WebtoonScaleGesture.effectivePointerCount(pointerCount = 1, isPointerUp = false) shouldBe 1
    }
}
// KNS <--
