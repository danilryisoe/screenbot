package com.example.screenbot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusView = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 36)
        }

        val btnOverlay = Button(this).apply {
            text = "1. Разрешить плавающее окно (HUD)"
            setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        val btnAccessibility = Button(this).apply {
            text = "2. Включить ScreenBot в спец. возможностях"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(statusView)
            addView(btnOverlay)
            addView(btnAccessibility)
        }

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasService = BotAccessibilityService.instance != null
        statusView.text = buildString {
            appendLine("Плавающий HUD: ${if (hasOverlay) "ГОТОВ" else "НЕТ ПРАВ"}")
            append("Сервис кликов/зрения: ${if (hasService) "ВКЛЮЧЁН" else "ВЫКЛЮЧЕН"}")
        }
    }
}

