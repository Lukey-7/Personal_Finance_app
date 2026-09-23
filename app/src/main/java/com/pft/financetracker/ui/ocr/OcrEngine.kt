package com.pft.financetracker.ui.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device text recognition using ML Kit's *bundled* Latin model. The model ships inside the APK, so this
 * works in airplane mode and never downloads anything. The image is decoded, recognised, and dropped;
 * nothing is written to disk by this class.
 */
object OcrEngine {
    suspend fun recognize(context: Context, uri: Uri): String = withContext(Dispatchers.Default) {
        val bitmap = decode(context, uri)
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                suspendCancellableCoroutine { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { result ->
                            // ML Kit groups text into blocks/lines; receipts want one printed line per output line,
                            // sorted top-to-bottom so "TOTAL   450.00" stays on one line.
                            val lines = result.textBlocks.flatMap { it.lines }
                                .sortedWith(compareBy({ it.boundingBox?.centerY() ?: 0 }, { it.boundingBox?.left ?: 0 }))
                            cont.resume(mergeRows(lines.map { Row(it.text, it.boundingBox?.centerY() ?: 0, it.boundingBox?.left ?: 0, it.boundingBox?.height() ?: 20) }))
                        }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
            } finally {
                recognizer.close()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private data class Row(val text: String, val cy: Int, val left: Int, val h: Int)

    /** Lines whose vertical centres are within half a line height are the same printed row (name ... price). */
    private fun mergeRows(rows: List<Row>): String {
        val out = mutableListOf<MutableList<Row>>()
        for (r in rows) {
            val last = out.lastOrNull()
            if (last != null && kotlin.math.abs(last.first().cy - r.cy) <= maxOf(8, last.first().h / 2)) last += r else out += mutableListOf(r)
        }
        return out.joinToString("\n") { row -> row.sortedBy { it.left }.joinToString("  ") { it.text } }
    }

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

    @Suppress("unused")
    private fun decodeLegacy(context: Context, uri: Uri): Bitmap =
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
}
