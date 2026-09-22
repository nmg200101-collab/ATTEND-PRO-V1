package com.attendpro.store

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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

/**
 * V137 single Store-side receiver center:
 * Reports -> Receiver phones -> Overview / Phones / Add phone / Permissions.
 *
 * The Activity content view is installed once. Background callbacks only update state and
 * re-render the center when the Activity is alive and the request generation is current.
 */
class StoreReceiverPermissionsActivity : Activity() {
    private enum class Tab { OVERVIEW, PHONES, ADD_PHONE }

    private lateinit var repo: StoreRepository
    private val p by lazy { UiKit.palette(this) }
    private val meta by lazy { getSharedPreferences("receiver_v137_meta", MODE_PRIVATE) }

    private lateinit var header: LinearLayout
    private lateinit var tabs: LinearLayout
    private lateinit var content: LinearLayout

    private var tab = Tab.OVERVIEW
    private var selectedReceiverId = ""
    private var pendingInvite: ReportProtocol.ReceiverInvite? = null
    private var finalGrantText = ""
    private var notice = ""
    private var linkProgress = ""

    @Volatile private var remoteInFlight = false
    @Volatile private var requestGeneration = 0L

    private fun t(ar: String, en: String) = AppLanguage.text(this, ar, en)
    private fun alive() = !isFinishing && !isDestroyed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = StoreRepository(this)
        if (repo.hasStoreAdminPin && !repo.hasActiveStoreAdminSession()) {
            showError(
                t("الوصول محمي", "Protected access"),
                t("افتح هذا المركز من «التقارير» بعد الدخول إلى إدارة المحل.", "Open this center from Reports after entering Store Management.")
            ) { finish() }
            return
        }

