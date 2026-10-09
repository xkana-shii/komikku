package eu.kanade.tachiyomi.data.coil

import android.app.Application
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import ca.mpreg.imagedecoder.ImageDecoder
import coil3.Canvas
import coil3.Image
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.hippo.unifile.UniFile
import mihon.core.archive.CbzCrypto
import mihon.core.archive.CbzCrypto.getCoverStream
import mihon.core.archive.archiveReader
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.BufferedInputStream
import java.nio.ByteBuffer

/**
 * A [Decoder] that uses [ImageDecoder] to decode image formats not supported
 * by the Android system decoder (AVIF, JXL, HEIF, etc.).
 */
class ImageDecoder(private val resources: ImageSource, private val options: Options) : Decoder {

    private val context = Injekt.get<Application>()

    /**
     * Wraps a raw [ImageDecoder.Frame] as a Coil [Image] for callers that want
     * direct access to the RGBA [ByteBuffer] (e.g. the new-decoder path).
     */
    class DecodeResultImage(
        val frame: ImageDecoder.Frame,
        val isHdr: Boolean,
        val hdrHeadroom: Float,
        val gainmap: ImageDecoder.Gainmap?,
    ) : Image {
        val image: ByteBuffer get() = frame.image

        // Taken now: a caller may close the frame once it has the pixels.
        override val size: Long = frame.image.capacity().toLong()
        override val width: Int get() = frame.width
        override val height: Int get() = frame.height
        override val shareable: Boolean get() = true
        override fun draw(canvas: Canvas) {}
    }

    override suspend fun decode(): DecodeResult {
        // SY -->
        val source = resources.source()
        var coverStream: BufferedInputStream? = null
        if (source.peek().use { CbzCrypto.detectCoverImageArchive(it.inputStream()) }) {
            if (source.peek().use { ImageUtil.findImageType(it.inputStream()) == null }) {
                coverStream = UniFile.fromFile(resources.file().toFile())
                    ?.archiveReader(context = context)
                    ?.getCoverStream()
            }
        }
        // SY <--

        val res = source.use {
            coverStream.use { archiveCover ->
                ImageDecoder.open(archiveCover ?: it.inputStream()).use { decoder ->
                    DecodeResultImage(
                        decoder.decodeNext(),
                        decoder.isHdr,
                        decoder.hdrHeadroom,
                        if (decoder.hdrKind == ImageDecoder.HdrKind.GAINMAP) decoder.getGainmap() else null,
                    )
                }
            }
        }

        val srcWidth = res.width
        val srcHeight = res.height

        // newDecoder path: caller wants the raw DecodeResult (e.g. for custom rendering).
        // Hand it back as-is; sampling is the caller's responsibility.
        if (options.newDecoder) {
            return DecodeResult(
                image = res,
                isSampled = false,
            )
        }

        // Normal path: produce a Bitmap scaled to the requested output size.
        val dstWidth = options.size.widthPx(options.scale) { srcWidth }
        val dstHeight = options.size.heightPx(options.scale) { srcHeight }
        val sampleSize = DecodeUtils.calculateInSampleSize(
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            dstWidth = dstWidth,
            dstHeight = dstHeight,
            scale = options.scale,
        )

        // Copy RGBA pixels from the native buffer into a full-resolution bitmap.
        // We must do this while `res` (and its native memory) is still alive.
        // HDR frames are half-float RGBA.
        val config = if (res.isHdr) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
        val fullBitmap = try {
            createBitmap(srcWidth, srcHeight, config).also { bitmap ->
                res.image.rewind()
                bitmap.copyPixelsFromBuffer(res.image)
            }
        } finally {
            res.frame.close()
        }

        // Downsample if needed. sampleSize is a power-of-two factor; the target
        // dimensions are src / sampleSize, matching BitmapFactory inSampleSize behaviour.
        val bitmap = if (sampleSize > 1) {
            val scaledWidth = (srcWidth / sampleSize).coerceAtLeast(1)
            val scaledHeight = (srcHeight / sampleSize).coerceAtLeast(1)
            val scaled = fullBitmap.scale(scaledWidth, scaledHeight)
            fullBitmap.recycle()
            scaled
        } else {
            fullBitmap
        }

        return DecodeResult(
            image = bitmap.asImage(),
            isSampled = sampleSize > 1,
        )
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            return if (options.newDecoder || options.customDecoder || isApplicable(result.source.source())) {
                ImageDecoder(result.source, options)
            } else {
                null
            }
        }

        private fun isApplicable(source: BufferedSource): Boolean {
            val type = source.peek().inputStream().buffered().use { stream ->
                ImageUtil.findImageType(stream)
            }
            // SY -->
            source.peek().inputStream().use { stream ->
                if (CbzCrypto.detectCoverImageArchive(stream)) return true
            }
            // SY <--
            return when (type) {
                ImageUtil.ImageType.AVIF,
                ImageUtil.ImageType.JXL,
                ImageUtil.ImageType.HEIF,
                ImageUtil.ImageType.JP2,
                -> true

                else -> false
            }
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()
    }
}
