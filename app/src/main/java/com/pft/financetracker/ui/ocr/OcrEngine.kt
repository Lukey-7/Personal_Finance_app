package com.pft.financetracker.ui.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import com.pft.financetracker.BuildConfig
import com.pft.financetracker.domain.ocr.OcrLine
import com.pft.financetracker.domain.ocr.OcrRows
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device text recognition using ML Kit's *bundled* Latin and Devanagari models. Both ship inside the APK,
 * so this works in airplane mode and never downloads anything. The image is decoded, read by both models at
 * once, and dropped; nothing is written to disk by this class.
 */
object OcrEngine {
    suspend fun recognize(context: Context, uri: Uri): String = withContext(Dispatchers.Default) {
        val bitmap = decode(context, uri)
        val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val devanagari = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            // Both readings in parallel: the Latin one for English and Hinglish bills, the Devanagari one for bills
            // printed in Hindi. OcrRows.pickScript keeps the Devanagari reading only when it found Hindi script.
            val (latinText, hindiText) = coroutineScope {
                val a = async { rows(read(latin, image)) }
                // A failed Hindi reading falls back to Latin only; cancellation (the user left the screen) still propagates.
                val b = async { try { rows(read(devanagari, image)) } catch (e: CancellationException) { throw e } catch (e: Exception) { "" } }
                a.await() to b.await()
            }
            val text = OcrRows.pickScript(latinText, hindiText)
            // Debug builds only: the recognised text, so parser problems on real photos can be reproduced as
            // unit tests. Release builds never log bill contents.
            if (BuildConfig.DEBUG) {
                Log.d("FinTrackOCR", "script=${if (text === hindiText && text != latinText) "devanagari" else "latin"}")
                Log.d("FinTrackOCR", "recognised:\n$text")
            }
            text
        } finally {
            latin.close()
            devanagari.close()
            bitmap.recycle()
        }
    }

    private suspend fun read(recognizer: TextRecognizer, image: InputImage): Text = suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /**
     * ML Kit groups text into blocks/lines; receipts want one printed line per output line, so
     * "TOTAL   450.00" stays on one line even when the photo is tilted (see OcrRows).
     */
    private fun rows(result: Text): String = OcrRows.merge(result.textBlocks.flatMap { it.lines }.map {
        val box = it.boundingBox
        OcrLine(it.text, box?.centerX() ?: 0, box?.centerY() ?: 0, box?.left ?: 0, box?.height() ?: 20, it.angle)
    })

    private fun decode(context: Context, uri: Uri): Bitmap {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            // Cap the longest side around 2000px: enough for receipt text, cheap enough for the recogniser.
            val maxSide = maxOf(info.size.width, info.size.height)
            if (maxSide > 2000) {
                val scale = 2000f / maxSide
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }.let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    }
}
