package com.karem.autoclicker

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var etInterval: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        etInterval = findViewById(R.id.etInterval)

        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        etInterval.setText(prefs.getInt(Prefs.INTERVAL_MS, 50).toString())

        findViewById<Button>(R.id.btnGrantOverlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Разрешение уже выдано", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnGrantAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val interval = etInterval.text.toString().toIntOrNull() ?: 50
            if (interval < 1) {
                Toast.makeText(this, "Интервал должен быть больше 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Сначала дай разрешение на оверлей", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Сначала включи службу спец. возможностей", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit()
                .putInt(Prefs.INTERVAL_MS, interval)
                .putString(Prefs.LAST_ERROR, "")
                .apply()
            startService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "АвтоКликер запущен, сверни приложение", Toast.LENGTH_SHORT).show()

            Handler(Looper.getMainLooper()).postDelayed({
                val err = prefs.getString(Prefs.LAST_ERROR, "")
                if (!err.isNullOrEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Ошибка запуска службы")
                        .setMessage(err)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }, 1000)
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        tvOverlayStatus.text = if (Settings.canDrawOverlays(this))
            "1. Разрешение на оверлей: выдано ✅"
        else
            "1. Разрешение на оверлей: не выдано"

        tvAccessibilityStatus.text = if (isAccessibilityServiceEnabled())
            "2. Служба спец. возможностей: включена ✅"
        else
            "2. Служба спец. возможностей: выключена"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${ClickAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }
}