        tab = savedInstanceState?.getString(KEY_TAB)
            ?.let { runCatching { Tab.valueOf(it) }.getOrNull() } ?: Tab.OVERVIEW
        selectedReceiverId = savedInstanceState?.getString(KEY_RECEIVER_ID).orEmpty()
        installUiOnce()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_TAB, tab.name)
        outState.putString(KEY_RECEIVER_ID, selectedReceiverId)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized && alive()) {
            render()
            flushPendingReceiverDeletes()
        }
    }

    override fun onDestroy() {
        requestGeneration++
        super.onDestroy()
    }

    private fun installUiOnce() {
        window.statusBarColor = p.bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = if (AppLanguage.isEnglish(this@StoreReceiverPermissionsActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            setPadding(
                UiKit.dp(this@StoreReceiverPermissionsActivity, 14),
                UiKit.dp(this@StoreReceiverPermissionsActivity, 18),
                UiKit.dp(this@StoreReceiverPermissionsActivity, 14),
                UiKit.dp(this@StoreReceiverPermissionsActivity, 28)
            )
            setBackgroundColor(p.bg)
        }
        header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabs = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(header)
        root.addView(tabs)
        root.addView(content)
        root.addView(UiKit.card(this, p, 7).apply {
            addView(UiKit.button(this@StoreReceiverPermissionsActivity, p, t("رجوع إلى التقارير", "Back to reports"), false).apply {
                setOnClickListener { finish() }
            })
        })
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(p.bg)
            addView(root)
        })
    }

    private fun render() {
        if (!alive() || !::content.isInitialized) return
        renderHeader()
        renderTabs()
        content.removeAllViews()
        if (notice.isNotBlank()) {
            content.addView(UiKit.card(this, p, 7).apply {
                addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p, notice).apply { gravity = Gravity.CENTER })
            })
        }
        when (tab) {
            Tab.OVERVIEW -> overviewTab()
            Tab.PHONES -> phonesTab()
            Tab.ADD_PHONE -> addPhoneTab()
        }
    }

    private fun renderHeader() {
        header.removeAllViews()
        header.addView(UiKit.heroCard(this, p, 14).apply {
            addView(UiKit.title(this@StoreReceiverPermissionsActivity, p, t("هواتف الاستلام", "Receiver phones"), 24f).apply {
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
            })
            addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p,
                t("${repo.storeName} • الفرع ${repo.branchId}", "${repo.storeName} • Branch ${repo.branchId}")
            ).apply {
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
            })
        })
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        tabs.addView(UiKit.card(this, p, 8).apply {
            addTab(this, Tab.OVERVIEW, t("نظرة عامة", "Overview"))
            addTab(this, Tab.PHONES, t("الهواتف", "Phones"))
            addTab(this, Tab.ADD_PHONE, t("إضافة هاتف", "Add phone"))
        })
    }

    private fun addTab(card: LinearLayout, target: Tab, label: String) {
        card.addView(UiKit.button(this, p, label, tab == target).apply {
            setOnClickListener {
                tab = target
                notice = ""
                render()
            }
        })
    }

    private fun overviewTab() {
        val phones = repo.authorizedReportReceivers()
        val active = phones.count { it.active }
        val reportPhones = phones.count { it.active && it.canReceiveReports }
        val messagePhones = phones.count { it.active && it.canMessageEmployees }
        val employeePhones = phones.count { it.active && it.canManageStore }

        content.addView(UiKit.card(this, p, 10).apply {
            addView(UiKit.sectionLabel(this@StoreReceiverPermissionsActivity, p, t("نظرة عامة", "Overview")))
            addView(UiKit.title(this@StoreReceiverPermissionsActivity, p,
                t("$active هاتف نشط من ${phones.size}", "$active active of ${phones.size} phones"), 20f
            ))
            addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p, t(
                "استلام التقارير: $reportPhones\nمراسلة الموظفين: $messagePhones\nإدارة الموظفين: $employeePhones",
                "Receive reports: $reportPhones\nEmployee messaging: $messagePhones\nEmployee management: $employeePhones"
            )))
            addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p, t(
                "المسار الوحيد لإدارة هواتف الاستلام هو: التقارير ← هواتف الاستلام.",
                "The single receiver-management path is: Reports → Receiver phones."
            )))
        })

        content.addView(UiKit.card(this, p, 9).apply {
            addView(UiKit.sectionLabel(this@StoreReceiverPermissionsActivity, p, t("حالة الخادم", "Server status")))
            addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p,
                if (repo.serverUrl.isNotBlank() && repo.isCentralActivationActive())
                    t("✓ التفعيل المركزي والخادم متاحان.", "✓ Central activation and server are available.")
                else
                    t("الخادم المركزي غير متاح حاليًا. تبقى البيانات المحلية محفوظة ولا تُحذف.", "The central server is currently unavailable. Local data remains preserved.")
            ))
        })
    }

    private fun phonesTab() {
        val phones = repo.authorizedReportReceivers()
        if (phones.isEmpty()) {
            content.addView(UiKit.card(this, p).apply {
                addView(UiKit.sectionLabel(this@StoreReceiverPermissionsActivity, p, t("الهواتف", "Phones")))
                addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p, t(
                    "لا توجد هواتف مرتبطة. انتقل إلى «إضافة هاتف».",
                    "No linked phones. Open Add phone."
                )))
            })
            return
        }

        phones.forEach { phone ->
            content.addView(UiKit.card(this, p, 11).apply {
                addView(UiKit.title(this@StoreReceiverPermissionsActivity, p, phone.name.ifBlank { t("هاتف استلام", "Receiver phone") }, 19f))
                addView(UiKit.statusBadge(this@StoreReceiverPermissionsActivity, p,
                    if (phone.active) t("نشط", "Active") else t("موقوف", "Disabled"), phone.active))
                addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p, t(
                    "المعرف: ${phone.receiverId}\nحالة الربط: ${if (phone.active) "مرتبط ونشط" else "مرتبط وموقوف"}\nآخر اتصال بالخادم: ${metaTime(phone.receiverId, META_SERVER_SYNC)}\nآخر تحديث للصلاحيات: ${metaTime(phone.receiverId, META_PERMISSION_UPDATE)}",
                    "ID: ${phone.receiverId}\nLink: ${if (phone.active) "linked and active" else "linked and disabled"}\nLast server contact: ${metaTime(phone.receiverId, META_SERVER_SYNC)}\nLast permission update: ${metaTime(phone.receiverId, META_PERMISSION_UPDATE)}"
                )))
                addView(UiKit.sectionLabel(this@StoreReceiverPermissionsActivity, p, t("الصلاحيات الحالية", "Current permissions")))
                addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p, permissionsText(phone)))
                addView(UiKit.subtitle(this@StoreReceiverPermissionsActivity, p, t(
                    "النوع: هاتف استلام التقارير / الإدارة",
                    "Type: report receiver / administration phone"
                )))
                addView(UiKit.button(this@StoreReceiverPermissionsActivity, p,
                    if (selectedReceiverId == phone.receiverId) t("إغلاق تعديل الصلاحيات", "Close permission editor")
                    else t("تعديل صلاحيات هذا الهاتف", "Edit this phone permissions"), false).apply {
                    isEnabled = !remoteInFlight
                    setOnClickListener {
                        selectedReceiverId = if (selectedReceiverId == phone.receiverId) "" else phone.receiverId
                        render()
                    }
                })
                if (selectedReceiverId == phone.receiverId) {
                    val reports = permissionCheckBox(
                        t("استلام التقارير", "Receive reports"),
                        t("استلام تقارير الحضور من هذا المحل.", "Receive attendance reports from this store."),
                        phone.canReceiveReports
                    )
                    val messages = permissionCheckBox(
                        t("مراسلة الموظفين", "Message employees"),
                        t("إرسال الرسائل واستلام الردود.", "Send messages and receive replies."),
                        phone.canMessageEmployees
                    )
                    val manage = permissionCheckBox(
                        t("إدارة الموظفين", "Employee management"),
                        t("عرض/إضافة/تعديل/تفعيل/إيقاف الموظفين فقط.", "View/add/edit/enable/disable employees only."),
                        phone.canManageStore
                    )
                    addView(reports); addView(messages); addView(manage)
                    addView(UiKit.button(this@StoreReceiverPermissionsActivity, p,
                        if (remoteInFlight) t("جاري الحفظ والتحقق…", "Saving and verifying…")
                        else t("حفظ صلاحيات هذا الهاتف", "Save this phone permissions"), false).apply {
                        isEnabled = !remoteInFlight
                        setOnClickListener { savePermissions(phone, reports.isChecked, messages.isChecked, manage.isChecked) }
                    })
                }
                addView(UiKit.button(this@StoreReceiverPermissionsActivity, p,
                    if (phone.active) t("إيقاف الهاتف", "Disable phone") else t("تفعيل الهاتف", "Enable phone"), false
                ).apply {
                    isEnabled = !remoteInFlight
                    setOnClickListener {
                        if (phone.active) confirmDisable(phone) else setPhoneActive(phone, true)
                    }
                })
                addView(UiKit.button(this@StoreReceiverPermissionsActivity, p, t("حذف / فك ارتباط الهاتف", "Remove / unlink phone"), false).apply {
                    isEnabled = !remoteInFlight
                    setOnClickListener { confirmRemove(phone) }
                })
            })
        }
    }

    private fun addPhoneTab() {
        val card = UiKit.card(this, p, 10)
        card.addView(UiKit.sectionLabel(this, p, t("إضافة هاتف", "Add phone")))
        if (linkProgress.isNotBlank()) {
            card.addView(UiKit.subtitle(this, p, linkProgress))
        }
        card.addView(UiKit.subtitle(this, p, t(
            "من هاتف الاستلام افتح «المحلات» ثم «إضافة محل جديد» لإظهار QR تعريف الهاتف. امسح الرمز هنا، ثم حدد الصلاحيات.",
            "On the receiver phone open Stores, then Add store to show the identity QR. Scan it here, then choose permissions."
        )))
        card.addView(UiKit.button(this, p, t("مسح QR هاتف الاستلام", "Scan receiver phone QR")).apply {
            isEnabled = !remoteInFlight
            setOnClickListener { scanReceiver() }
        })

        pendingInvite?.let { invite ->
            card.addView(UiKit.title(this, p, invite.name.ifBlank { t("هاتف استلام", "Receiver phone") }, 18f))
            card.addView(UiKit.subtitle(this, p, t("المعرف: ${invite.receiverId}", "ID: ${invite.receiverId}")))
            val reports = permissionCheckBox(t("استلام التقارير", "Receive reports"), t("استلام تقارير الحضور.", "Receive attendance reports."), true)
            val messages = permissionCheckBox(t("مراسلة الموظفين", "Message employees"), t("إرسال الرسائل واستلام الردود.", "Send messages and receive replies."), false)
            val manage = permissionCheckBox(t("إدارة الموظفين", "Employee management"), t("عرض/إضافة/تعديل/تفعيل/إيقاف الموظفين فقط.", "View/add/edit/enable/disable employees only."), false)
            card.addView(reports)
            card.addView(messages)
            card.addView(manage)
            card.addView(UiKit.button(this, p,
                if (remoteInFlight) t("جاري الربط…", "Linking…") else t("حفظ وربط الهاتف", "Save and link phone")
            ).apply {
                isEnabled = !remoteInFlight
                setOnClickListener {
                    if (!reports.isChecked && !messages.isChecked && !manage.isChecked) {
                        showError(t("اختر صلاحية", "Choose a permission"), t("يجب اختيار صلاحية واحدة على الأقل.", "Select at least one permission."))
                    } else {
                        completeAdd(invite, reports.isChecked, messages.isChecked, manage.isChecked)
                    }
                }
            })
        }

        if (finalGrantText.isNotBlank()) {
            val qr = runCatching { QrCodeTools.bitmap(finalGrantText, 700) }.getOrNull()
            card.addView(UiKit.sectionLabel(this, p, t("QR الربط النهائي", "Final linking QR")))
            card.addView(UiKit.subtitle(this, p, t(
                "امسح هذا الرمز من هاتف الاستلام عبر «مسح QR الربط النهائي».",
                "Scan this code on the receiver phone using Scan final link QR."
            )))
            if (qr != null) {
                card.addView(ImageView(this@StoreReceiverPermissionsActivity).apply {
                    setImageBitmap(qr)
                    adjustViewBounds = true
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this@StoreReceiverPermissionsActivity, 420))
                })
            }
        }
        content.addView(card)
    }


    private fun permissionCheckBox(label: String, description: String, checked: Boolean): CheckBox =
        CheckBox(this).apply {
            text = "$label\n$description"
            isChecked = checked
            textSize = 16f
            setTextColor(p.text)
            gravity = if (AppLanguage.isEnglish(this@StoreReceiverPermissionsActivity)) Gravity.START else Gravity.END
            layoutDirection = if (AppLanguage.isEnglish(this@StoreReceiverPermissionsActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            setPadding(
                UiKit.dp(this@StoreReceiverPermissionsActivity, 6),
                UiKit.dp(this@StoreReceiverPermissionsActivity, 10),
                UiKit.dp(this@StoreReceiverPermissionsActivity, 6),
                UiKit.dp(this@StoreReceiverPermissionsActivity, 10)
            )
        }

    private fun scanReceiver() {
        startActivityForResult(
            Intent(this, QrScannerActivity::class.java)
                .putExtra(QrScannerActivity.EXTRA_PROMPT, t("وجّه الكاميرا إلى QR تعريف هاتف الاستلام", "Point the camera at the receiver phone identity QR")),
            REQ_RECEIVER_QR
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_RECEIVER_QR || resultCode != RESULT_OK) return
        val invite = ReportProtocol.decodeInvite(data?.getStringExtra(QrScannerActivity.EXTRA_RESULT).orEmpty())
        if (invite == null) {
            showError(t("QR غير صالح", "Invalid QR"), t("اعرض QR تعريف هاتف الاستلام ثم أعد المسح.", "Show the receiver phone identity QR and scan again."))
            return
        }
        pendingInvite = invite
        finalGrantText = ""
        linkProgress = t("1/5 تم التعرف على QR الهاتف ✓", "1/5 Receiver QR recognized ✓")
        notice = t("تمت قراءة هاتف الاستلام. حدد الصلاحيات ثم احفظ الربط.", "Receiver phone read. Choose permissions, then save the link.")
        render()
    }

    private fun completeAdd(invite: ReportProtocol.ReceiverInvite, reports: Boolean, messages: Boolean, manage: Boolean) {
        if (remoteInFlight) return
        if (invite.expiresAt < System.currentTimeMillis() || invite.receiverId.isBlank() || invite.secret.isBlank()) {
            showError(t("تعذر الربط", "Link failed"), t("رمز الهاتف منتهي أو غير صالح.", "The phone code is expired or invalid."))
            return
        }

        remoteInFlight = true
        linkProgress = t("2/5 بدء حفظ الربط…", "2/5 Starting link save…")
        val generation = ++requestGeneration
        render()

        Thread {
            var finalError: Throwable? = null
            var verified = false
            try {
                val centralAvailable = repo.isCentralActivationActive() && repo.serverUrl.isNotBlank()
                if (centralAvailable) {
                    postLinkProgress(generation, t("2/5 تسجيل الهاتف في الخادم…", "2/5 Registering phone with server…"))
                    CentralServerClient.registerReceiver(
                        repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this),
                        invite.receiverId, invite.name, invite.secret
                    ).getOrThrow()

                    postLinkProgress(generation, t("3/5 حفظ الصلاحيات…", "3/5 Saving permissions…"))
                    CentralServerClient.setReceiverPermissions(
                        repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this),
                        invite.receiverId, reports, messages, manage
                    ).getOrThrow()

                    postLinkProgress(generation, t("4/5 التحقق النهائي من الربط…", "4/5 Verifying final link…"))
                    val remote = CentralServerClient.receiverCapabilities(
                        repo.serverUrl, invite.receiverId, invite.secret, repo.storeId
                    ).getOrThrow()
                    check(
                        remote.storeId == repo.storeId &&
                            remote.canReceiveReports == reports &&
                            remote.canMessageEmployees == messages &&
                            remote.canManageStore == manage
                    ) { "لم تتطابق بيانات الربط بعد الحفظ" }
                    verified = true
                }

                check(repo.authorizeReportReceiver(invite)) { "تعذر حفظ الهاتف محليًا" }
                repo.setReportReceiverPermissions(invite.receiverId, reports, messages, manage)
                markMeta(invite.receiverId, META_PERMISSION_UPDATE)
                if (verified) markMeta(invite.receiverId, META_SERVER_SYNC)
            } catch (t: Throwable) {
                finalError = t
            }

            runOnUiThread {
                if (generation != requestGeneration) return@runOnUiThread
                remoteInFlight = false
                if (!alive()) return@runOnUiThread

                if (finalError == null) {
                    val grant = ReportProtocol.RemoteReceiverGrant(
                        invite.receiverId,
                        repo.serverUrl,
                        repo.storeName,
                        repo.branchId,
                        System.currentTimeMillis() + 10 * 60_000L,
                        storeId = repo.storeId
                    )
                    finalGrantText = ReportProtocol.encodeRemoteGrant(grant)
                    pendingInvite = null
                    selectedReceiverId = invite.receiverId
                    linkProgress = if (verified)
                        t("5/5 اكتمل الربط والتحقق ✓", "5/5 Link and verification complete ✓")
                    else
                        t("5/5 تم الحفظ المحلي ✓ — ستتم مزامنة الخادم عند عودة الإنترنت", "5/5 Saved locally ✓ — server sync will complete when internet returns")
                    notice = t(
                        "تم حفظ الهاتف. امسح QR الربط النهائي من هاتف الاستلام.",
                        "Phone saved. Scan the final link QR on the receiver phone."
                    )
                } else {
                    linkProgress = t(
                        "توقف الربط بأمان — يمكنك الضغط على «حفظ وربط الهاتف» لإعادة المحاولة من نفس QR.",
                        "Link stopped safely — press Save and link phone to retry using the same QR."
                    )
                    showError(
                        t("تعذر إكمال الربط", "Could not complete link"),
                        networkMessage(finalError)
                    )
                }
                render()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun postLinkProgress(generation: Long, value: String) {
        runOnUiThread {
            if (generation != requestGeneration || !alive()) return@runOnUiThread
            linkProgress = value
            render()
        }
    }

    private fun savePermissions(phone: AuthorizedReportReceiver, reports: Boolean, messages: Boolean, manage: Boolean) {
        if (remoteInFlight) return
        selectedReceiverId = phone.receiverId
        repo.setReportReceiverPermissions(phone.receiverId, reports, messages, manage)
        markMeta(phone.receiverId, META_PERMISSION_UPDATE)

        if (!repo.isCentralActivationActive() || repo.serverUrl.isBlank()) {
            notice = t("تم حفظ الصلاحيات محليًا، وستتم مزامنتها عند توفر الخادم.", "Permissions saved locally and will sync when the server is available.")
            render()
            return
        }

        remoteInFlight = true
        val generation = ++requestGeneration
        render()
        Thread {
            val identity = DeviceIdentity(this)
            var push = CentralServerClient.setReceiverPermissions(
                repo.serverUrl, repo.centralAccessToken, repo.storeId, identity,
                phone.receiverId, reports, messages, manage
            )

            fun verified(): Result<Boolean> =
                CentralServerClient.receiverCapabilities(repo.serverUrl, phone.receiverId, phone.secret, repo.storeId).map { remote ->
                    remote.canReceiveReports == reports &&
                        remote.canMessageEmployees == messages &&
                        remote.canManageStore == manage
                }

            var verify = if (push.isSuccess && phone.active) verified() else Result.success(!phone.active)
            if (push.isSuccess && phone.active && verify.getOrNull() == false) {
                push = CentralServerClient.setReceiverPermissions(
                    repo.serverUrl, repo.centralAccessToken, repo.storeId, identity,
                    phone.receiverId, reports, messages, manage
                )
                if (push.isSuccess) verify = verified()
            }

            runOnUiThread {
                if (generation != requestGeneration) return@runOnUiThread
                remoteInFlight = false
                if (!alive()) return@runOnUiThread
                when {
                    push.isFailure -> showError(
                        t("حُفظ محليًا وتعذرت مزامنة الخادم", "Saved locally; server sync failed"),
                        networkMessage(push.exceptionOrNull())
                    )
                    !phone.active -> {
                        markMeta(phone.receiverId, META_SERVER_SYNC)
                        notice = t("تم حفظ الصلاحيات على الخادم. الهاتف موقوف حاليًا.", "Permissions saved on the server. The phone is currently disabled.")
                    }
                    verify.isFailure -> showError(
                        t("تم الإرسال وتعذر التحقق النهائي", "Saved; final verification unavailable"),
                        networkMessage(verify.exceptionOrNull())
                    )
                    verify.getOrNull() == true -> {
                        markMeta(phone.receiverId, META_SERVER_SYNC)
                        markMeta(phone.receiverId, META_PERMISSION_UPDATE)
                        notice = t("تم حفظ الصلاحيات والتحقق من وصولها للخادم ✓", "Permissions saved and verified on the server ✓")
                    }
                    else -> showError(
                        t("لم تتطابق الصلاحيات على الخادم", "Server permissions did not match"),
                        t("تم الحفظ محليًا، لكن الخادم لم يُرجع الصلاحيات نفسها بعد إعادة المحاولة.", "Saved locally, but the server did not return the same permissions after retry.")
                    )
                }
                render()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun confirmDisable(phone: AuthorizedReportReceiver) {
        if (!alive()) return
        AlertDialog.Builder(this)
            .setTitle(t("تأكيد إيقاف الهاتف", "Confirm phone disable"))
            .setMessage(t("سيتم إيقاف صلاحيات هذا الهاتف حتى تعيد تفعيله.", "This phone's permissions will be disabled until you enable it again."))
            .setPositiveButton(t("إيقاف", "Disable")) { _, _ -> setPhoneActive(phone, false) }
            .setNegativeButton(t("إلغاء", "Cancel"), null)
            .show()
    }

    private fun setPhoneActive(phone: AuthorizedReportReceiver, active: Boolean) {
        if (remoteInFlight) return
        if (!repo.isCentralActivationActive() || repo.serverUrl.isBlank()) {
            repo.setReportReceiverActive(phone.receiverId, active)
            notice = if (active) t("تم تفعيل الهاتف محليًا.", "Phone enabled locally.") else t("تم إيقاف الهاتف محليًا.", "Phone disabled locally.")
            render()
            return
        }

        remoteInFlight = true
        val generation = ++requestGeneration
        render()
        Thread {
            val result = CentralServerClient.setReceiverActive(
                repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this),
                phone.receiverId, active
            )
            runOnUiThread {
                if (generation != requestGeneration) return@runOnUiThread
                remoteInFlight = false
                if (!alive()) return@runOnUiThread
                if (result.isSuccess) {
                    repo.setReportReceiverActive(phone.receiverId, active)
                    markMeta(phone.receiverId, META_SERVER_SYNC)
                    notice = if (active) t("تم تفعيل الهاتف ✓", "Phone enabled ✓") else t("تم إيقاف الهاتف ✓", "Phone disabled ✓")
                } else {
                    showError(t("تعذر تحديث حالة الهاتف", "Could not update phone status"), networkMessage(result.exceptionOrNull()))
                }
                render()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun confirmRemove(phone: AuthorizedReportReceiver) {
        if (!alive()) return
        AlertDialog.Builder(this)
            .setTitle(t("حذف / فك ارتباط ${phone.name}؟", "Remove / unlink ${phone.name}?"))
            .setMessage(t(
                "سيتم سحب صلاحيات هاتف الاستلام. لن تتغير بيانات ربط الموظفين أو Bluetooth أو GPS.",
                "Receiver permissions will be revoked. Employee pairing, Bluetooth and GPS data will not change."
            ))
            .setPositiveButton(t("حذف وفك الارتباط", "Remove and unlink")) { _, _ -> removePhone(phone) }
            .setNegativeButton(t("إلغاء", "Cancel"), null)
            .show()
    }

    private fun removePhone(phone: AuthorizedReportReceiver) {
        if (remoteInFlight) return
        remoteInFlight = true
        val generation = ++requestGeneration
        render()

        Thread {
            val remoteResult = if (repo.isCentralActivationActive() && repo.serverUrl.isNotBlank()) {
                CentralServerClient.deleteReceiverBinding(
                    repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), phone.receiverId
                )
            } else {
                Result.failure(IllegalStateException("SERVER_OFFLINE"))
            }

            runOnUiThread {
                if (generation != requestGeneration) return@runOnUiThread
                remoteInFlight = false
                if (!alive()) return@runOnUiThread

                // Local deletion is immediate and authoritative for this Store UI.
                repo.removeReportReceiver(phone.receiverId)
                meta.edit()
                    .remove(metaKey(phone.receiverId, META_SERVER_SYNC))
                    .remove(metaKey(phone.receiverId, META_PERMISSION_UPDATE))
                    .apply()
                selectedReceiverId = ""
                if (remoteResult.isSuccess) {
                    removePendingDelete(phone.receiverId)
                    notice = t(
                        "تم حذف الهاتف وفك ارتباطه بالكامل من هذا المحل ✓",
                        "Phone fully removed and unlinked from this store ✓"
                    )
                } else {
                    addPendingDelete(phone.receiverId)
                    notice = t(
                        "تم حذف الهاتف من هذا الجهاز ✓ وسيكتمل سحب الربط من الخادم تلقائيًا عند عودة الإنترنت.",
                        "Phone removed from this device ✓. Server unlink will finish automatically when internet returns."
                    )
                }
                render()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun pendingDeletes(): Set<String> =
        meta.getStringSet(META_PENDING_DELETES, emptySet()).orEmpty().filter { it.isNotBlank() }.toSet()

    private fun addPendingDelete(receiverId: String) {
        meta.edit().putStringSet(META_PENDING_DELETES, pendingDeletes() + receiverId).apply()
    }

    private fun removePendingDelete(receiverId: String) {
        meta.edit().putStringSet(META_PENDING_DELETES, pendingDeletes() - receiverId).apply()
    }

    private fun flushPendingReceiverDeletes() {
        val ids = pendingDeletes()
        if (ids.isEmpty() || remoteInFlight || !repo.isCentralActivationActive() || repo.serverUrl.isBlank()) return
        remoteInFlight = true
        val generation = ++requestGeneration
        Thread {
            val completed = mutableSetOf<String>()
            ids.forEach { id ->
                val result = CentralServerClient.deleteReceiverBinding(
                    repo.serverUrl, repo.centralAccessToken, repo.storeId, DeviceIdentity(this), id
                )
                if (result.isSuccess) completed += id
            }
            runOnUiThread {
                if (generation != requestGeneration) return@runOnUiThread
                completed.forEach(::removePendingDelete)
                remoteInFlight = false
                if (alive()) render()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun permissionsText(phone: AuthorizedReportReceiver): String = listOf(
        permissionLine(phone.canReceiveReports, t("استلام التقارير", "Receive reports")),
        permissionLine(phone.canMessageEmployees, t("مراسلة الموظفين", "Message employees")),
        permissionLine(phone.canManageStore, t("إدارة الموظفين", "Employee management"))
    ).joinToString("\n")

    private fun permissionLine(allowed: Boolean, label: String) =
        if (allowed) t("✓ مسموح — $label", "✓ Allowed — $label") else t("— غير مسموح — $label", "— Not allowed — $label")

    private fun markMeta(receiverId: String, kind: String) {
        meta.edit().putLong(metaKey(receiverId, kind), System.currentTimeMillis()).apply()
    }

    private fun metaTime(receiverId: String, kind: String): String {
        val value = meta.getLong(metaKey(receiverId, kind), 0L)
        return if (value <= 0L) t("لم يتم بعد", "Not yet") else formatTime(value)
    }

    private fun metaKey(receiverId: String, kind: String) = "$kind:$receiverId"

    private fun networkMessage(error: Throwable?): String {
        val raw = error?.message.orEmpty()
        return when {
            raw.contains("Failed to connect to /", true) ||
                raw.contains("Network is unreachable", true) ||
                raw.contains("ENETUNREACH", true) ||
                raw.contains("Unable to resolve host", true) ||
                raw.contains("No address associated", true) ||
                raw.contains("UnknownHost", true) ->
                t(
                    "تعذر الاتصال بالخادم عبر الشبكة الحالية، وسيتم استخدام مسار اتصال بديل تلقائيًا.",
                    "The server could not be reached on the current network; an alternate connection path is used automatically."
                )
            raw.contains("timeout", true) || raw.contains("timed out", true) ->
                t("انتهت مهلة الاتصال بالخادم. تحقق من الإنترنت وأعد المحاولة.", "The server connection timed out. Check connectivity and try again.")
            else -> t("تعذر إكمال الاتصال بالخادم. أعد المحاولة بعد التحقق من الشبكة.", "Could not complete the server request. Check the network and try again.")
        }
    }

    private fun formatTime(value: Long) =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(value))

    private fun showError(title: String, message: String, onDone: (() -> Unit)? = null) {
        if (!alive()) {
            onDone?.invoke()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(t("حسنًا", "OK")) { _, _ -> onDone?.invoke() }
            .show()
    }

    companion object {
        private const val REQ_RECEIVER_QR = 9317
        private const val KEY_TAB = "receiver_center_tab"
        private const val KEY_RECEIVER_ID = "receiver_center_selected"
        private const val META_SERVER_SYNC = "server_sync"
        private const val META_PERMISSION_UPDATE = "permission_update"
        private const val META_PENDING_DELETES = "pending_receiver_deletes_v139"
    }
}
