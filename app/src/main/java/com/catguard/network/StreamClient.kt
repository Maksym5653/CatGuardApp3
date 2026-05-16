package com.catguard.network

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Запускається на пристрої-глядачі.
 * Підключається до ws://IP_КАМЕРИ:8765
 *
 * Виправлено:
 *   - AtomicBoolean disconnected — виклик onDisconnect лише ОДИН раз
 *     (раніше onError + onClose обидва викликали onDisconnect)
 */
class StreamClient(
    serverUri: URI,
    val cameraId: String,
    private val onStatus:     (cameraId: String, status: String) -> Unit,
    private val onConnect:    (cameraId: String) -> Unit,
    private val onDisconnect: (cameraId: String) -> Unit
) : WebSocketClient(serverUri) {

    private val disconnected = AtomicBoolean(false)

    override fun onOpen(handshake: ServerHandshake) {
        disconnected.set(false)
        Log.i("StreamClient", "Підключено до $cameraId")
        onConnect(cameraId)
    }

    override fun onMessage(message: String) {
        if (message.startsWith("STATUS:")) {
            val status = message.removePrefix("STATUS:")
            Log.d("StreamClient", "$cameraId → $status")
            onStatus(cameraId, status)
        }
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        Log.w("StreamClient", "$cameraId закрито (code=$code)")
        if (disconnected.compareAndSet(false, true)) {
            onDisconnect(cameraId)
        }
    }

    override fun onError(ex: Exception) {
        Log.e("StreamClient", "$cameraId помилка: ${ex.message}")
        // НЕ викликаємо onDisconnect тут — onClose прийде автоматично після помилки
    }
}
