package com.attendpro.core

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.app.AlertDialog
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/**
 * ماسح QR داخلي خاص بـ ATTEND PRO.
 * لا يعتمد على Activity خارجية ولا على IntentIntegrator، وهذا يمنع مشكلة خروج/إغلاق التطبيق
 * التي ظهرت على بعض إصدارات Samsung / Android الحديثة عند فتح الماسح القديم.
 */
class QrScannerActivity : Activity() {
    companion object {
        const val EXTRA_PROMPT = "attendpro.qr.prompt"
        const val EXTRA_RESULT = "attendpro.qr.result"
        const val EXTRA_ERROR = "attendpro.qr.error"
        private const val CAMERA_REQUEST = 7301
    }

    private lateinit var barcodeView: DecoratedBarcodeView
    private var finishedWithResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(3, 18, 31)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(3, 18, 31))
            setPadding(dp(14), dp(16), dp(14), dp(14))
        }
        val title = TextView(this).apply {
            text = "ATTEND PRO • مسح QR"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        val prompt = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_PROMPT).orEmpty().ifBlank { "وجّه الكاميرا إلى رمز QR" }
            textSize = 15f
            setTextColor(Color.rgb(190, 215, 230))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
        }
        barcodeView = DecoratedBarcodeView(this).apply {
            setStatusText("ثبّت الهاتف حتى يتم التقاط الرمز تلقائيًا")
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val cancel = Button(this).apply {
            text = "إلغاء والعودة"
            isAllCaps = false
            setOnClickListener { cancelScan("تم إلغاء المسح") }
        }
        root.addView(title)
        root.addView(prompt)
        root.addView(barcodeView)
        root.addView(Button(this).apply {
            text = "تعذر المسح؟ أدخل رمز الربط يدويًا"
            isAllCaps = false
            setOnClickListener { showManualCode() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(10) })
        root.addView(cancel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(10) })
        setContentView(root)

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            beginScan()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
        }
    }

    private fun showManualCode() {
        runCatching { barcodeView.pause() }
        val input = EditText(this).apply {
            hint = "الصق رمز الربط AP5Q أو AP4P أو AP3P أو APPAIR"
            minLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val dialog = AlertDialog.Builder(this).setTitle("إدخال رمز الربط")
            .setView(input).setPositiveButton("استخدام الرمز", null)
            .setNegativeButton("العودة للكاميرا") { _, _ -> runCatching { barcodeView.resume() } }.create()
        dialog.setOnCancelListener { runCatching { barcodeView.resume() } }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim()
                if (value.isBlank()) { input.error = "ألصق رمز الربط كاملًا"; return@setOnClickListener }
                finishedWithResult = true
                dialog.dismiss()
                setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, value))
                finish()
            }
        }
        dialog.show()
    }

    private fun beginScan() {
        if (finishedWithResult) return
        barcodeView.decodeSingle(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                val text = result?.text?.trim().orEmpty()
                if (text.isBlank() || finishedWithResult) return
                val pairingEnvelope = PairingProtocol.decodeQrPairingEnvelope(text) != null
                val provision = PairingProtocol.decodeEmployeeProvision(text) != null
                val shortPairing = text.removePrefix("APPAIR:").replace(Regex("[^A-Za-z0-9]"), "").length == 8 && text.startsWith("APPAIR:")
                val attendance = AttendanceQrProtocol.isAttendanceQr(text)
                if (!pairingEnvelope && !provision && !shortPairing && !attendance) {
                    barcodeView.setStatusText("هذا ليس QR خاصًا بـ ATTEND PRO — وجّه الكاميرا إلى رمز الربط الظاهر في جهاز المحل")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ beginScan() }, 500L)
                    return
                }
                finishedWithResult = true
                barcodeView.pause()
                setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, text))
                finish()
            }

            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
        })
        runCatching { barcodeView.resume() }.onFailure {
            cancelScan("تعذر تشغيل الكاميرا: ${it.message ?: "خطأ غير معروف"}")
        }
    }

    private fun cancelScan(message: String) {
        if (finishedWithResult) return
        finishedWithResult = true
        runCatching { barcodeView.pause() }
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR, message))
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) beginScan()
            else cancelScan("يلزم السماح للكاميرا لقراءة QR")
        }
    }

    override fun onResume() {
        super.onResume()
        if (::barcodeView.isInitialized && !finishedWithResult && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            runCatching { barcodeView.resume() }
        }
    }

    override fun onPause() {
        if (::barcodeView.isInitialized) runCatching { barcodeView.pause() }
        super.onPause()
    }

    override fun onBackPressed() = cancelScan("تم إلغاء المسح")

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
