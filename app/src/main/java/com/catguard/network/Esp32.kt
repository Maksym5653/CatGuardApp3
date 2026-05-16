package com.catguard.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object Esp32 {

    private const val BASE = "http://192.168.4.1"
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private var lastState = ""   // уникаємо дублів запитів

    suspend fun alarmOn() = send("on")
    suspend fun alarmOff() = send("off")

    private suspend fun send(state: String) {
        if (lastState == state) return   // вже в цьому стані
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE/toggle_alarm?state=$state"
                val req = Request.Builder().url(url).build()
                val resp = http.newCall(req).execute()
                lastState = state
                Log.i("ESP32", "Відповідь: ${resp.code} | alarm=$state")
            } catch (e: Exception) {
                Log.e("ESP32", "Помилка: ${e.message}")
            }
        }
    }

    fun reset() { lastState = "" }
}
