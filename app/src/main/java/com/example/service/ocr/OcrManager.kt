package com.example.service.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.OCRResult as PaddleLine
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
    companion object {
        private const val MIN_ACCEPTED_CONFIDENCE = 45
        private const val MIN_USEFUL_CHARACTERS = 6
    }

    private val mutex = Mutex()
    private var tessApi: TessBaseAPI? = null
    private var paddleApi: PaddleOCR? = null
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
        val scaled = scaleForOcr(source)
        val prepared = prepareDarkVariant(scaled)
        val savedPath = saveBitmapToAppStorage(source)
        try {
            val paddleFiles = modelManager.activePaddleOcrFiles()
            val best = if (paddleFiles != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val api = getOrCreatePaddleApi(
                    paddleFiles.detector.absolutePath,
                    paddleFiles.recognizer.absolutePath,
                    paddleFiles.dictionary.absolutePath
                )
                val result = api.recognize(scaled)
                val accepted = result.results.filter { it.confidence >= 0.35f && it.text.isNotBlank() }
                Candidate(
                    text = composePaddleText(accepted),
                    confidence = if (accepted.isEmpty()) 0 else (accepted.map { it.confidence }.average() * 100).toInt()
                )
            } else {
                val api = getOrCreateApi()
                listOf(
                    recognizeCandidate(api, scaled, TessBaseAPI.PageSegMode.PSM_AUTO),
                    recognizeCandidate(api, prepared, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
                ).maxByOrNull { candidate ->
                    candidate.confidence * kotlin.math.sqrt(candidate.text.count(Char::isLetterOrDigit).coerceAtLeast(1).toDouble())
                } ?: Candidate("", 0)
            }
            val text = best.text.trim()
            val confidence = best.confidence
            val usefulCharacters = text.count(Char::isLetterOrDigit)
            if (text.isBlank()) OcrResult(false, "", savedPath, "No readable Arabic or English text was found.")
            else if (confidence < MIN_ACCEPTED_CONFIDENCE || usefulCharacters < MIN_USEFUL_CHARACTERS) {
                OcrResult(false, text, savedPath, "OCR result was too uncertain to save automatically.")
            } else OcrResult(true, text, savedPath)
        } catch (error: Exception) {
            OcrResult(false, "", savedPath, error.localizedMessage ?: "OCR failed")
        } finally {
            if (prepared !== scaled) prepared.recycle()
            if (scaled !== source) scaled.recycle()
        }
    }

    private suspend fun getOrCreatePaddleApi(detector: String, recognizer: String, dictionary: String): PaddleOCR {
        val selectedModel = modelManager.activeOcrName()
        if (loadedModelName != selectedModel) close()
        paddleApi?.let { return it }
        return PaddleOCR.create(
            context = context,
            config = PaddleOCRConfig(detThresh = 0.3f, detBoxThresh = 0.55f, recScoreThresh = 0.30f, recBatchSize = 1),
            engineConfig = EngineConfig(numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)),
            detModelAssetPath = detector,
            recModelAssetPath = recognizer,
            recConfigAssetPath = dictionary
        ).also {
            paddleApi = it
            loadedModelName = selectedModel
        }
    }

    private fun composePaddleText(lines: List<PaddleLine>): String {
        if (lines.isEmpty()) return ""
        val rowHeight = lines.map { line ->
            val ys = line.box.points.map { it.y }
            (ys.maxOrNull() ?: 0f) - (ys.minOrNull() ?: 0f)
        }.filter { it > 0f }.average().takeIf { !it.isNaN() }?.toFloat() ?: 18f
        val sorted = lines.sortedWith(compareBy<PaddleLine> { it.box.points.minOf { p -> p.y } }.thenBy { it.box.points.minOf { p -> p.x } })
        val rows = mutableListOf<MutableList<PaddleLine>>()
        sorted.forEach { line ->
            val cy = line.box.points.map { it.y }.average().toFloat()
            val row = rows.lastOrNull()
            val rowCy = row?.lastOrNull()?.box?.points?.map { it.y }?.average()?.toFloat()
            if (row == null || rowCy == null || kotlin.math.abs(cy - rowCy) > rowHeight * 0.65f) rows += mutableListOf(line)
            else row += line
        }
        return rows.joinToString("\n") { row ->
            val arabicChars = row.sumOf { it.text.count { ch -> ch.code in 0x0600..0x06FF } }
            val latinChars = row.sumOf { it.text.count { ch -> ch in 'A'..'Z' || ch in 'a'..'z' } }
            val ordered = if (arabicChars > latinChars) row.sortedByDescending { it.box.points.minOf { p -> p.x } }
            else row.sortedBy { it.box.points.minOf { p -> p.x } }
            ordered.joinToString(" ") { it.text.trim() }.trim()
        }.trim()
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
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
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

    private data class Candidate(val text: String, val confidence: Int)

    private fun recognizeCandidate(api: TessBaseAPI, bitmap: Bitmap, pageSegMode: Int): Candidate {
        api.pageSegMode = pageSegMode
        api.setImage(bitmap)
        val candidate = Candidate(api.utF8Text.orEmpty().trim(), api.meanConfidence())
        api.clear()
        return candidate
    }

    private fun scaleForOcr(bitmap: Bitmap): Bitmap {
        val maxDimension = 3200
        val largest = maxOf(bitmap.width, bitmap.height)
        val minimumWidthScale = 1800f / bitmap.width.coerceAtLeast(1)
        val maximumScale = maxDimension.toFloat() / largest.coerceAtLeast(1)
        val scale = minimumWidthScale.coerceAtLeast(1f).coerceAtMost(maximumScale)
        return if (scale == 1f) bitmap else Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun prepareDarkVariant(bitmap: Bitmap): Bitmap {
        val scaled = bitmap.copy(Bitmap.Config.ARGB_8888, true)
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
            gray = ((gray - 128) * 1.15f + 128).toInt().coerceIn(0, 255)
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
        paddleApi?.close()
        paddleApi = null
        loadedModelName = null
    }
}
