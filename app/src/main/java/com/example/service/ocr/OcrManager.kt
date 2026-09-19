package com.example.service.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.example.service.models.ModelManager
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class OcrResult(
    val isSuccess: Boolean,
    val text: String,
    val savedImagePath: String? = null,
    val errorMessage: String? = null
)

/** Offline Arabic + English OCR with bundled models. */
class OcrManager(
    private val context: Context,
    private val modelManager: ModelManager
) {
    private val mutex = Mutex()
    private var tessApi: TessBaseAPI? = null
    private var loadedModelName: String? = null

    suspend fun recognizeTextFromUri(uri: Uri): OcrResult = withContext(Dispatchers.IO) {
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }.getOrNull()
        if (bitmap == null) OcrResult(false, "", errorMessage = "The selected image could not be opened.")
        else recognize(bitmap)
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        recognize(bitmap)
    }

    private suspend fun recognize(source: Bitmap): OcrResult = mutex.withLock {
        val prepared = prepareForOcr(source)
        val savedPath = saveBitmapToAppStorage(source)
        try {
            val api = getOrCreateApi()
            api.setImage(prepared)
            val text = api.utF8Text.orEmpty().trim()
            api.clear()
            if (text.isBlank()) OcrResult(false, "", savedPath, "No readable Arabic or English text was found.")
            else OcrResult(true, text, savedPath)
        } catch (error: Exception) {
            OcrResult(false, "", savedPath, error.localizedMessage ?: "OCR failed")
        } finally {
            if (prepared !== source) prepared.recycle()
        }
    }

    private fun getOrCreateApi(): TessBaseAPI {
        val selectedModel = modelManager.activeOcrName()
        if (loadedModelName != selectedModel) close()
        tessApi?.let { return it }
        val downloaded = modelManager.activeOcrFiles()
        val dataDir = downloaded?.dataDirectory ?: File(context.filesDir, "tesseract").also { embeddedDir ->
            val tessDataDir = File(embeddedDir, "tessdata").apply { mkdirs() }
            copyModelIfNeeded("ara.traineddata", tessDataDir)
            copyModelIfNeeded("eng.traineddata", tessDataDir)
        }
        return TessBaseAPI().also { api ->
            check(api.init(dataDir.absolutePath, "ara+eng")) { "Arabic OCR model could not be initialized." }
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
            tessApi = api
            loadedModelName = selectedModel
        }
    }

    private fun copyModelIfNeeded(name: String, destination: File) {
        val target = File(destination, name)
        if (target.exists() && target.length() > 0) return
        context.assets.open("tessdata/$name").use { input ->
            FileOutputStream(target).use(input::copyTo)
        }
    }

    private fun prepareForOcr(bitmap: Bitmap): Bitmap {
        val maxDimension = 3200
        val largest = maxOf(bitmap.width, bitmap.height)
        val minimumWidthScale = 1800f / bitmap.width.coerceAtLeast(1)
        val maximumScale = maxDimension.toFloat() / largest.coerceAtLeast(1)
        val scale = minimumWidthScale.coerceAtLeast(1f).coerceAtMost(maximumScale)
        val scaled = if (scale == 1f) bitmap.copy(Bitmap.Config.ARGB_8888, true) else Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        var luminanceTotal = 0L
        pixels.forEach { pixel ->
            luminanceTotal += (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
        }
        val invert = luminanceTotal / pixels.size.coerceAtLeast(1) < 128
        pixels.indices.forEach { index ->
            val color = pixels[index]
            var gray = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
            if (invert) gray = 255 - gray
            gray = ((gray - 128) * 1.35f + 128).toInt().coerceIn(0, 255)
            pixels[index] = Color.rgb(gray, gray, gray)
        }
        scaled.setPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        return scaled
    }

    private fun saveBitmapToAppStorage(bitmap: Bitmap): String? = runCatching {
        val ocrDir = File(context.filesDir, "ocr_images").apply { mkdirs() }
        val destination = File(ocrDir, "ocr_${System.currentTimeMillis()}.jpg")
        FileOutputStream(destination).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        destination.absolutePath
    }.getOrNull()

    fun close() {
        tessApi?.recycle()
        tessApi = null
        loadedModelName = null
    }
}
