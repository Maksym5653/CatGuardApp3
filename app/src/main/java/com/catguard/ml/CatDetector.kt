package com.catguard.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector

/**
 * Використовує TFLite Task Vision API.
 * Модель: efficientdet_lite0.tflite (COCO 80 класів, включаючи "cat")
 * Покласти в: app/src/main/assets/efficientdet_lite0.tflite
 * Скачати: https://tfhub.dev/tensorflow/lite-model/efficientdet/lite0/detection/metadata/1
 */
class CatDetector(private val ctx: Context) {

    private var detector: ObjectDetector? = null

    fun init() {
        try {
            val opts = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(5)
                .setScoreThreshold(0.4f)
                .build()
            detector = ObjectDetector.createFromFileAndOptions(ctx, "efficientdet_lite0.tflite", opts)
            Log.i("CatDetector", "Модель завантажена")
        } catch (e: Exception) {
            Log.e("CatDetector", "Помилка завантаження: ${e.message}")
        }
    }

    fun hasCat(bitmap: Bitmap): Boolean {
        val det = detector ?: return false
        return try {
            val img = TensorImage.fromBitmap(bitmap)
            val results = det.detect(img)
            results.any { d ->
                d.categories.any { c ->
                    c.label.lowercase().contains("cat") && c.score >= 0.4f
                }
            }
        } catch (e: Exception) {
            Log.e("CatDetector", "Помилка виявлення: ${e.message}")
            false
        }
    }

    fun close() = detector?.close()
}
