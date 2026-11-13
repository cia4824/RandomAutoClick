package com.example.randomautoclicker

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var etMinDelay: EditText
    private lateinit var etMaxDelay: EditText
    private lateinit var etRandomRadius: EditText
    private lateinit var cbRandomOrder: CheckBox
    private lateinit var btnToggleOverlay: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(AutoClickService.PREFS_NAME, Context.MODE_PRIVATE)

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F5F7FA"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }

        val title = TextView(this).apply {
            text = "Cấu hình Auto Click Ngẫu Nhiên"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A1A1A"))
        }
        container.addView(title)

        tvStatus = TextView(this).apply {
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
        }
        container.addView(tvStatus)

        val btnAccessibility = createButton(
            text = "1. Bật Dịch vụ Trợ năng (Accessibility)",
            bgColor = "#1976D2"
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        container.addView(btnAccessibility)

        btnToggleOverlay = createButton(
            text = "2. Hiện / Ẩn Thanh điều khiển nổi",
            bgColor = "#2E7D32"
        ) {
            val service = AutoClickService.instance
            if (service == null) {
                Toast.makeText(
                    this,
                    "Vui lòng bật Dịch vụ Trợ năng ở bước 1 trước!",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                service.toggleOverlayVisibility()
            }
        }
        container.addView(btnToggleOverlay)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#E0E0E0"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(18)
            }
        }

        card.addView(createLabel("Thời gian chờ tối thiểu (ms):"))
        etMinDelay = createNumberInput(
            prefs.getLong(AutoClickService.KEY_MIN_DELAY, 300L).toString()
        )
        card.addView(etMinDelay)

        card.addView(createLabel("Thời gian chờ tối đa (ms):"))
        etMaxDelay = createNumberInput(
            prefs.getLong(AutoClickService.KEY_MAX_DELAY, 800L).toString()
        )
        card.addView(etMaxDelay)

        card.addView(createLabel("Bán kính chạm ngẫu nhiên quanh điểm (px):\n(0 = chạm cố định, 40-150 = lệch ngẫu nhiên trong vòng tròn)"))
        etRandomRadius = createNumberInput(
            prefs.getInt(AutoClickService.KEY_RANDOM_RADIUS, 45).toString()
        )
        card.addView(etRandomRadius)

        cbRandomOrder = CheckBox(this).apply {
            text = "Chạm ngẫu nhiên giữa các điểm (khi đặt nhiều điểm)"
            isChecked = prefs.getBoolean(AutoClickService.KEY_RANDOM_ORDER, false)
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        }
        card.addView(cbRandomOrder)

        val btnSave = createButton(
            text = "Lưu & Áp dụng cài đặt",
            bgColor = "#EF6C00"
        ) {
            saveSettings()
        }
        card.addView(btnSave)

        container.addView(card)

        val note = TextView(this).apply {
            text = "Mẹo sử dụng:\n" +
                "• Dùng nút (+) trên thanh nổi để thêm nhiều điểm chạm.\n" +
                "• Kéo thả vòng tròn mục tiêu tới vị trí muốn bấm.\n" +
                "• Vòng tròn nét mờ thể hiện vùng chạm ngẫu nhiên.\n" +
                "• Nút (🎲/➡️) trên thanh nổi giúp chuyển nhanh giữa chạm ngẫu nhiên điểm hoặc tuần tự."
            textSize = 13f
            setTextColor(Color.parseColor("#555555"))
            setPadding(0, dp(16), 0, 0)
        }
        container.addView(note)

        rootScroll.addView(container)
        setContentView(rootScroll)
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun updateServiceStatus() {
        val enabled = AutoClickService.instance != null
        if (enabled) {
            tvStatus.text = "Trạng thái: ĐÃ BẬT Trợ năng (Sẵn sàng)"
            tvStatus.setTextColor(Color.parseColor("#1B5E20"))
            tvStatus.background = GradientDrawable().apply {
                setColor(Color.parseColor("#E8F5E9"))
                cornerRadius = dp(8).toFloat()
            }
        } else {
            tvStatus.text = "Trạng thái: CHƯA BẬT Trợ năng"
            tvStatus.setTextColor(Color.parseColor("#B71C1C"))
            tvStatus.background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFEBEE"))
                cornerRadius = dp(8).toFloat()
            }
        }
    }

    private fun saveSettings() {
        val minDelay = (etMinDelay.text.toString().toLongOrNull() ?: 300L).coerceAtLeast(50L)
        val maxDelay = (etMaxDelay.text.toString().toLongOrNull() ?: 800L).coerceAtLeast(minDelay)
        val radius = (etRandomRadius.text.toString().toIntOrNull() ?: 45).coerceIn(0, 500)
        val randomOrder = cbRandomOrder.isChecked

        etMinDelay.setText(minDelay.toString())
        etMaxDelay.setText(maxDelay.toString())
        etRandomRadius.setText(radius.toString())

        getSharedPreferences(AutoClickService.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(AutoClickService.KEY_MIN_DELAY, minDelay)
            .putLong(AutoClickService.KEY_MAX_DELAY, maxDelay)
            .putInt(AutoClickService.KEY_RANDOM_RADIUS, radius)
            .putBoolean(AutoClickService.KEY_RANDOM_ORDER, randomOrder)
            .apply()

        AutoClickService.instance?.reloadSettings()
        Toast.makeText(this, "Đã lưu cài đặt!", Toast.LENGTH_SHORT).show()
    }

    private fun createLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        }
    }

    private fun createNumberInput(initial: String): EditText {
        return EditText(this).apply {
            setText(initial)
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun createButton(text: String, bgColor: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            isAllCaps = false
            textSize = 15f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
