package com.goroyattemiyo.wms.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.goroyattemiyo.wms.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MediaArtwork(
    media: MediaPresentation,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val cacheKey = media.artworkCandidates.joinToString(separator = "|")
    val bitmap by produceState<Bitmap?>(initialValue = ArtworkBitmapCache.get(cacheKey), key1 = cacheKey) {
        value = ArtworkBitmapCache.get(cacheKey) ?: withContext(Dispatchers.IO) {
            resolveArtwork(context, media.artworkCandidates)
        }?.also { ArtworkBitmapCache.put(cacheKey, it) }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Image(
                painter = painterResource(R.drawable.wms_emblem),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun resolveArtwork(
    context: Context,
    candidates: List<MediaArtworkCandidate>,
): Bitmap? = candidates.firstNotNullOfOrNull { candidate ->
    runCatching {
        when (candidate) {
            is MediaArtworkCandidate.Persisted -> decodePersisted(context, candidate.uri)
            is MediaArtworkCandidate.EmbeddedAudio -> embeddedArtwork(candidate.localPath)
            is MediaArtworkCandidate.VideoFrame -> videoFrame(candidate.localPath)
            MediaArtworkCandidate.WmsFallback -> null
        }
    }.getOrNull()
}

private fun decodePersisted(context: Context, value: String): Bitmap? {
    val uri = Uri.parse(value)
    return when (uri.scheme?.lowercase()) {
        "https" -> downloadBitmap(value)
        "content", "android.resource" -> context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        "file" -> uri.path?.let(BitmapFactory::decodeFile)
        else -> null
    }
}

private fun downloadBitmap(value: String): Bitmap? {
    val connection = URL(value).openConnection() as? HttpsURLConnection ?: return null
    return try {
        connection.connectTimeout = 5_000
        connection.readTimeout = 7_000
        connection.instanceFollowRedirects = true
        connection.connect()
        if (connection.responseCode !in 200..299) return null
        val declaredSize = connection.contentLengthLong
        if (declaredSize > MAX_ARTWORK_BYTES) return null
        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_ARTWORK_BYTES) return null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } finally {
        connection.disconnect()
    }
}

private fun embeddedArtwork(localPath: String): Bitmap? {
    val file = File(localPath)
    if (!file.isFile || file.length() <= 0L) return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    } finally {
        retriever.release()
    }
}

private fun videoFrame(localPath: String): Bitmap? {
    val file = File(localPath)
    if (!file.isFile || file.length() <= 0L) return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } finally {
        retriever.release()
    }
}

private object ArtworkBitmapCache {
    private val cache = LruCache<String, Bitmap>(16)

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

private const val MAX_ARTWORK_BYTES = 8 * 1024 * 1024
