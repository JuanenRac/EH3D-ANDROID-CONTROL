// EH3D-ANDROID-CONTROL - bounded MJPEG renderer for the camera of the drill head (through EH3D-SERVER).
package es.electrohobby3d.eh3d

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

private sealed interface MjpegState { data object Loading : MjpegState; data class Frame(val bitmap: Bitmap) : MjpegState; data class Failure(val message: String) : MjpegState }

/** How long to wait before asking for a stream again: 1 s, doubling to 30 s, back to 1 s once a picture arrived. */
class Backoff {
    var attempts = 0
        private set
    val delayMs: Long get() = minOf(30_000L, 1_000L shl attempts.coerceAtMost(5))
    fun failed() { attempts = minOf(attempts + 1, 10) }
    fun succeeded() { attempts = 0 }
}

@Composable
/**
 * [sampleSize] 2 or 4 decodes a picture at half or a quarter of its size: a grid of sixteen tiles does not need sixteen full-size pictures at once. The stream stops while the app is out of
 * sight, a dropped stream is asked for again after 1, 2, 4... seconds (30 at most) instead of staying dead, and two bitmaps are decoded in turn so that each picture does not allocate a new one.
 */
fun MjpegFeed(url: String, modifier: Modifier = Modifier, sampleSize: Int = 1, resolve: (suspend (String) -> String)? = null) {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var inSight by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) inSight = true else if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) inSight = false
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val feed by produceState<MjpegState>(MjpegState.Loading, url, inSight) {
        if (!inSight) return@produceState
        value = MjpegState.Loading
        val backoff = Backoff()
        var shown: Bitmap? = null
        var spare: Bitmap? = null
        withContext(Dispatchers.IO) {
            while (isActive) {
                var connection: HttpURLConnection? = null
                try {
                    // [resolve] turns the plain address into one with a stream ticket (a viewer cannot watch without it); a ticket is for one use, so each try asks for a new one
                    val target = runCatching { resolve?.invoke(url) }.getOrNull() ?: url
                    connection = URL(target).openConnection() as HttpURLConnection
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 20_000
                    connection.setRequestProperty("Accept", "multipart/x-mixed-replace")
                    if (connection.responseCode != 200) throw IllegalStateException(trf("El servidor respondió HTTP %s", connection.responseCode))
                    connection.inputStream.use { raw ->
                        val input = BufferedInputStream(raw, 64 * 1024)
                        while (isActive) {
                            val frame = readJpeg(input) ?: break
                            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceIn(1, 8); inMutable = true; inBitmap = spare }
                            val bitmap = try { BitmapFactory.decodeByteArray(frame, 0, frame.size, options) }
                                catch (_: IllegalArgumentException) { options.inBitmap = null; BitmapFactory.decodeByteArray(frame, 0, frame.size, options) }   // a spare of another size cannot be reused
                            if (bitmap != null) { spare = shown; shown = bitmap; value = MjpegState.Frame(bitmap); backoff.succeeded() }
                        }
                    }
                } catch (error: Exception) {
                    if (!isActive) break
                    if (value !is MjpegState.Frame) value = MjpegState.Failure(error.message ?: tr("Vídeo no disponible"))
                } finally { connection?.disconnect() }   // whatever happened (a cancelled screen, a lost connection), the socket is closed
                if (!isActive) break
                backoff.failed()
                if (value is MjpegState.Loading) value = MjpegState.Failure(tr("No llegan imágenes"))
                kotlinx.coroutines.delay(backoff.delayMs)
            }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        when (val current = feed) {
            MjpegState.Loading -> CircularProgressIndicator()
            is MjpegState.Failure -> Text(current.message, color = MaterialTheme.colorScheme.error)
            is MjpegState.Frame -> Canvas(Modifier.fillMaxSize()) {
                val image = current.bitmap.asImageBitmap()
                val scale = minOf(size.width / image.width, size.height / image.height)
                val width = image.width * scale; val height = image.height * scale
                drawImage(image, dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - width) / 2).toInt(), ((size.height - height) / 2).toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(width.toInt(), height.toInt()))
            }
        }
    }
}

private const val MAX_JPEG_BYTES = 8 * 1024 * 1024

/**
 * One JPEG of the stream, read by its structure: after the start marker every segment is skipped whole by its length, and the picture ends at the end marker that follows the scan.
 * Cutting at the first FFD9 (the end of a picture) was wrong for a picture that carries a thumbnail (an EXIF block holds a whole small JPEG with its own end marker): the frame came out truncated.
 * Null when the stream ends or the picture is larger than [MAX_JPEG_BYTES].
 */
private fun readJpeg(input: BufferedInputStream): ByteArray? {
    val out = java.io.ByteArrayOutputStream(64 * 1024)
    var previous = -1
    while (true) {
        val value = input.read()
        if (value < 0) return null
        if (previous == 0xff && value == 0xd8) { out.write(0xff); out.write(0xd8); break }
        previous = value
    }
    var marker = nextMarker(input) ?: return null
    while (true) {
        out.write(0xff); out.write(marker)
        if (marker == 0xd9) return out.toByteArray()
        if (marker in 0xd0..0xd7 || marker == 0x01) { marker = nextMarker(input) ?: return null; continue }
        val high = input.read(); val low = input.read()
        if (high < 0 || low < 0) return null
        out.write(high); out.write(low)
        val length = (high shl 8) or low
        if (length < 2) return null
        for (i in 0 until length - 2) { val value = input.read(); if (value < 0) return null; out.write(value) }
        if (out.size() > MAX_JPEG_BYTES) return null
        if (marker == 0xda) {
            // The coded picture: it goes on until a marker that is neither a stuffed zero nor a restart.
            while (true) {
                val value = input.read()
                if (value < 0) return null
                if (value != 0xff) { out.write(value); if (out.size() > MAX_JPEG_BYTES) return null; continue }
                var next = input.read()
                while (next == 0xff) next = input.read()
                if (next < 0) return null
                if (next == 0x00 || next in 0xd0..0xd7) { out.write(0xff); out.write(next); continue }
                marker = next
                break
            }
        } else marker = nextMarker(input) ?: return null
    }
}

/** The code of the next marker (the byte after an FF that is not a fill FF or a stuffed zero), or null at the end of the stream. */
private fun nextMarker(input: BufferedInputStream): Int? {
    while (true) {
        val value = input.read()
        if (value < 0) return null
        if (value != 0xff) continue
        var next = input.read()
        while (next == 0xff) next = input.read()
        if (next < 0) return null
        if (next != 0x00) return next
    }
}
