package dev.mietze.piimager.system

import android.content.ContentResolver
import android.net.Uri
import dev.mietze.piimager.data.CustomizationSettings
import dev.mietze.piimager.data.FlashProgress
import dev.mietze.piimager.data.FlashStage
import dev.mietze.piimager.data.ImageSource
import dev.mietze.piimager.data.StorageTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.Locale
import java.util.zip.ZipInputStream

class ImageWriter(
    private val resolver: ContentResolver,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    data class Result(
        val bytesWritten: Long,
        val imageSha256: String,
        val diskSha256: String?,
        val downloadSha256: String?
    )

    suspend fun write(
        source: ImageSource,
        target: StorageTarget,
        verifyDisk: Boolean,
        customizationSettings: CustomizationSettings? = null,
        initFormat: String? = null,
        isCancelled: () -> Boolean,
        onProgress: (FlashProgress) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        require(target.removable || target.usbBacked) {
            "Safety gate: target is not removable or USB-backed."
        }

        val meta = when (source) {
            is ImageSource.Remote -> SourceMeta(
                name = source.item.url ?: source.item.name,
                expectedSize = source.item.extractSize,
                expectedImageHash = source.item.extractSha256,
                expectedDownloadHash = source.item.downloadSha256
            )
            is ImageSource.Local -> SourceMeta(
                name = source.displayName,
                expectedSize = null,
                expectedImageHash = null,
                expectedDownloadHash = null
            )
        }

        meta.expectedSize?.let {
            require(it <= target.sizeBytes) {
                "Image is ${formatBytes(it)}, target only has ${formatBytes(target.sizeBytes)}."
            }
        }

        onProgress(
            FlashProgress(
                FlashStage.PREPARING,
                message = "Preparing storage",
                detail = target.path
            )
        )

        if (isCancelled()) throw CancelledException()

        val downloadDigest = MessageDigest.getInstance("SHA-256")
        val imageDigest = MessageDigest.getInstance("SHA-256")

        val rawInput: InputStream
        val networkClose: (() -> Unit)?

        when (source) {
            is ImageSource.Remote -> {
                val url = source.item.url ?: error("Selected OS has no image URL")
                val response = client.newCall(
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", "PiImagerAndroid/0.1")
                        .build()
                ).execute()
                if (!response.isSuccessful) {
                    response.close()
                    error("Image download HTTP ${response.code}")
                }
                val body = response.body ?: run {
                    response.close()
                    error("Image download returned no data")
                }
                rawInput = DigestInputStream(body.byteStream(), downloadDigest)
                networkClose = { response.close() }
            }

            is ImageSource.Local -> {
                val input = resolver.openInputStream(Uri.parse(source.uri))
                    ?: error("Unable to open local image")
                rawInput = input
                networkClose = null
            }
        }

        var writerProcess: Process? = null
        try {
            val decoded = decodeStream(BufferedInputStream(rawInput, BUFFER), meta.name)
            writerProcess = RootStorage.openRootWriter(target.path)
            val output = writerProcess.outputStream.buffered(BUFFER)
            var total = 0L
            val buffer = ByteArray(BUFFER)

            onProgress(
                FlashProgress(
                    FlashStage.DOWNLOADING_WRITING,
                    bytesTotal = meta.expectedSize,
                    message = "Writing image",
                    detail = target.model
                )
            )

            while (true) {
                if (isCancelled()) throw CancelledException()
                val count = decoded.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                output.write(buffer, 0, count)
                imageDigest.update(buffer, 0, count)
                total += count

                val fraction = meta.expectedSize
                    ?.takeIf { it > 0 }
                    ?.let { (total.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat() }
                    ?: 0f

                onProgress(
                    FlashProgress(
                        FlashStage.DOWNLOADING_WRITING,
                        fraction = fraction,
                        bytesDone = total,
                        bytesTotal = meta.expectedSize,
                        message = "Writing image",
                        detail = "${formatBytes(total)} written"
                    )
                )
            }

            output.flush()
            output.close()

            val exit = writerProcess.waitFor()
            if (exit != 0) {
                val err = writerProcess.errorStream.bufferedReader().readText().trim()
                error("Root writer failed ($exit)${if (err.isNotBlank()) ": $err" else ""}")
            }
            RootStorage.sync()

            val imageHash = imageDigest.digest().toHex()
            val dlHash = if (source is ImageSource.Remote) downloadDigest.digest().toHex() else null

            meta.expectedImageHash?.let { expected ->
                check(imageHash.equals(expected, ignoreCase = true)) {
                    "Image SHA-256 mismatch. Expected $expected, got $imageHash"
                }
            }
            var diskHash: String? = null
            if (verifyDisk) {
                onProgress(
                    FlashProgress(
                        FlashStage.VERIFYING,
                        fraction = 0f,
                        bytesTotal = total,
                        message = "Verifying written data",
                        detail = "Reading image back from ${target.name}"
                    )
                )
                diskHash = verifyTarget(target.path, total, isCancelled, onProgress)
                check(diskHash.equals(imageHash, ignoreCase = true)) {
                    "Verification failed. Written image and storage hashes differ."
                }
            }

            var customised = false
            if (customizationSettings?.enabled == true && !initFormat.isNullOrBlank() && initFormat != "none") {
                if (isCancelled()) throw CancelledException()
                onProgress(
                    FlashProgress(
                        FlashStage.CUSTOMIZING,
                        fraction = 0.98f,
                        bytesDone = total,
                        bytesTotal = total,
                        message = "Applying OS customisation",
                        detail = initFormat
                    )
                )
                customised = CustomizationApplier.apply(target, initFormat, customizationSettings)
                RootStorage.sync()
            }

            val finalDetail = buildString {
                append(if (verifyDisk) "Image verified" else "Verification skipped")
                if (customised) append(" • OS customisation applied")
            }
            onProgress(
                FlashProgress(
                    FlashStage.DONE,
                    fraction = 1f,
                    bytesDone = total,
                    bytesTotal = total,
                    message = "Write successful",
                    detail = finalDetail
                )
            )

            Result(total, imageHash, diskHash, dlHash)
        } catch (e: CancelledException) {
            writerProcess?.destroy()
            onProgress(FlashProgress(FlashStage.CANCELLED, message = "Write cancelled"))
            throw e
        } finally {
            runCatching { rawInput.close() }
            runCatching { networkClose?.invoke() }
        }
    }

    private fun decodeStream(input: InputStream, name: String): InputStream {
        val lower = name.lowercase(Locale.ROOT).substringBefore('?')
        return when {
            lower.endsWith(".xz") -> XZInputStream(input)
            lower.endsWith(".gz") || lower.endsWith(".gzip") -> GZIPInputStream(input, BUFFER)
            lower.endsWith(".zip") -> openZipImage(input)
            else -> input
        }
    }

    private fun openZipImage(input: InputStream): InputStream {
        val zip = ZipInputStream(input)
        while (true) {
            val entry = zip.nextEntry ?: error("ZIP does not contain a flashable image")
            if (entry.isDirectory) continue
            val n = entry.name.lowercase(Locale.ROOT)
            if (
                n.endsWith(".img") ||
                n.endsWith(".raw") ||
                n.endsWith(".bin") ||
                entry.size > 64L * 1024 * 1024
            ) {
                return zip
            }
            zip.closeEntry()
        }
    }

    private fun verifyTarget(
        targetPath: String,
        length: Long,
        isCancelled: () -> Boolean,
        onProgress: (FlashProgress) -> Unit
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val p = RootStorage.openRootReader(targetPath, length)
        val input = p.inputStream.buffered(BUFFER)
        val buffer = ByteArray(BUFFER)
        var total = 0L

        while (true) {
            if (isCancelled()) {
                p.destroy()
                throw CancelledException()
            }
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            digest.update(buffer, 0, count)
            total += count
            onProgress(
                FlashProgress(
                    FlashStage.VERIFYING,
                    fraction = (total.toDouble() / length.toDouble())
                        .coerceIn(0.0, 1.0).toFloat(),
                    bytesDone = total,
                    bytesTotal = length,
                    message = "Verifying written data",
                    detail = "${formatBytes(total)} checked"
                )
            )
        }

        input.close()
        val exit = p.waitFor()
        check(exit == 0) { "Unable to read storage back for verification" }
        return digest.digest().toHex()
    }

    private data class SourceMeta(
        val name: String,
        val expectedSize: Long?,
        val expectedImageHash: String?,
        val expectedDownloadHash: String?
    )

    class CancelledException : RuntimeException("Cancelled")

    companion object {
        const val BUFFER = 1024 * 1024

        fun formatBytes(value: Long): String {
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var v = value.toDouble()
            var i = 0
            while (v >= 1024 && i < units.lastIndex) {
                v /= 1024
                i++
            }
            return if (i == 0) "${value} ${units[i]}" else String.format(Locale.US, "%.1f %s", v, units[i])
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
