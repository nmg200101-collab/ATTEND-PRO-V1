package com.attendpro.core

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object UiKit {
    enum class Preset(val id: String, val title: String, val description: String) {
        PROFESSIONAL("professional", "Professional Blue — الأزرق الاحترافي", "متوازن وواضح للاستخدام اليومي"),
        EMERALD("emerald", "Calm Green — الأخضر الهادئ", "ألوان هادئة وبطاقات ناعمة"),
        SAND("sand", "Warm Sand — الرملي الدافئ", "مريح للعين ومساحات أوسع"),
        VIOLET("violet", "Modern Purple — البنفسجي العصري", "مظهر حديث وتباين واضح"),
        GRAPHITE("graphite", "Graphite — الجرافيت", "رصين وعملي للواجهات الإدارية"),
        MINIMAL("minimal", "Minimal — البسيط", "زوايا أقل وحركة بصرية أقل"),
        MIDNIGHT("midnight", "Midnight — منتصف الليل", "تباين عالٍ وواجهة عصرية هادئة"),
        LIGHT_BUSINESS("light_business", "Light Business — الأعمال الفاتح", "واجهة أعمال نظيفة ومساحات مريحة")
    }

    enum class LayoutMode(val id: String, val title: String, val description: String) {
        ORGANIZED("organized", "لوحة منظمة", "توازن بين البطاقات والاختصارات للاستخدام اليومي"),
        COMPACT("compact", "قائمة مضغوطة", "صفوف قصيرة وواضحة للشاشات الصغيرة والوصول السريع"),
        LARGE("large", "بطاقات كبيرة", "عناصر أكبر وشرح أوضح للمستخدم الذي يفضّل اللمس المريح")
    }

    data class Palette(
        val bg: Int,
        val surface: Int,
        val surface2: Int,
        val text: Int,
        val muted: Int,
        val primary: Int,
        val accent: Int,
        val success: Int,
        val danger: Int,
        val divider: Int,
        val cardRadius: Int,
        val buttonRadius: Int,
        val heroRadius: Int,
        val cardElevation: Int,
        val buttonHeight: Int,
        val cardPadding: Int,
        val preset: Preset
    )

    private const val PREFS = "attend_pro_appearance"
    private const val KEY_PRESET = "ui_preset"
    private const val KEY_LAYOUT = "ui_layout_mode"

    fun currentPreset(context: Context): Preset {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PRESET, Preset.PROFESSIONAL.id)
        // Preserve the two experimental 1.9.39 ids without losing the user's appearance choice.
        if (id == "ocean") return Preset.MIDNIGHT
        if (id == "copper") return Preset.LIGHT_BUSINESS
        return Preset.entries.firstOrNull { it.id == id } ?: Preset.PROFESSIONAL
    }

    fun setPreset(context: Context, preset: Preset) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PRESET, preset.id).apply()
    }

    fun currentLayout(context: Context): LayoutMode {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAYOUT, LayoutMode.ORGANIZED.id)
        return LayoutMode.entries.firstOrNull { it.id == id } ?: LayoutMode.ORGANIZED
    }

    fun setLayout(context: Context, mode: LayoutMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAYOUT, mode.id).apply()
    }

    fun appearanceSummary(context: Context): String = "${currentPreset(context).title} • ${currentLayout(context).title}"

    fun showAppearancePicker(activity: Activity, onChanged: (() -> Unit)? = null) {
        val items = arrayOf(
            "الألوان والثيمات\n${currentPreset(activity).title}",
            "ترتيب الواجهة\n${currentLayout(activity).title}",
            "الوضع الليلي والنهاري\nيتبع إعداد الهاتف تلقائيًا"
        )
        AlertDialog.Builder(activity)
            .setTitle("المظهر وطريقة العرض")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showThemePicker(activity, onChanged)
                    1 -> showLayoutPicker(activity, onChanged)
                    else -> AlertDialog.Builder(activity)
                        .setTitle("الوضع الليلي والنهاري")
                        .setMessage("يتبع ATTEND PRO إعداد الوضع الليلي أو النهاري في الهاتف تلقائيًا حتى تبقى الألوان متناسقة مع النظام.")
                        .setPositiveButton("حسنًا", null)
                        .show()
                }
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showThemePicker(activity: Activity, onChanged: (() -> Unit)?) {
        val current = currentPreset(activity)
        val labels = Preset.entries.map { preset ->
            val selected = if (preset == current) "✓ " else ""
            "$selected${preset.title}\n${preset.description}"
        }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("الألوان والثيمات")
            .setSingleChoiceItems(labels, Preset.entries.indexOf(current)) { dialog, which ->
                setPreset(activity, Preset.entries[which])
                dialog.dismiss()
                onChanged?.invoke() ?: activity.recreate()
            }
            .setNegativeButton("رجوع", null)
            .show()
    }

    private fun showLayoutPicker(activity: Activity, onChanged: (() -> Unit)?) {
        val current = currentLayout(activity)
        val labels = LayoutMode.entries.map { mode ->
            val selected = if (mode == current) "✓ " else ""
            "$selected${mode.title}\n${mode.description}"
        }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("ترتيب الواجهة")
            .setSingleChoiceItems(labels, LayoutMode.entries.indexOf(current)) { dialog, which ->
                setLayout(activity, LayoutMode.entries[which])
                dialog.dismiss()
                onChanged?.invoke() ?: activity.recreate()
            }
            .setNegativeButton("رجوع", null)
            .show()
    }

    fun palette(context: Context): Palette {
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return paletteFor(currentPreset(context), dark)
    }

    private fun paletteFor(preset: Preset, dark: Boolean): Palette {
        val success = if (dark) Color.rgb(61, 199, 132) else Color.rgb(31, 151, 91)
        val danger = if (dark) Color.rgb(244, 105, 105) else Color.rgb(203, 59, 64)
        return when (preset) {
            Preset.PROFESSIONAL -> if (dark) Palette(
                Color.rgb(5, 17, 28), Color.rgb(11, 31, 47), Color.rgb(19, 45, 64),
                Color.rgb(242, 248, 252), Color.rgb(174, 196, 209), Color.rgb(20, 124, 205), Color.rgb(27, 176, 169),
                success, danger, Color.rgb(37, 65, 84), 20, 15, 24, 2, 52, 16, preset
            ) else Palette(
                Color.rgb(244, 248, 252), Color.WHITE, Color.rgb(236, 243, 248),
                Color.rgb(18, 39, 54), Color.rgb(89, 108, 121), Color.rgb(13, 111, 187), Color.rgb(18, 164, 157),
                success, danger, Color.rgb(220, 231, 238), 20, 15, 24, 2, 52, 16, preset
            )
            Preset.EMERALD -> if (dark) Palette(
                Color.rgb(7, 22, 20), Color.rgb(12, 38, 33), Color.rgb(20, 53, 46),
                Color.rgb(240, 249, 246), Color.rgb(171, 201, 192), Color.rgb(28, 139, 103), Color.rgb(70, 188, 145),
                success, danger, Color.rgb(39, 75, 65), 24, 18, 28, 1, 54, 17, preset
            ) else Palette(
                Color.rgb(245, 250, 247), Color.WHITE, Color.rgb(233, 244, 238),
                Color.rgb(27, 52, 43), Color.rgb(91, 119, 107), Color.rgb(31, 132, 96), Color.rgb(78, 169, 133),
                success, danger, Color.rgb(214, 232, 221), 24, 18, 28, 1, 54, 17, preset
            )
            Preset.SAND -> if (dark) Palette(
                Color.rgb(26, 22, 17), Color.rgb(43, 36, 27), Color.rgb(58, 49, 37),
                Color.rgb(250, 246, 238), Color.rgb(205, 192, 169), Color.rgb(173, 118, 52), Color.rgb(208, 156, 82),
                success, danger, Color.rgb(80, 67, 49), 26, 20, 30, 1, 54, 18, preset
            ) else Palette(
                Color.rgb(250, 247, 240), Color.rgb(255, 253, 248), Color.rgb(244, 237, 224),
                Color.rgb(54, 45, 33), Color.rgb(121, 105, 82), Color.rgb(171, 112, 43), Color.rgb(203, 150, 74),
                success, danger, Color.rgb(231, 221, 203), 26, 20, 30, 1, 54, 18, preset
            )
            Preset.VIOLET -> if (dark) Palette(
                Color.rgb(18, 15, 31), Color.rgb(31, 25, 49), Color.rgb(45, 37, 67),
                Color.rgb(248, 245, 253), Color.rgb(195, 183, 216), Color.rgb(111, 79, 190), Color.rgb(157, 101, 214),
                success, danger, Color.rgb(66, 54, 91), 22, 18, 27, 2, 52, 16, preset
            ) else Palette(
                Color.rgb(248, 246, 252), Color.WHITE, Color.rgb(240, 235, 248),
                Color.rgb(45, 36, 61), Color.rgb(105, 91, 126), Color.rgb(101, 73, 180), Color.rgb(148, 91, 201),
                success, danger, Color.rgb(225, 216, 238), 22, 18, 27, 2, 52, 16, preset
            )
            Preset.GRAPHITE -> if (dark) Palette(
                Color.rgb(14, 16, 18), Color.rgb(27, 30, 33), Color.rgb(39, 43, 47),
                Color.rgb(245, 247, 248), Color.rgb(183, 190, 196), Color.rgb(76, 139, 173), Color.rgb(85, 168, 162),
                success, danger, Color.rgb(56, 61, 66), 16, 13, 20, 1, 50, 15, preset
            ) else Palette(
                Color.rgb(246, 247, 248), Color.WHITE, Color.rgb(237, 239, 241),
                Color.rgb(34, 38, 42), Color.rgb(101, 108, 114), Color.rgb(65, 124, 156), Color.rgb(64, 148, 142),
                success, danger, Color.rgb(220, 224, 227), 16, 13, 20, 1, 50, 15, preset
            )
            Preset.MIDNIGHT -> if (dark) Palette(
                Color.rgb(6, 22, 28), Color.rgb(12, 37, 45), Color.rgb(19, 52, 61),
                Color.rgb(240, 249, 251), Color.rgb(170, 200, 207), Color.rgb(21, 128, 151), Color.rgb(35, 174, 154),
                success, danger, Color.rgb(38, 75, 84), 22, 17, 27, 2, 52, 16, preset
            ) else Palette(
                Color.rgb(243, 250, 251), Color.WHITE, Color.rgb(232, 244, 246),
                Color.rgb(24, 48, 55), Color.rgb(88, 117, 123), Color.rgb(20, 121, 145), Color.rgb(30, 160, 142),
                success, danger, Color.rgb(211, 232, 236), 22, 17, 27, 2, 52, 16, preset
            )
            Preset.LIGHT_BUSINESS -> if (dark) Palette(
                Color.rgb(25, 18, 15), Color.rgb(43, 31, 25), Color.rgb(58, 42, 34),
                Color.rgb(250, 246, 242), Color.rgb(205, 188, 176), Color.rgb(173, 93, 48), Color.rgb(202, 132, 72),
                success, danger, Color.rgb(80, 58, 47), 24, 18, 29, 2, 54, 17, preset
            ) else Palette(
                Color.rgb(251, 247, 244), Color.WHITE, Color.rgb(245, 237, 231),
                Color.rgb(57, 40, 32), Color.rgb(124, 99, 85), Color.rgb(167, 86, 44), Color.rgb(197, 126, 67),
                success, danger, Color.rgb(232, 216, 205), 24, 18, 29, 2, 54, 17, preset
            )
            Preset.MINIMAL -> if (dark) Palette(
                Color.rgb(12, 13, 14), Color.rgb(23, 24, 26), Color.rgb(32, 34, 36),
                Color.rgb(246, 246, 246), Color.rgb(174, 177, 180), Color.rgb(55, 127, 149), Color.rgb(73, 155, 143),
                success, danger, Color.rgb(48, 50, 53), 10, 10, 14, 0, 48, 14, preset
            ) else Palette(
                Color.rgb(249, 249, 249), Color.WHITE, Color.rgb(242, 243, 244),
                Color.rgb(28, 30, 32), Color.rgb(100, 104, 108), Color.rgb(52, 118, 141), Color.rgb(65, 146, 134),
                success, danger, Color.rgb(226, 228, 230), 10, 10, 14, 0, 48, 14, preset
            )
        }
    }

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun round(color: Int, radius: Int, context: Context, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(context, radius).toFloat()
        if (stroke != null) setStroke(dp(context, 1), stroke)
    }

    private fun ripple(context: Context, normal: android.graphics.drawable.Drawable, rippleColor: Int): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(rippleColor), normal, null)

    fun card(context: Context, palette: Palette, padding: Int = palette.cardPadding): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, padding), dp(context, padding), dp(context, padding), dp(context, padding))
        background = round(palette.surface, palette.cardRadius, context, palette.divider)
        elevation = dp(context, palette.cardElevation).toFloat()
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(context, if (palette.preset == Preset.MINIMAL) 9 else 12)
        }
    }

    fun makeInteractive(view: View, context: Context, palette: Palette) {
        val base = view.background ?: round(palette.surface, palette.cardRadius, context, palette.divider)
        view.background = ripple(context, base, Color.argb(28, Color.red(palette.primary), Color.green(palette.primary), Color.blue(palette.primary)))
        view.isClickable = true
        view.isFocusable = true
    }

    fun heroCard(context: Context, palette: Palette, padding: Int = 20): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(context, padding), dp(context, padding), dp(context, padding), dp(context, padding))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(palette.primary, palette.accent)).apply {
            cornerRadius = dp(context, palette.heroRadius).toFloat()
        }
        elevation = dp(context, if (palette.cardElevation == 0) 0 else palette.cardElevation + 2).toFloat()
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(context, 14)
        }
    }

    fun title(context: Context, palette: Palette, text: String, size: Float = 21f): TextView = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(palette.text)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.START
        includeFontPadding = false
        letterSpacing = 0.005f
    }

    fun subtitle(context: Context, palette: Palette, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 14.2f
        setTextColor(palette.muted)
        gravity = Gravity.START
        setLineSpacing(dp(context, 1).toFloat(), 1.14f)
        includeFontPadding = false
    }

    fun field(context: Context, palette: Palette, hintText: String, numeric: Boolean = false): EditText = EditText(context).apply {
        hint = hintText
        textSize = 15.5f
        setTextColor(palette.text)
        setHintTextColor(palette.muted)
        background = round(palette.surface2, palette.buttonRadius, context, palette.divider)
        setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
        inputType = if (numeric) android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(context, 10)
        }
    }

    fun iconFor(label: String): String = when {
        label.contains("وجه") -> "◉"
        label.contains("صوت") || label.contains("ميكروفون") -> "◖"
        label.contains("كلمة مرور") || label.contains("رمز حماية") -> "▣"
        label.contains("بصمة") -> "◎"
        label.contains("QR") || label.contains("كيو") -> "▦"
        label.contains("موظف") -> "♙"
        label.contains("تقرير") -> "▤"
        label.contains("اتصال") || label.contains("Bluetooth") || label.contains("Wi‑Fi") -> "⌁"
        label.contains("تحديث") || label.contains("إعادة") || label.contains("مزامنة") -> "↻"
        label.contains("إعداد") || label.contains("صيانة") -> "⚙"
        label.contains("مالك") || label.contains("حماية") -> "◆"
        label.contains("حضور") -> "✓"
        label.contains("انصراف") -> "↗"
        label.contains("تاريخ") || label.contains("دوام") -> "◷"
        else -> ""
    }

    private fun decorated(label: String): String {
        if (label.isBlank() || label.firstOrNull()?.let { !it.isLetterOrDigit() } == true) return label
        val icon = iconFor(label)
        return if (icon.isBlank()) label else "$icon  $label"
    }

    fun button(context: Context, palette: Palette, label: String, primary: Boolean = true): Button = Button(context).apply {
        text = decorated(label)
        textSize = 14.8f
        isAllCaps = false
        gravity = Gravity.CENTER
        setTypeface(typeface, if (primary) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(if (primary) Color.WHITE else palette.text)
        val normal = if (primary) {
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(palette.primary, palette.accent)).apply {
                cornerRadius = dp(context, palette.buttonRadius).toFloat()
            }
        } else round(palette.surface2, palette.buttonRadius, context, palette.divider)
        background = ripple(context, normal, if (primary) Color.argb(50, 255,255,255) else Color.argb(28, Color.red(palette.primary), Color.green(palette.primary), Color.blue(palette.primary)))
        elevation = dp(context, if (primary && palette.cardElevation > 0) 1 else 0).toFloat()
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, palette.buttonHeight)).apply {
            bottomMargin = dp(context, 9)
        }
    }

    fun sectionLabel(context: Context, palette: Palette, label: String): TextView = TextView(context).apply {
        text = label
        textSize = 12.8f
        setTextColor(palette.accent)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(context, 3), 0, dp(context, 9))
        includeFontPadding = false
    }


    fun actionTile(context: Context, palette: Palette, titleText: String, subtitleText: String, action: () -> Unit): LinearLayout {
        val mode = currentLayout(context)
        return card(context, palette, when (mode) {
            LayoutMode.COMPACT -> 10
            LayoutMode.LARGE -> 16
            LayoutMode.ORGANIZED -> 12
        }).apply {
            val cleanTitle = titleText.replace(Regex("""^[^\p{L}\p{N}]+\s*"""), "")
            val icon = iconFor(titleText)
            if (mode == LayoutMode.COMPACT) {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                if (icon.isNotBlank()) addView(TextView(context).apply {
                    text = icon
                    textSize = 17f
                    gravity = Gravity.CENTER
                    setTextColor(palette.accent)
                    background = round(palette.surface2, 12, context, palette.divider)
                    layoutParams = LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)).apply {
                        marginEnd = dp(context, 10)
                    }
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    addView(title(context, palette, cleanTitle, 15.5f))
                    addView(subtitle(context, palette, subtitleText).apply { textSize = 12.1f; maxLines = 2 })
                })
            } else {
                gravity = Gravity.CENTER_HORIZONTAL
                if (icon.isNotBlank()) addView(TextView(context).apply {
                    text = icon
                    textSize = if (mode == LayoutMode.LARGE) 23f else 19f
                    gravity = Gravity.CENTER
                    setTextColor(palette.accent)
                    background = round(palette.surface2, 13, context, palette.divider)
                    layoutParams = LinearLayout.LayoutParams(
                        dp(context, if (mode == LayoutMode.LARGE) 50 else 42),
                        dp(context, if (mode == LayoutMode.LARGE) 50 else 42)
                    ).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        bottomMargin = dp(context, 8)
                    }
                })
                addView(title(context, palette, cleanTitle, if (mode == LayoutMode.LARGE) 17.4f else 16.2f).apply { gravity = Gravity.CENTER })
                addView(subtitle(context, palette, subtitleText).apply {
                    gravity = Gravity.CENTER
                    textSize = if (mode == LayoutMode.LARGE) 13.1f else 12.4f
                    maxLines = if (mode == LayoutMode.LARGE) 4 else 3
                })
            }
            makeInteractive(this, context, palette)
            setOnClickListener { action() }
        }
    }

    fun statusBadge(context: Context, palette: Palette, label: String, positive: Boolean): TextView = TextView(context).apply {
        text = label
        textSize = 12.8f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        val base = if (positive) palette.success else palette.danger
        setTextColor(base)
        background = round(Color.argb(27, Color.red(base), Color.green(base), Color.blue(base)), 999, context, Color.argb(85, Color.red(base), Color.green(base), Color.blue(base)))
        setPadding(dp(context, 14), dp(context, 7), dp(context, 14), dp(context, 7))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(context, 10)
        }
    }
}
