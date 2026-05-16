package com.catguard.network

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

/**
 * Запускається на пристрої-камері.
 * Порт: 8765
 * Глядач підключається по IP телефону-камери: ws://192.168.x.x:8765
 *
 * Протокол:
 *   Камера → глядач:  "STATUS:OBJECT_DETECTED" або "STATUS:OBJECT_LOST"
 *   Глядач → камера:  "PING" (щоб перевірити зв'язок)
 */
class StreamServer(port: Int = 8765) : WebSocketServer(InetSocketAddress(port)) {

    var onClientCount: ((Int) -> Unit)? = null
    private val clients = mutableSetOf<WebSocket>()

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        clients.add(conn)
        Log.i("StreamServer", "Підключився глядач: ${conn.remoteSocketAddress}")
        onClientCount?.invoke(clients.size)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        clients.remove(conn)
        Log.i("StreamServer", "Відключився глядач")
        onClientCount?.invoke(clients.size)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        Log.d("StreamServer", "Повідомлення від глядача: $message")
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e("StreamServer", "Помилка: ${ex.message}")
    }

    override fun onStart() {
        Log.i("StreamServer", "Сервер запущено на порту ${port}")
    }

    /** Відправити статус усім підключеним глядачам */
    override fun broadcast(status: String) {
        val msg = "STATUS:$status"
        clients.forEach { if (it.isOpen) it.send(msg) }
    }

    fun clientCount() = clients.size
}
