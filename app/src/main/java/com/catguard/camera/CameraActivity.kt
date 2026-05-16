package com.catguard.camera

import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.catguard.databinding.ActivityCameraBinding
import com.catguard.ml.CatDetector
import com.catguard.network.StreamServer
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var b: ActivityCameraBinding
    private lateinit var detector: CatDetector
    private lateinit var server: StreamServer
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile private var lastBitmap: android.graphics.Bitmap? = null
    private var lastStatus = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(b.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val ip = getLocalIp()
        b.tvCode.text   = "IP: $ip:8765 | Глядачів: 0"
        b.tvStatus.text = "Запуск…"

        // WebSocket сервер на порту 8765
        server = StreamServer(8765)
        server.onClientCount = { count ->
            runOnUiThread {
                b.tvCode.text = "IP: $ip:8765 | Глядачів: $count"
            }
        }
        server.start()

        // TFLite
        detector = CatDetector(this)
        lifecycleScope.launch(Dispatchers.IO) {
            detector.init()
            withContext(Dispatchers.Main) { b.tvStatus.text = "Готово. Сканую…" }
        }

        startCamera()
        startDetectionLoop()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()   // ← зберігаємо future і лише тут викликаємо .get()

            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(b.previewView.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analysis.setAnalyzer(executor) { proxy ->
                lastBitmap = proxy.toBitmap()
                proxy.close()
            }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                b.tvStatus.text = "Помилка камери: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startDetectionLoop() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)
                val bmp = lastBitmap ?: continue
                val catFound = detector.hasCat(bmp)
                val status   = if (catFound) "OBJECT_DETECTED" else "OBJECT_LOST"

                if (status != lastStatus) {
                    lastStatus = status
                    server.broadcast(status)
                }

                withContext(Dispatchers.Main) {
                    if (catFound) {
                        b.tvStatus.text = "🐱 КІТ ЗНАЙДЕНИЙ"
                        b.tvStatus.setBackgroundColor(0xFF1B5E20.toInt())
                    } else {
                        b.tvStatus.text = "👁 Сканування… кота немає"
                        b.tvStatus.setBackgroundColor(0xFF333333.toInt())
                    }
                }
            }
        }
    }

    private fun getLocalIp(): String {
        return try {
            @Suppress("DEPRECATION")
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ip = wm.connectionInfo.ipAddress
            "%d.%d.%d.%d".format(
                ip and 0xff,
                ip shr 8  and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff
            )
        } catch (_: Exception) { "???" }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        detector.close()
        try { server.stop(500) } catch (_: Exception) {}
    }
}
