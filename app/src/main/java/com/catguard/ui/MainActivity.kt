package com.catguard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.catguard.camera.CameraActivity
import com.catguard.databinding.ActivityMainBinding
import com.catguard.viewer.ViewerActivity

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var pendingMode = ""

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) launch()
        else Toast.makeText(this, "Потрібні дозволи для роботи", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnCamera.setOnClickListener {
            pendingMode = "camera"
            checkAndLaunch()
        }
        b.btnViewer.setOnClickListener {
            pendingMode = "viewer"
            checkAndLaunch()
        }
    }

    private fun checkAndLaunch() {
        // Viewer Mode потребує лише INTERNET (без камери)
        val needed = if (pendingMode == "camera")
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.INTERNET)
        else
            arrayOf(Manifest.permission.INTERNET)

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) launch() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun launch() {
        val cls = if (pendingMode == "camera") CameraActivity::class.java else ViewerActivity::class.java
        startActivity(Intent(this, cls))
    }
}
