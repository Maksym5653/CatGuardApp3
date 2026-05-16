package com.catguard.viewer

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.catguard.databinding.ActivityViewerBinding
import com.catguard.network.Esp32
import com.catguard.network.StreamClient
import kotlinx.coroutines.*
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class ViewerActivity : AppCompatActivity() {

    private var _b: ActivityViewerBinding? = null
    private val b get() = _b!!

    private val statuses = ConcurrentHashMap<String, String>()
    private val clients  = ConcurrentHashMap<String, StreamClient>()

    private var alarmOn  = false
    private var alarmJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _b = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(b.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        b.btnAdd.setOnClickListener        { showAddDialog() }
        b.btnDisconnect.setOnClickListener { disconnectAll() }
        b.btnTest.setOnClickListener {
            lifecycleScope.launch {
                try {
                    Esp32.alarmOn()
                    safeUi {
                        b.tvAlarm.text = "🔴 ТЕСТ ТРИВОГИ"
                        b.tvAlarm.setBackgroundColor(0xFFFF1744.toInt())
                    }
                    delay(3000)
                    Esp32.alarmOff()
                    safeUi { updateUI() }
                } catch (_: Exception) {}
            }
        }

        updateUI()
    }

    // ─── Безпечний UI-апдейт ────────────────────────────────────────────────
    private fun safeUi(action: () -> Unit) {
        if (!isDestroyed && !isFinishing) runOnUiThread {
            if (!isDestroyed) action()
        }
    }

    // ─── Діалог додавання камери ─────────────────────────────────────────────
    private fun showAddDialog() {
        if (clients.size >= 2) {
            Toast.makeText(this, "Максимум 2 камери", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply {
            hint = "IP камери (напр.: 192.168.1.55)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(60, 30, 60, 30)
        }
        AlertDialog.Builder(this)
            .setTitle("Підключити камеру")
            .setMessage("Введіть IP телефону-камери.\nIP показано у Camera Mode.")
            .setView(input)
            .setPositiveButton("Підключити") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotEmpty()) connectCamera(ip)
                else Toast.makeText(this, "Введіть IP", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    // ─── Підключення ─────────────────────────────────────────────────────────
    private fun connectCamera(ip: String) {
        if (clients.containsKey(ip)) {
            Toast.makeText(this, "Вже підключено до $ip", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = try { URI("ws://$ip:8765") } catch (e: Exception) {
            Toast.makeText(this, "Невірна адреса: $ip", Toast.LENGTH_SHORT).show()
            return
        }
        val client = StreamClient(
            serverUri    = uri,
            cameraId     = ip,
            onStatus     = ::onCameraStatus,
            onConnect    = { camId ->
                statuses[camId] = "OBJECT_DETECTED"
                safeUi { updateUI() }
            },
            onDisconnect = { camId ->
                clients.remove(camId)
                statuses.remove(camId)
                safeUi {
                    updateUI()
                    Toast.makeText(this, "Камера $camId відключилась", Toast.LENGTH_SHORT).show()
                }
            }
        )
        clients[ip] = client
        try {
            client.connect()
            Toast.makeText(this, "Підключаюсь до $ip…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            clients.remove(ip)
            Toast.makeText(this, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Обробка статусу ─────────────────────────────────────────────────────
    private fun onCameraStatus(cameraId: String, status: String) {
        statuses[cameraId] = status
        val anyFound = statuses.values.any  { it == "OBJECT_DETECTED" }
        val allLost  = statuses.isNotEmpty() && statuses.values.all { it == "OBJECT_LOST" }

        when {
            anyFound -> {
                alarmJob?.cancel()
                if (alarmOn) {
                    alarmOn = false
                    lifecycleScope.launch { try { Esp32.alarmOff() } catch (_: Exception) {} }
                }
            }
            allLost  -> {
                alarmJob?.cancel()
                alarmJob = lifecycleScope.launch {
                    delay(2000)
                    if (!isActive) return@launch
                    if (statuses.values.all { it == "OBJECT_LOST" } && !alarmOn) {
                        alarmOn = true
                        try { Esp32.alarmOn() } catch (_: Exception) {}
                    }
                }
            }
        }
        safeUi { updateUI() }
    }

    // ─── Оновлення UI ────────────────────────────────────────────────────────
    private fun updateUI() {
        val entries = clients.entries.toList()
        val count   = entries.size

        // Відображення панелей камер
        when (count) {
            0 -> {
                b.videoView1.visibility = View.GONE
                b.videoView2.visibility = View.GONE
                b.divider.visibility    = View.GONE
                b.tvEmpty.visibility    = View.VISIBLE
            }
            1 -> {
                b.videoView1.visibility = View.VISIBLE
                b.videoView2.visibility = View.GONE
                b.divider.visibility    = View.GONE
                b.tvEmpty.visibility    = View.GONE
                bindCamera(entries[0].key, b.tvCamera1Label, b.tvCamera1Status, 1)
                clearCamera(b.tvCamera2Label, b.tvCamera2Status)
            }
            else -> {
                b.videoView1.visibility = View.VISIBLE
                b.videoView2.visibility = View.VISIBLE
                b.divider.visibility    = View.VISIBLE
                b.tvEmpty.visibility    = View.GONE
                bindCamera(entries[0].key, b.tvCamera1Label, b.tvCamera1Status, 1)
                bindCamera(entries[1].key, b.tvCamera2Label, b.tvCamera2Status, 2)
            }
        }

        // Статус-бар
        when {
            alarmOn -> {
                b.tvAlarm.text = "🚨 ТРИВОГА! Кіт зник з усіх камер!"
                b.tvAlarm.setBackgroundColor(0xFFFF1744.toInt())
            }
            count == 0 -> {
                b.tvAlarm.text = "Немає підключених камер"
                b.tvAlarm.setBackgroundColor(0xFF1A1A2E.toInt())
            }
            else -> {
                val allOk = statuses.values.all { it == "OBJECT_DETECTED" }
                b.tvAlarm.text = if (allOk) "✅ Кіт під наглядом" else "⚠️ Кіт не виявлений!"
                b.tvAlarm.setBackgroundColor(if (allOk) 0xFF1B5E20.toInt() else 0xFFE65100.toInt())
            }
        }
    }

    private fun bindCamera(
        ip: String,
        label: android.widget.TextView,
        statusView: android.widget.TextView,
        num: Int
    ) {
        val status = statuses[ip] ?: "—"
        val found  = status == "OBJECT_DETECTED"
        label.text = "📷 Камера $num  ($ip)"
        statusView.text = if (found) "🐱 Кіт знайдений" else "👁 Сканування…"
        statusView.setBackgroundColor(if (found) 0xFF1B5E20.toInt() else 0xFF37474F.toInt())
        label.visibility      = View.VISIBLE
        statusView.visibility = View.VISIBLE
    }

    private fun clearCamera(
        label: android.widget.TextView,
        statusView: android.widget.TextView
    ) {
        label.text      = ""
        statusView.text = ""
        label.visibility      = View.GONE
        statusView.visibility = View.GONE
    }

    // ─── Відключення ─────────────────────────────────────────────────────────
    private fun disconnectAll() {
        clients.values.forEach { try { it.close() } catch (_: Exception) {} }
        clients.clear()
        statuses.clear()
        alarmJob?.cancel()
        if (alarmOn) {
            alarmOn = false
            lifecycleScope.launch { try { Esp32.alarmOff() } catch (_: Exception) {} }
        }
        updateUI()
    }

    override fun onDestroy() {
        _b = null          // звільняємо binding ДО super щоб callbacks нічого не чіпали
        super.onDestroy()
        clients.values.forEach { try { it.close() } catch (_: Exception) {} }
        clients.clear()
        statuses.clear()
        alarmJob?.cancel()
        Esp32.reset()
    }
}
