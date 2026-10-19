package eu.kanade.tachiyomi.data.coil

import io.kotest.matchers.shouldBe
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import java.nio.file.Files

class CoverImageValidatorTest {

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
    private val png = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00,
    )
    private val gif = "GIF89a".toByteArray(Charsets.US_ASCII)
    private val webp = "RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray(Charsets.US_ASCII)
    private val bmp = byteArrayOf(0x42, 0x4D, 0x00, 0x00)
    private val avif = "ftypavif".toByteArray(Charsets.US_ASCII).let { byteArrayOf(0, 0, 0, 0x20) + it }
    private val heic = "ftypheic".toByteArray(Charsets.US_ASCII).let { byteArrayOf(0, 0, 0, 0x20) + it }

    @Test
    fun `recognizes common image headers`() {
        listOf(jpeg, png, gif, webp, bmp, avif, heic).forEach { bytes ->
            CoverImageValidator.hasKnownImageHeader(bytes) shouldBe true
        }
    }

    @Test
    fun `rejects html and json bodies`() {
        CoverImageValidator.hasKnownImageHeader("<!DOCTYPE html><html>".toByteArray()) shouldBe false
        CoverImageValidator.hasKnownImageHeader("{\"error\":\"not found\"}".toByteArray()) shouldBe false
    }

    @Test
    fun `accepts an image response with an image content type`() {
        CoverImageValidator.isImageResponse(response(jpeg, "image/jpeg")) shouldBe true
    }

    @Test
    fun `accepts an octet stream only when the header is a known image`() {
        CoverImageValidator.isImageResponse(response(jpeg, "application/octet-stream")) shouldBe true
        CoverImageValidator.isImageResponse(
            response("<!DOCTYPE html>".toByteArray(), "application/octet-stream"),
        ) shouldBe false
    }

    @Test
    fun `accepts a response without a content type only when the header is a known image`() {
        CoverImageValidator.isImageResponse(response(png, contentType = null)) shouldBe true
        CoverImageValidator.isImageResponse(response("<html>".toByteArray(), contentType = null)) shouldBe false
    }

    @Test
    fun `rejects text json and xml content types even with image-like bytes`() {
        listOf("text/html", "application/json", "application/xml").forEach { contentType ->
            CoverImageValidator.isImageResponse(response(png, contentType)) shouldBe false
        }
    }

    @Test
    fun `validates an image file`() {
        val file = Files.createTempFile("cover-validator", ".img").toFile()
        try {
            file.writeBytes(jpeg)
            CoverImageValidator.isImageFile(file) shouldBe true

            file.writeBytes("<!DOCTYPE html>".toByteArray())
            CoverImageValidator.isImageFile(file) shouldBe false
        } finally {
            file.delete()
        }
    }

    private fun response(bytes: ByteArray, contentType: String?): Response {
        val body = bytes.toResponseBody(contentType?.toMediaType())
        return Response.Builder()
            .request(Request.Builder().url("https://example.com/cover.jpg").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .apply {
                if (contentType != null) {
                    header("Content-Type", contentType)
                }
            }
            .body(body)
            .build()
    }
}
