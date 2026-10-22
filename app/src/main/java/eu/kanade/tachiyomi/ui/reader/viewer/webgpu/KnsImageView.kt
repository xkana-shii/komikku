package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.content.Context
import androidx.compose.runtime.Composable
import ca.mpreg.webgpuviewer.ImageView
import ca.mpreg.webgpuviewer.viewer.ImageViewerContinuousState
import ca.mpreg.webgpuviewer.viewer.KnsImageViewer
import ca.mpreg.webgpuviewer.viewer.KnsImageViewerContinuous

// KNS -->
/** Uses the upstream renderer with fork gesture routing until that fix is available upstream. */
internal open class KnsImageView(
    context: Context,
    isVertical: Boolean = false,
    isReversed: Boolean = false,
) : ImageView(context, isVertical = isVertical, isReversed = isReversed) {
    @Composable
    override fun Content() {
        KnsImageViewer(state = state)
    }
}

internal class KnsImageViewContinuous(context: Context) : KnsImageView(context) {
    override val state = ImageViewerContinuousState()

    @Composable
    override fun Content() {
        KnsImageViewerContinuous(state = state)
    }
}
// KNS <--
