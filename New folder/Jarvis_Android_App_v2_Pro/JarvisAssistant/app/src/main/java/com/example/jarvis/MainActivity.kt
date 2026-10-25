package com.example.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnSaveKey = findViewById<Button>(R.id.btnSaveKey)
        val btnBattery = findViewById<Button>(R.id.btnBattery)
        val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)
        val btnStartInvisible = findViewById<Button>(R.id.btnStartInvisible)
        val btnStopJarvis = findViewById<Button>(R.id.btnStopJarvis)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        val prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        etApiKey.setText(prefs.getString("gemini_api_key", ""))

        requestRequiredPermissions()

        btnSaveKey.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            prefs.edit().putString("gemini_api_key", key).apply()
            Toast.makeText(this, "API Key सेव्ह झाली!", Toast.LENGTH_SHORT).show()
        }

        btnBattery.setOnClickListener {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "बॅटरी परवानगी आधीच ऑन आहे!", Toast.LENGTH_SHORT).show()
            }
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "'Jarvis Invisible Controller' ऑन करा", Toast.LENGTH_LONG).show()
        }

        btnStartInvisible.setOnClickListener {
            val serviceIntent = Intent(this, JarvisVoiceService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "Jarvis आता अदृश्य मोडमध्ये बॅकग्राऊंडला सुरू झाला आहे!", Toast.LENGTH_LONG).show()
            // अ‍ॅप बंद करून पूर्णपणे बॅकग्राऊंडमध्ये (Invisible) पाठवा
            moveTaskToBack(true)
        }

        btnStopJarvis.setOnClickListener {
            stopService(Intent(this, JarvisVoiceService::class.java))
            tvStatus.text = "स्टेटस: जार्विस बंद केला आहे."
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS
        )
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 101)
        }
    }
}
