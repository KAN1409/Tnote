package com.example.service.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

data class OcrResult(
    val isSuccess: Boolean,
    val text: String,
    val savedImagePath: String? = null,
    val errorMessage: String? = null
)

class OcrManager(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeTextFromUri(uri: Uri): OcrResult = withContext(Dispatchers.IO) {
        try {
            val savedPath = saveImageToAppStorage(uri)
            val image = InputImage.fromFilePath(context, uri)

            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val resultText = visionText.text.trim()
                        continuation.resume(
                            OcrResult(
                                isSuccess = true,
                                text = resultText,
                                savedImagePath = savedPath,
                                errorMessage = if (resultText.isBlank()) "No text could be found in the selected image." else null
                            )
                        )
                    }
                    .addOnFailureListener { exception ->
                        continuation.resume(
                            OcrResult(
                                isSuccess = false,
                                text = "",
                                savedImagePath = savedPath,
                                errorMessage = exception.localizedMessage ?: "Failed to process image OCR"
                            )
                        )
                    }
            }
        } catch (e: Exception) {
            OcrResult(
                isSuccess = false,
                text = "",
                savedImagePath = null,
                errorMessage = "Error reading image: ${e.localizedMessage}"
            )
        }
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        try {
            val savedPath = saveBitmapToAppStorage(bitmap)
            val image = InputImage.fromBitmap(bitmap, 0)

            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val resultText = visionText.text.trim()
                        continuation.resume(
                            OcrResult(
                                isSuccess = true,
                                text = resultText,
                                savedImagePath = savedPath,
                                errorMessage = if (resultText.isBlank()) "No text could be found in the captured image." else null
                            )
                        )
                    }
                    .addOnFailureListener { exception ->
                        continuation.resume(
                            OcrResult(
                                isSuccess = false,
                                text = "",
                                savedImagePath = savedPath,
                                errorMessage = exception.localizedMessage ?: "Failed to process image OCR"
                            )
                        )
                    }
            }
        } catch (e: Exception) {
            OcrResult(
                isSuccess = false,
                text = "",
                savedImagePath = null,
                errorMessage = "Error processing bitmap: ${e.localizedMessage}"
            )
        }
    }

    private fun saveImageToAppStorage(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val ocrDir = File(context.filesDir, "ocr_images").apply { mkdirs() }
            val destFile = File(ocrDir, "ocr_${System.currentTimeMillis()}.jpg")

            FileOutputStream(destFile).use { output ->
                inputStream.copyTo(output)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun saveBitmapToAppStorage(bitmap: Bitmap): String? {
        return try {
            val ocrDir = File(context.filesDir, "ocr_images").apply { mkdirs() }
            val destFile = File(ocrDir, "ocr_${System.currentTimeMillis()}.jpg")

            FileOutputStream(destFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun close() {
        try {
            recognizer.close()
        } catch (e: Exception) {}
    }
}
