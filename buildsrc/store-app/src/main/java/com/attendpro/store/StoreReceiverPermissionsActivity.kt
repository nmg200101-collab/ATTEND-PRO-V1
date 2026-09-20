package com.attendpro.store

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.attendpro.core.AppLanguage
import com.attendpro.core.AuthorizedReportReceiver
import com.attendpro.core.CentralServerClient
import com.attendpro.core.DeviceIdentity
import com.attendpro.core.QrCodeTools
import com.attendpro.core.QrScannerActivity
import com.attendpro.core.ReportProtocol
import com.attendpro.core.StoreRepository
import com.attendpro.core.UiKit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StoreReceiverPermissionsActivity : Activity() {
    companion object { private const val REQ_RECEIVER_QR = 9317 }

    private lateinit var repo: StoreRepository
    private val p by lazy { UiKit.palette(this) }
    private var tab = 0
    private fun t(ar: String, en: String) = AppLanguage.text(this, ar, en)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = StoreRepository(this)
        if (repo.hasStoreAdminPin && !repo.hasActiveStoreAdminSession()) {
            info(t("الوصول محمي", "Protected access"), t("افتح المركز من قسم التقارير بعد الدخول إلى إدارة المحل.", "Open this center from Reports after entering Store Management.")) { finish() }
            return
        }
        tab = savedInstanceState?.getInt("tab", 0) ?: 0
        buildUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", tab)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::repo.isInitialized) buildUi()
    }

    private fun buildUi() {
        window.statusBarColor = p.bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = if (AppLanguage.isEnglish(this@StoreReceiverPermissionsActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@StoreReceiverPermissionsActivity, 14), UiKit.dp(this@StoreReceiverPermissionsActivity, 18), UiKit.dp(this@StoreReceiverPermissionsActivity, 14), UiKit.dp(this@StoreReceiverPermissionsActivity, 28))
            setBackgroundColor(p.bg)
        }

        root.addView(UiKit.heroCard(this, p, 14).apply {
            addView(UiKit.title(this@StoreReceiverPermissionsActivity, p, t("هواتف الاستلام والصلاحيات", "Receiver phones and permissions"), 24f).apply {
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
            })
            addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p, t("${repo.storeName} • الفرع ${repo.branchId}", "${repo.storeName} • Branch ${repo.branchId}")).apply {
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
            })
        })

        root.addView(UiKit.card(this, p, 8).apply {
            listOf(t("الهواتف", "Phones"), t("إضافة هاتف", "Add phone"), t("الصلاحيات", "Permissions")).forEachIndexed { index, label ->
                addView(UiKit.button(this@StoreReceiverPermissionsActivity, p, label, tab == index).apply {
                    setOnClickListener { tab = index; buildUi() }
                })
            }
        })

        when (tab) {
            0 -> phonesTab(root)
            1 -> addPhoneTab(root)
            else -> permissionsTab(root)
        }

        root.addView(UiKit.card(this, p, 7).apply {
            addView(UiKit.button(this@StoreReceiverPermissionsActivity, p, t("رجوع", "Back"), false).apply { setOnClickListener { finish() } })
        })
        setContentView(ScrollView(this).apply { isFillViewport = true; setBackgroundColor(p.bg); addView(root) })
    }

    private fun phonesTab(root: LinearLayout) {
        val phones = repo.authorizedReportReceivers()
        if (phones.isEmpty()) {
            root.addView(UiKit.card(this, p).apply {
                addView(UiKit.sectionLabel(this@StoreReceiverPermissionsActivity, p, t("الهواتف", "Phones")))
                addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p, t("لا توجد هواتف مرتبطة. انتقل إلى تبويب «إضافة هاتف» لبدء الربط.", "No linked phones. Open Add phone to start linking.")))
            })
            return
        }
        phones.forEach { phone ->
            root.addView(UiKit.card(this, p, 11).apply {
                addView(UiKit.title(this@StoreReceiverPermissionsActivity, p, phone.name.ifBlank { t("هاتف استلام", "Receiver phone") }, 19f))
                addView(UiKit.statusBadge(this@StoreReceiverPermissionsActivity, p, if (phone.active) t("نشط", "Active") else t("موقوف", "Disabled"), phone.active))
                addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p,
                    t("المعرف: ${phone.receiverId}\nالفرع: ${repo.branchId}\nأضيف: ${formatTime(phone.createdAt)}\nآخر استخدام: ${formatTimeOrNever(phone.lastUsedAt)}",
                      "ID: ${phone.receiverId}\nBranch: ${repo.branchId}\nAdded: ${formatTime(phone.createdAt)}\nLast use: ${formatTimeOrNever(phone.lastUsedAt)}")))
                addView(UiKit.sectionLabel(this@StoreReceiverPermissionsActivity, p, t("الصلاحيات الحالية", "Current permissions")))
                addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p, permissionsText(phone)))
                addView(UiKit.button(this@StoreReceiverPermissionsActivity, p, t("إدارة الصلاحيات", "Manage permissions"), false).apply { setOnClickListener { editPermissions(phone) } })
                addView(UiKit.button(this@StoreReceiverPermissionsActivity, p, if (phone.active) t("إيقاف الهاتف", "Disable phone") else t("تفعيل الهاتف", "Enable phone"), false).apply {
                    setOnClickListener { setPhoneActive(phone, !phone.active) }
                })
                addView(UiKit.button(this@StoreReceiverPermissionsActivity, p, t("فك ارتباط الهاتف", "Unlink phone"), false).apply { setOnClickListener { confirmRemove(phone) } })
            })
        }
    }

    private fun addPhoneTab(root: LinearLayout) {
        root.addView(UiKit.card(this, p).apply {
            addView(UiKit.sectionLabel(this@StoreReceiverPermissionsActivity, p, t("إضافة هاتف", "Add phone")))
            addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p, t("من هاتف الاستلام افتح تبويب التفعيل ثم «إظهار QR تعريف هذا الهاتف». بعد المسح ستختار الصلاحيات قبل حفظ الهاتف.", "On the receiver phone open Activation, then Show this phone identity QR. After scanning, choose permissions before saving.")))
            addView(UiKit.button(this@StoreReceiverPermissionsActivity, p, t("مسح QR هاتف الاستلام", "Scan receiver phone QR")).apply { setOnClickListener { scanReceiver() } })
        })
    }

    private fun permissionsTab(root: LinearLayout) {
        val phones = repo.authorizedReportReceivers()
        if (phones.isEmpty()) {
            root.addView(UiKit.card(this, p).apply {
                addView(UiKit.sectionLabel(this@StoreReceiverPermissionsActivity, p, t("الصلاحيات", "Permissions")))
                addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p, t("أضف هاتفًا أولًا ثم عد إلى هذا التبويب.", "Add a phone first, then return to this tab.")))
            })
            return
        }
        phones.forEach { phone ->
            root.addView(UiKit.card(this, p, 10).apply {
                addView(UiKit.title(this@StoreReceiverPermissionsActivity, p, t("صلاحيات: ${phone.name}", "Permissions: ${phone.name}"), 18f))
                addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p, permissionsText(phone)))
                addView(UiKit.button(this@StoreReceiverPermissionsActivity, p, t("حفظ التغييرات / تعديل الصلاحيات", "Save changes / Edit permissions"), false).apply { setOnClickListener { editPermissions(phone) } })
            })
        }
    }

    private fun scanReceiver() {
        startActivityForResult(Intent(this, QrScannerActivity::class.java).putExtra(QrScannerActivity.EXTRA_PROMPT, t("وجّه الكاميرا إلى QR تعريف هاتف الاستلام", "Point the camera at the receiver phone identity QR")), REQ_RECEIVER_QR)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_RECEIVER_QR || resultCode != RESULT_OK) return
        val invite = ReportProtocol.decodeInvite(data?.getStringExtra(QrScannerActivity.EXTRA_RESULT).orEmpty())
        if (invite == null) {
            info(t("QR غير صالح", "Invalid QR"), t("اعرض QR تعريف هاتف الاستلام ثم أعد المسح.", "Show the receiver phone identity QR and scan again."))
            return
        }
        chooseNewPhonePermissions(invite)
    }

    private fun chooseNewPhonePermissions(invite: ReportProtocol.ReceiverInvite) {
        val labels = arrayOf(t("استلام التقارير", "Receive reports"), t("مراسلة الموظفين", "Message employees"), t("إدارة الموظفين", "Employee management"))
        val checked = booleanArrayOf(true, false, false)
        AlertDialog.Builder(this)
            .setTitle(t("إعداد الهاتف الجديد — ${invite.name}", "Configure new phone — ${invite.name}"))
            .setMessage(t("اختر صلاحية واحدة أو أكثر. «إدارة الموظفين» تسمح بإضافة الموظفين وتعديلهم وتفعيلهم/إيقافهم فقط، ولا تمنح إعدادات المحل أو إدارة النظام.", "Choose one or more permissions. Employee management allows employee add/edit/enable/disable only and does not grant Store or system settings."))
            .setMultiChoiceItems(labels, checked) { _, which, value -> checked[which] = value }
            .setPositiveButton(t("حفظ وربط الهاتف", "Save and link phone")) { _, _ ->
                if (!checked.any { it }) {
                    info(t("اختر صلاحية", "Choose a permission"), t("يجب اختيار صلاحية واحدة على الأقل.", "Select at least one permission."))
                } else completeAdd(invite, checked[0], checked[1], checked[2])
            }
            .setNegativeButton(t("إلغاء", "Cancel"), null)
            .show()
    }

    private fun completeAdd(invite: ReportProtocol.ReceiverInvite, reports: Boolean, messages: Boolean, manage: Boolean) {
        if (!repo.authorizeReportReceiver(invite)) {
            info(t("تعذر الربط", "Link failed"), t("رمز الهاتف منتهي أو غير صالح.", "The phone code is expired or invalid."))
            return
        }
        repo.setReportReceiverPermissions(invite.receiverId, reports, messages, manage)
        if (!repo.isCentralActivationActive() || repo.serverUrl.isBlank()) {
            info(t("تم الحفظ محليًا", "Saved locally"), t("تم حفظ الهاتف والصلاحيات محليًا، لكن لا يمكن إنشاء الربط النهائي حتى يتوفر الخادم المركزي.", "The phone and permissions were saved locally, but final linking requires the central server."))
            tab = 0; buildUi(); return
        }
        Thread {
            val register = CentralServerClient.registerReceiver(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), invite.receiverId, invite.name, invite.secret)
            val permissions = if (register.isSuccess) CentralServerClient.setReceiverPermissions(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), invite.receiverId, reports, messages, manage) else Result.failure(register.exceptionOrNull() ?: IllegalStateException("register failed"))
            runOnUiThread {
                if (register.isSuccess && permissions.isSuccess) {
                    showFinalGrant(invite)
                } else {
                    val reason = permissions.exceptionOrNull()?.message ?: register.exceptionOrNull()?.message ?: t("خطأ غير معروف", "Unknown error")
                    info(t("حُفظ محليًا وتعذرت مزامنة الخادم", "Saved locally; server sync failed"), reason)
                }
                tab = 0; buildUi()
            }
        }.start()
    }

    private fun showFinalGrant(invite: ReportProtocol.ReceiverInvite) {
        val grant = ReportProtocol.RemoteReceiverGrant(invite.receiverId, repo.serverUrl, repo.storeName, repo.branchId, System.currentTimeMillis() + 10 * 60_000L)
        val qr = runCatching { QrCodeTools.bitmap(ReportProtocol.encodeRemoteGrant(grant), 700) }.getOrElse {
            info(t("تعذر إنشاء QR", "Unable to create QR"), it.message ?: t("خطأ", "Error")); return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p, t("امسح هذا الرمز من هاتف الاستلام عبر «مسح QR الربط النهائي».", "Scan this code on the receiver phone using Scan final link QR.")))
            addView(ImageView(this@StoreReceiverPermissionsActivity).apply {
                setImageBitmap(qr); adjustViewBounds = true
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this@StoreReceiverPermissionsActivity, 420))
            })
        }
        AlertDialog.Builder(this).setTitle(t("تم ربط الهاتف ✓ — QR الربط النهائي", "Phone linked ✓ — final linking QR")).setView(box).setPositiveButton(t("تم", "Done"), null).show()
    }

    private fun editPermissions(phone: AuthorizedReportReceiver) {
        val labels = arrayOf(t("استلام التقارير", "Receive reports"), t("مراسلة الموظفين", "Message employees"), t("إدارة الموظفين", "Employee management"))
        val checked = booleanArrayOf(phone.canReceiveReports, phone.canMessageEmployees, phone.canManageStore)
        AlertDialog.Builder(this)
            .setTitle(t("صلاحيات: ${phone.name}", "Permissions: ${phone.name}"))
            .setMessage(t("إدارة الموظفين لا تمنح صلاحية إعدادات المحل أو المالك أو النظام.", "Employee management does not grant Store, owner, or system settings."))
            .setMultiChoiceItems(labels, checked) { _, which, value -> checked[which] = value }
            .setPositiveButton(t("حفظ التغييرات", "Save changes")) { _, _ -> savePermissions(phone, checked[0], checked[1], checked[2]) }
            .setNegativeButton(t("إلغاء", "Cancel"), null)
            .show()
    }

    private fun savePermissions(phone: AuthorizedReportReceiver, reports: Boolean, messages: Boolean, manage: Boolean) {
        repo.setReportReceiverPermissions(phone.receiverId, reports, messages, manage)
        if (!repo.isCentralActivationActive() || repo.serverUrl.isBlank()) {
            info(t("تم الحفظ محليًا", "Saved locally"), t("تم حفظ الصلاحيات محليًا، والخادم غير متاح للمزامنة الآن.", "Permissions were saved locally; the server is unavailable for sync.")); buildUi(); return
        }
        Thread {
            val result = CentralServerClient.setReceiverPermissions(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), phone.receiverId, reports, messages, manage)
            runOnUiThread {
                if (result.isSuccess) info(t("تم حفظ التغييرات ✓", "Changes saved ✓"), t("تم حفظ الصلاحيات محليًا ومزامنتها مع الخادم. سيقرأ هاتف الاستلام الصلاحيات الجديدة عند التحديث.", "Permissions were saved locally and synced with the server. The receiver phone will read them on refresh."))
                else info(t("حُفظ محليًا وفشلت مزامنة الخادم", "Saved locally; server sync failed"), result.exceptionOrNull()?.message ?: t("خطأ", "Error"))
                buildUi()
            }
        }.start()
    }

    private fun setPhoneActive(phone: AuthorizedReportReceiver, active: Boolean) {
        repo.setReportReceiverActive(phone.receiverId, active)
        if (!repo.isCentralActivationActive() || repo.serverUrl.isBlank()) { buildUi(); return }
        Thread {
            val result = CentralServerClient.setReceiverActive(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), phone.receiverId, active)
            runOnUiThread {
                if (result.isFailure) info(t("تغيرت الحالة محليًا وفشلت مزامنة الخادم", "Local state changed; server sync failed"), result.exceptionOrNull()?.message ?: t("خطأ", "Error"))
                buildUi()
            }
        }.start()
    }

    private fun confirmRemove(phone: AuthorizedReportReceiver) {
        AlertDialog.Builder(this).setTitle(t("فك ارتباط ${phone.name}؟", "Unlink ${phone.name}?"))
            .setMessage(t("سيتم سحب صلاحيات هاتف الاستلام. هذا لا يغيّر ربط أي موظف ولا إعدادات Bluetooth أو GPS.", "Receiver-phone permissions will be revoked. Employee pairing and Bluetooth/GPS settings are not changed."))
            .setPositiveButton(t("فك الارتباط", "Unlink")) { _, _ -> removePhone(phone) }
            .setNegativeButton(t("إلغاء", "Cancel"), null).show()
    }

    private fun removePhone(phone: AuthorizedReportReceiver) {
        if (!repo.isCentralActivationActive() || repo.serverUrl.isBlank()) {
            repo.removeReportReceiver(phone.receiverId); buildUi(); return
        }
        Thread {
            val result = CentralServerClient.setReceiverActive(repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), phone.receiverId, false)
            runOnUiThread {
                if (result.isSuccess) {
                    repo.removeReportReceiver(phone.receiverId)
                    info(t("تم فك الارتباط ✓", "Phone unlinked ✓"), t("تم سحب صلاحيات الهاتف من هذا المحل.", "Phone permissions were revoked from this Store."))
                } else info(t("تعذر فك الارتباط", "Unable to unlink"), result.exceptionOrNull()?.message ?: t("خطأ", "Error"))
                buildUi()
            }
        }.start()
    }

    private fun permissionsText(phone: AuthorizedReportReceiver): String = listOf(
        permissionLine(phone.canReceiveReports, t("استلام التقارير", "Receive reports")),
        permissionLine(phone.canMessageEmployees, t("مراسلة الموظفين", "Message employees")),
        permissionLine(phone.canManageStore, t("إدارة الموظفين", "Employee management"))
    ).joinToString("\n")

    private fun permissionLine(allowed: Boolean, label: String) = if (allowed) t("✓ مسموح — $label", "✓ Allowed — $label") else t("— غير مسموح — $label", "— Not allowed — $label")
    private fun formatTime(value: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(value))
    private fun formatTimeOrNever(value: Long) = if (value <= 0L) t("لم يُستخدم بعد", "Not used yet") else formatTime(value)

    private fun info(title: String, message: String, onDone: (() -> Unit)? = null) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton(t("حسنًا", "OK")) { _, _ -> onDone?.invoke() }.show()
    }
}
