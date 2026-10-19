// KUN -->
package eu.kanade.tachiyomi.data.coil

import okhttp3.Response
import java.io.File

/**
 * Rejects HTML/JSON error pages and other non-image bodies before they are cached or persisted
 * as a recovered cover.
 *
 * This is a header sniff only. A body that starts with a valid image header but is truncated or
 * otherwise corrupt still reaches Coil's decoder; cleaning that up after a decode failure is a
 * follow-up (it needs a decode-success callback from the image pipeline).
 */
internal object CoverImageValidator {

    private const val SNIFF_BYTES = 16

    private val HEIF_BRANDS = setOf("avif", "avis", "heic", "heix", "hevc", "mif1", "msf1")

    fun hasKnownImageHeader(bytes: ByteArray): Boolean {
        return isJpeg(bytes) ||
            isPng(bytes) ||
            isGif(bytes) ||
            isWebp(bytes) ||
            isBmp(bytes) ||
            isHeif(bytes)
    }

    fun isImageResponse(response: Response): Boolean {
        return try {
            val contentType = response.header("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()

            if (contentType != null) {
                if (contentType.startsWith("text/") ||
                    contentType == "application/json" ||
                    contentType == "application/xml"
                ) {
                    return false
                }
            }

            val bytes = response.peekBody(SNIFF_BYTES.toLong()).bytes()
            hasKnownImageHeader(bytes)
        } catch (e: Exception) {
            false
        }
    }

    fun isImageFile(file: File): Boolean {
        return try {
            val bytes = ByteArray(SNIFF_BYTES)
            val read = file.inputStream().use { it.read(bytes) }
            read > 0 && hasKnownImageHeader(bytes.copyOf(read))
        } catch (e: Exception) {
            false
        }
    }

    private fun isJpeg(bytes: ByteArray): Boolean {
        return bytes.size >= 3 &&
            bytes[0].unsigned() == 0xFF &&
            bytes[1].unsigned() == 0xD8 &&
            bytes[2].unsigned() == 0xFF
    }

    private fun isPng(bytes: ByteArray): Boolean {
        return bytes.size >= 8 &&
            bytes[0].unsigned() == 0x89 &&
            bytes[1].ascii() == 'P' &&
            bytes[2].ascii() == 'N' &&
            bytes[3].ascii() == 'G' &&
            bytes[4].unsigned() == 0x0D &&
            bytes[5].unsigned() == 0x0A &&
            bytes[6].unsigned() == 0x1A &&
            bytes[7].unsigned() == 0x0A
    }

    private fun isGif(bytes: ByteArray): Boolean {
        return bytes.size >= 6 &&
            bytes[0].ascii() == 'G' &&
            bytes[1].ascii() == 'I' &&
            bytes[2].ascii() == 'F' &&
            bytes[3].ascii() == '8' &&
            (bytes[4].ascii() == '7' || bytes[4].ascii() == '9') &&
            bytes[5].ascii() == 'a'
    }

    private fun isWebp(bytes: ByteArray): Boolean {
        return bytes.size >= 12 &&
            bytes.ascii(0, 4) == "RIFF" &&
            bytes.ascii(8, 4) == "WEBP"
    }

    private fun isBmp(bytes: ByteArray): Boolean {
        return bytes.size >= 2 && bytes[0].ascii() == 'B' && bytes[1].ascii() == 'M'
    }

    private fun isHeif(bytes: ByteArray): Boolean {
        return bytes.size >= 12 &&
            bytes.ascii(4, 4) == "ftyp" &&
            bytes.ascii(8, 4) in HEIF_BRANDS
    }

    private fun ByteArray.ascii(offset: Int, length: Int): String {
        return String(this, offset, length, Charsets.US_ASCII)
    }

    private fun Byte.ascii(): Char = unsigned().toInt().toChar()

    private fun Byte.unsigned(): Int = toInt() and 0xFF
}
// KUN <--
