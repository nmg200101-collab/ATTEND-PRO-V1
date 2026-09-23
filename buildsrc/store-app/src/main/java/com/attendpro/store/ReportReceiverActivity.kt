package com.attendpro.store

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.attendpro.core.AppLanguage
import com.attendpro.core.CentralServerClient
import com.attendpro.core.QrCodeTools
import com.attendpro.core.QrScannerActivity
import com.attendpro.core.ReportProtocol
import com.attendpro.core.ReportReceiverStore
import com.attendpro.core.ReceiverStoreBinding
import com.attendpro.core.ReceiverOfflineReportProtocol
import com.attendpro.core.UiKit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * V138 multi-store receiver UI.
 *
 * One receiver identity can be linked to multiple stores. Each store has independent
 * permissions, reports, messages and employee administration state.
 * The Activity view hierarchy is installed exactly once. Network callbacks update state
 * and render the active section only; they never call setContentView or reopen a modal.
 * Each remote domain has its own in-flight guard and generation token so stale callbacks
 * cannot replace newer state.
 */
class ReportReceiverActivity : Activity() {
    private enum class Section { STORES, STATUS, REPORTS, MESSAGES }

    private data class ReplyRow(val employeeId: String, val body: String, val createdAt: Long)

    private lateinit var receiver: ReportReceiverStore
    private val p by lazy { UiKit.palette(this) }
    private lateinit var header: LinearLayout
    private lateinit var tabs: LinearLayout
    private lateinit var content: LinearLayout

    private var section = Section.STORES
    private var showIdentityQr = false
    private var selectedReportIndex: Int? = null
    private var messageEmployees: List<CentralServerClient.ReceiverEmployee> = emptyList()
    private var messageReplies: List<ReplyRow> = emptyList()
    private var selectedMessageEmployeeId: String? = null
    private var notice = ""
    private var lastServerRefreshAt = 0L
    private var lanStatus = ""
    private var bleStatus = ""
    private var bluetoothPermissionAsked = false
    private var dataEventsRegistered = false
    private val dataEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!alive() || intent?.action != ReceiverReportService.ACTION_DATA_CHANGED) return
            val storeId = intent.getStringExtra(ReceiverReportService.EXTRA_STORE_ID).orEmpty()
            val activeId = receiver.activeBinding()?.storeId.orEmpty()
            if (storeId.isNotBlank() && activeId.isNotBlank() && storeId != activeId) return
            when (intent.getStringExtra(ReceiverReportService.EXTRA_KIND).orEmpty()) {
                ReceiverReportService.KIND_REPORTS -> {
                    notice = t("وصل تقرير جديد تلقائيًا ✓", "A new report arrived automatically ✓")
                    if (selectedReportIndex == null) render()
                }
                ReceiverReportService.KIND_MESSAGES -> {
                    notice = t("وصل رد جديد من موظف ✓", "A new employee reply arrived ✓")
                    if (section == Section.MESSAGES) render()
                }
                ReceiverReportService.KIND_EMPLOYEES -> {
                    loadCachedEmployees(storeId.ifBlank { activeId })
                    if (section == Section.MESSAGES) render()
                }
                ReceiverReportService.KIND_OUTBOX -> {
                    if (section == Section.MESSAGES) render()
                }
                ReceiverReportService.KIND_BINDINGS -> {
                    refreshStoreBindings(silent = true, refreshCurrentSection = true)
                }
            }
        }
    }
    private var lanReportServer: ReceiverReportLanServer? = null
    private var bleReportServer: ReceiverReportBleServer? = null

    @Volatile private var storesInFlight = false
    @Volatile private var capabilitiesInFlight = false
    @Volatile private var reportsInFlight = false
    @Volatile private var messagesInFlight = false

    @Volatile private var storesGeneration = 0L
    @Volatile private var capabilitiesGeneration = 0L
    @Volatile private var reportsGeneration = 0L
    @Volatile private var messagesGeneration = 0L
    @Volatile private var actionGeneration = 0L

    private fun t(ar: String, en: String) = AppLanguage.text(this, ar, en)
    private fun alive() = !isFinishing && !isDestroyed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiver = ReportReceiverStore(this)
        receiver.resolvedReceiverName()
        if (receiver.storeBindings().any { it.active }) ReceiverReportService.ensureStarted(this)
        section = savedInstanceState?.getString(KEY_SECTION)
            ?.let { runCatching { Section.valueOf(it) }.getOrNull() }
            ?: if (receiver.storeBindings().size > 1) Section.STORES else Section.STATUS
        selectedReportIndex = savedInstanceState?.takeIf { it.containsKey(KEY_REPORT_INDEX) }
            ?.getInt(KEY_REPORT_INDEX)
        handleIncoming(intent)
        installUiOnce()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_SECTION, section.name)
        selectedReportIndex?.let { outState.putInt(KEY_REPORT_INDEX, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleIncoming(intent)
            if (alive()) render()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!dataEventsRegistered) {
            val filter = IntentFilter(ReceiverReportService.ACTION_DATA_CHANGED)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(dataEventsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(dataEventsReceiver, filter)
            }
            dataEventsRegistered = true
        }
    }

    override fun onStop() {
        if (dataEventsRegistered) {
            runCatching { unregisterReceiver(dataEventsReceiver) }
            dataEventsRegistered = false
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::receiver.isInitialized) {
            startLocalReportChannels()
            ReceiverReportService.requestImmediateSync(this)
            if (receiver.storeBindings().isNotEmpty()) {
                refreshStoreBindings(silent = true, refreshCurrentSection = true)
            }
        }
    }

    override fun onDestroy() {
        storesGeneration++
        capabilitiesGeneration++
        reportsGeneration++
        messagesGeneration++
        actionGeneration++
        super.onDestroy()
    }

    private fun installUiOnce() {
        window.statusBarColor = p.bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = if (AppLanguage.isEnglish(this@ReportReceiverActivity)) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
            setPadding(14, 18, 14, 28)
            setBackgroundColor(p.bg)
        }
        header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabs = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(header)
        root.addView(tabs)
        root.addView(content)
        root.addView(UiKit.card(this, p, 6).apply {
            addView(UiKit.button(this@ReportReceiverActivity, p, t("رجوع", "Back"), false).apply {
                setOnClickListener { finish() }
            })
        })
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(p.bg)
            addView(root)
        })
    }

    private fun handleIncoming(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND && i.type == "text/plain") {
            i.getStringExtra(Intent.EXTRA_TEXT)
                ?.takeIf { it.isNotBlank() }
                ?.let { receiver.receive(it) }
        }
    }

    private fun render() {
        if (!alive() || !::content.isInitialized) return
        coerceSectionToPermissions()
        renderHeader()
        renderTabs()
        content.removeAllViews()
        if (notice.isNotBlank()) {
            content.addView(UiKit.card(this, p, 7).apply {
                addView(UiKit.subtitle(this@ReportReceiverActivity, p, notice).apply { gravity = Gravity.CENTER })
            })
        }
        when (section) {
            Section.STORES -> renderStores()
            Section.STATUS -> renderStatus()
            Section.REPORTS -> renderReports()
            Section.MESSAGES -> renderMessages()
        }
    }

    private fun renderHeader() {
        header.removeAllViews()
        val active = receiver.activeBinding()
        val storeCount = receiver.storeBindings().count { it.active }
        header.addView(UiKit.heroCard(this, p, 10).apply {
            addView(UiKit.title(this@ReportReceiverActivity, p, t("هاتف الاستلام", "Receiver Phone"), 24f).apply {
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
            })
            addView(UiKit.subtitle(this@ReportReceiverActivity, p, t(
                "${active?.storeName ?: "غير مرتبط"} • ${active?.branchId ?: "—"}\n${receiver.resolvedReceiverName()} • المحلات المرتبطة: $storeCount",
                "${active?.storeName ?: "Not linked"} • ${active?.branchId ?: "—"}\n${receiver.resolvedReceiverName()} • Linked stores: $storeCount"
            )).apply {
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
            })
        })
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        val card = UiKit.card(this, p, 7)
        addTab(card, Section.STORES, t("المحلات", "Stores"))
        if (receiver.activeBinding() != null) {
            addTab(card, Section.STATUS, t("الحالة", "Status"))
            if (receiver.canReceiveReports) addTab(card, Section.REPORTS, t("التقارير", "Reports"))
            if (receiver.canMessageEmployees) addTab(card, Section.MESSAGES, t("الرسائل", "Messages"))
        }
        tabs.addView(card)
    }

    private fun addTab(card: LinearLayout, target: Section, label: String) {
        card.addView(UiKit.button(this, p, label, section == target).apply {
            setOnClickListener {
                section = target
                notice = ""
                selectedReportIndex = null
                render()
                if (target == Section.MESSAGES || target == Section.REPORTS) {
                    ReceiverReportService.requestImmediateSync(this@ReportReceiverActivity)
                }
                refreshSelectedSection(silent = true)
            }
        })
    }

    private fun coerceSectionToPermissions() {
        if (receiver.activeBinding() == null) {
            section = Section.STORES
            return
        }
        if (section == Section.REPORTS && !receiver.canReceiveReports) section = Section.STATUS
        if (section == Section.MESSAGES && !receiver.canMessageEmployees) section = Section.STATUS
    }

    private fun renderStores() {
        val bindings = receiver.storeBindings()
        val activeId = receiver.activeBinding()?.storeId.orEmpty()
        content.addView(UiKit.card(this, p, 9).apply {
            addView(UiKit.sectionLabel(this@ReportReceiverActivity, p, t("المحلات المرتبطة", "Linked stores")))
            addView(UiKit.subtitle(this@ReportReceiverActivity, p, t(
                "يمكن لهاتف الاستلام إدارة أكثر من محل. كل محل يحتفظ بصلاحياته وتقاريره ورسائله وموظفيه بصورة مستقلة.",
                "This receiver can manage multiple stores. Each store keeps independent permissions, reports, messages and employees."
            )))
            addView(UiKit.button(this@ReportReceiverActivity, p,
                if (storesInFlight) t("جاري تحديث المحلات…", "Refreshing stores…") else t("تحديث قائمة المحلات", "Refresh store list"),
                false
            ).apply {
                isEnabled = !storesInFlight
                setOnClickListener { refreshStoreBindings(silent = false, refreshCurrentSection = false) }
            })
            addView(UiKit.button(this@ReportReceiverActivity, p, t("＋ إضافة محل جديد", "＋ Add store"), false).apply {
                setOnClickListener { showIdentityQr = !showIdentityQr; render() }
            })
            addView(UiKit.button(this@ReportReceiverActivity, p,
                if (ReceiverReportService.nearbyPairingActive(this@ReportReceiverActivity))
                    t("✓ الارتباط القريب مفعّل", "✓ Nearby linking active")
                else t("تفعيل الارتباط القريب لمدة دقيقتين", "Enable nearby linking for 2 minutes"),
                false
            ).apply {
                setOnClickListener {
                    ReceiverReportService.enableNearbyPairing(this@ReportReceiverActivity)
                    notice = t(
                        "تم تفعيل الارتباط القريب لمدة دقيقتين. من جهاز المحل اضغط «اكتشاف هاتف قريب».",
                        "Nearby linking is active for 2 minutes. On the Store device tap Discover nearby phone."
                    )
                    render()
                }
            })
            addView(UiKit.button(this@ReportReceiverActivity, p, t("مسح QR الربط النهائي للمحل", "Scan store final link QR"), false).apply {
                setOnClickListener { scanGrant() }
            })
        })

        if (showIdentityQr) {
            val raw = ReportProtocol.encodeInvite(receiver.newInvite())
            val qr = runCatching { QrCodeTools.bitmap(raw, 700) }.getOrNull()
            content.addView(UiKit.card(this, p, 8).apply {
                addView(UiKit.sectionLabel(this@ReportReceiverActivity, p, t("QR تعريف هاتف الاستلام", "Receiver identity QR")))
                addView(UiKit.subtitle(this@ReportReceiverActivity, p, t(
                    "في جهاز المحل الجديد افتح «هواتف الاستلام والصلاحيات» ← «إضافة هاتف» ثم امسح هذا الرمز.",
                    "On the new Store device open Receiver phones & permissions → Add phone, then scan this code."
                )))
                if (qr != null) {
                    addView(ImageView(this@ReportReceiverActivity).apply {
                        setImageBitmap(qr)
                        adjustViewBounds = true
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 700)
                    })
                }
            })
        }

        if (bindings.isEmpty()) {
            content.addView(UiKit.card(this, p, 8).apply {
                addView(UiKit.subtitle(this@ReportReceiverActivity, p, t(
                    "لا يوجد محل مرتبط بعد. أضف أول محل باستخدام QR.",
                    "No store is linked yet. Add the first store using QR."
                )))
            })
            return
        }

        bindings.forEach { binding ->
            content.addView(UiKit.card(this, p, 8).apply {
                val selected = binding.storeId.isNotBlank() && binding.storeId == activeId
                val state = if (binding.active) t("نشط", "Active") else t("موقوف", "Disabled")
                val last = if (binding.lastServerRefreshAt > 0L) formatTime(binding.lastServerRefreshAt) else t("لم يتم بعد", "Not yet")
                addView(UiKit.title(this@ReportReceiverActivity, p,
                    "${if (selected) "✓ " else ""}${binding.storeName} • ${binding.branchId}", 18f))
                addView(UiKit.subtitle(this@ReportReceiverActivity, p, t(
                    "الحالة: $state\nآخر مزامنة: $last\nالصلاحيات: ${permissionsSummary(binding)}",
                    "Status: $state\nLast sync: $last\nPermissions: ${permissionsSummary(binding)}"
                )))
                if (binding.active) {
                    addView(UiKit.button(this@ReportReceiverActivity, p,
                        if (selected) t("المحل الحالي", "Current store") else t("فتح هذا المحل", "Open this store"),
                        selected
                    ).apply {
                        isEnabled = !selected
                        setOnClickListener { switchStore(binding) }
                    })
                }
                if (binding.storeId.isNotBlank()) {
                    addView(UiKit.button(this@ReportReceiverActivity, p, t("حذف / فك ارتباط هذا المحل", "Remove / unlink this store"), false).apply {
                        setOnClickListener { confirmUnlinkStore(binding) }
                    })
                } else {
                    addView(UiKit.subtitle(this@ReportReceiverActivity, p, t(
                        "ربط قديم من V137 — اضغط تحديث قائمة المحلات لترقيته تلقائيًا.",
                        "Legacy V137 link — refresh the store list to upgrade it automatically."
                    )))
                }
            })
        }
    }

    private fun permissionsSummary(binding: ReceiverStoreBinding): String {
        val parts = mutableListOf<String>()
        if (binding.canReceiveReports) parts += t("تقارير", "Reports")
        if (binding.canMessageEmployees) parts += t("رسائل", "Messages")
        return if (parts.isEmpty()) t("بدون صلاحيات", "No permissions") else parts.joinToString(" • ")
    }

    private fun switchStore(binding: ReceiverStoreBinding) {
        if (!binding.active || binding.storeId.isBlank()) return
        invalidateRemoteRequests()
        if (!receiver.selectStore(binding.storeId)) return
        lastServerRefreshAt = binding.lastServerRefreshAt
        selectedReportIndex = null
        selectedMessageEmployeeId = null
        messageEmployees = emptyList()
        messageReplies = emptyList()
        section = Section.STATUS
        notice = t("تم اختيار ${binding.storeName}.", "${binding.storeName} selected.")
        render()
        refreshCapabilities(silent = true, refreshCurrentSection = false)
    }

    private fun confirmUnlinkStore(binding: ReceiverStoreBinding) {
        if (!alive() || binding.storeId.isBlank()) return
        AlertDialog.Builder(this)
            .setTitle(t("فك ارتباط المحل", "Unlink store"))
            .setMessage(t(
                "سيتم حذف ارتباط «${binding.storeName}» من هاتف الاستلام فقط. لن يُحذف المحل أو الموظفون أو سجلات الحضور.",
                "Only the link to “${binding.storeName}” will be removed from this receiver. Store, employees and attendance records will not be deleted."
            ))
            .setPositiveButton(t("فك الارتباط", "Unlink")) { _, _ -> unlinkStore(binding) }
            .setNegativeButton(t("إلغاء", "Cancel"), null)
            .show()
    }

    private fun unlinkStore(binding: ReceiverStoreBinding) {
        if (storesInFlight || binding.storeId.isBlank()) return
        invalidateRemoteRequests()
        receiver.removeStoreBinding(binding.storeId)
        section = Section.STORES
        notice = t(
            "تم فك الارتباط من الهاتف فورًا ✓ ويجري تنظيف الربط من الخادم في الخلفية.",
            "Link removed from this phone immediately ✓. Server cleanup continues in the background."
        )
        render()

        if (binding.serverUrl.isNotBlank()) {
            ReceiverReportService.queueServerUnlink(this, binding.serverUrl, binding.storeId)
        }
        ReceiverReportService.stopIfUnused(this)
    }

    private fun renderStatus() {
        val binding = receiver.activeBinding()
        val linked = binding != null && binding.serverUrl.isNotBlank()
        content.addView(UiKit.card(this, p, 9).apply {
            addView(UiKit.sectionLabel(this@ReportReceiverActivity, p, t("حالة المحل الحالي", "Current store status")))
            addView(UiKit.title(this@ReportReceiverActivity, p,
                if (linked) t("✓ مرتبط بالخادم", "✓ Server linked") else t("غير مرتبط", "Not linked"), 20f))
            val last = binding?.lastServerRefreshAt?.takeIf { it > 0L }?.let { formatTime(it) } ?: t("لم يتم بعد", "Not yet")
            addView(UiKit.subtitle(this@ReportReceiverActivity, p, t(
                "اسم الهاتف: ${receiver.receiverName}\nالمعرف: ${receiver.receiverId}\nالمحل: ${binding?.storeName ?: "—"}\nالفرع: ${binding?.branchId ?: "—"}\nآخر اتصال بالخادم: $last",
                "Phone: ${receiver.receiverName}\nID: ${receiver.receiverId}\nStore: ${binding?.storeName ?: "—"}\nBranch: ${binding?.branchId ?: "—"}\nLast server contact: $last"
            )))
            addView(UiKit.button(this@ReportReceiverActivity, p, t("إدارة المحلات المرتبطة", "Manage linked stores"), false).apply {
                setOnClickListener { section = Section.STORES; notice = ""; render() }
            })
            if (linked) {
                addView(UiKit.button(this@ReportReceiverActivity, p,
                    if (capabilitiesInFlight) t("جاري التحديث…", "Refreshing…") else t("تحديث صلاحيات هذا المحل", "Refresh this store permissions"),
                    false
                ).apply {
                    isEnabled = !capabilitiesInFlight
                    setOnClickListener { refreshCapabilities(silent = false, refreshCurrentSection = false) }
                })
            }
        })

        content.addView(UiKit.card(this, p, 9).apply {
            addView(UiKit.sectionLabel(this@ReportReceiverActivity, p, t("الاستقبال القريب بدون إنترنت", "Nearby offline receiving")))
            val btMissing = ReceiverReportBluetoothSupport.missingPermissions(this@ReportReceiverActivity)
            addView(UiKit.subtitle(this@ReportReceiverActivity, p, t(
                "Wi‑Fi / Hotspot / LAN: ${lanStatus.ifBlank { "جاري التجهيز…" }}\nBluetooth: ${bleStatus.ifBlank { if (btMissing.isEmpty()) "جاري التجهيز…" else "يحتاج صلاحيات Bluetooth" }}",
                "Wi‑Fi / Hotspot / LAN: ${lanStatus.ifBlank { "Starting…" }}\nBluetooth: ${bleStatus.ifBlank { if (btMissing.isEmpty()) "Starting…" else "Bluetooth permissions required" }}"
            )))
            if (btMissing.isNotEmpty()) {
                addView(UiKit.button(this@ReportReceiverActivity, p, t("منح صلاحيات Bluetooth", "Grant Bluetooth permissions"), false).apply {
                    setOnClickListener {
                        bluetoothPermissionAsked = true
                        ReceiverReportBluetoothSupport.request(this@ReportReceiverActivity, REQ_BLUETOOTH_RECEIVER)
                    }
                })
            }
        })

        content.addView(UiKit.card(this, p, 9).apply {
            addView(UiKit.sectionLabel(this@ReportReceiverActivity, p, t("الصلاحيات لهذا المحل", "Permissions for this store")))
            addView(UiKit.subtitle(this@ReportReceiverActivity, p,
                "${mark(receiver.canReceiveReports)} ${t("استلام التقارير", "Receive reports")}\n" +
                    "${mark(receiver.canMessageEmployees)} ${t("مراسلة الموظفين", "Message employees")}\n"
            ))
            addView(UiKit.subtitle(this@ReportReceiverActivity, p, t(
                "هاتف الاستلام مخصص للتقارير ومراسلة الموظفين فقط، ولا يملك صلاحيات إدارة المحل أو النظام.",
                "The receiver phone is limited to reports and employee messaging only; Store and system administration are not available."
            )))
        })
    }

    private fun renderReports() {
        val card = UiKit.card(this, p, 9)
        val binding = receiver.activeBinding()
        binding?.storeId?.let { loadCachedEmployees(it) }
        card.addView(UiKit.sectionLabel(this, p, t(
            "التقارير — ${binding?.storeName ?: "—"}",
            "Reports — ${binding?.storeName ?: "—"}"
        )))
        card.addView(UiKit.button(this, p,
            if (reportsInFlight) t("جاري تحديث التقارير…", "Refreshing reports…") else t("تحديث التقارير", "Refresh reports")
        ).apply {
            isEnabled = !reportsInFlight
            setOnClickListener { refreshReports(silent = false) }
        })

        val items = receiver.receivedReports(binding?.storeId.orEmpty())
        val selected = selectedReportIndex?.let { items.getOrNull(it) }
        if (selected != null) {
            card.addView(UiKit.title(this, p, "${selected.storeName} — ${selected.periodLabel}", 18f))
            card.addView(UiKit.subtitle(this, p, selected.reportText))
            card.addView(UiKit.button(this, p, t("العودة لقائمة التقارير", "Back to reports"), false).apply {
                setOnClickListener { selectedReportIndex = null; render() }
            })
        } else {
            card.addView(UiKit.subtitle(this, p, if (items.isEmpty())
                t("لا توجد تقارير مستلمة بعد.", "No reports received yet.")
            else t("التقارير المستلمة: ${items.size}", "Received reports: ${items.size}")))
            items.take(30).forEachIndexed { index, x ->
                val d = formatTime(x.receivedAt)
                card.addView(UiKit.button(this, p, "${x.storeName} • ${x.branchId}\n${x.periodLabel} • $d", false).apply {
                    setOnClickListener { selectedReportIndex = index; render() }
                })
            }
        }
        content.addView(card)
    }

    private fun loadCachedEmployees(storeId: String) {
        if (storeId.isBlank()) return
        val cached = receiver.cachedEmployees(storeId)
        if (cached.isNotEmpty()) {
            messageEmployees = cached.map {
                CentralServerClient.ReceiverEmployee(
                    it.employeeId,
                    it.employeeName,
                    it.branchId,
                    it.lastSeenAt
                )
            }
            if (selectedMessageEmployeeId !in messageEmployees.map { it.employeeId }) {
                selectedMessageEmployeeId = null
            }
        }
    }

    private fun renderMessages() {
        val card = UiKit.card(this, p, 9)
        val binding = receiver.activeBinding()
        card.addView(UiKit.sectionLabel(this, p, t(
            "الرسائل — ${binding?.storeName ?: "—"}",
            "Messages — ${binding?.storeName ?: "—"}"
        )))
        card.addView(UiKit.button(this, p,
            if (messagesInFlight) t("جاري التحديث…", "Refreshing…") else t("تحديث الرسائل والردود", "Refresh messages and replies")
        ).apply {
            isEnabled = !messagesInFlight
            setOnClickListener { refreshMessages(silent = false) }
        })

        if (messageEmployees.isEmpty()) {
            card.addView(UiKit.subtitle(this, p, t(
                "جاري جلب قائمة الموظفين… ستظهر تلقائيًا عند وصولها.",
                "Loading employees… They will appear automatically."
            )))
        } else {
            card.addView(UiKit.subtitle(this, p, t("اختر موظفًا:", "Choose an employee:")))
            messageEmployees.forEach { employee ->
                val selected = selectedMessageEmployeeId == employee.employeeId
                card.addView(UiKit.button(this, p, "${employee.employeeName} • ${employee.employeeId}", selected).apply {
                    setOnClickListener { selectedMessageEmployeeId = employee.employeeId; render() }
                })
            }
        }

        val target = messageEmployees.firstOrNull { it.employeeId == selectedMessageEmployeeId }
        if (target != null) {
            val field = UiKit.field(this, p, t("اكتب الرسالة إلى ${target.employeeName}", "Write a message to ${target.employeeName}"))
            card.addView(field)
            card.addView(UiKit.button(this, p,
                t("إرسال الآن", "Send now")
            ).apply {
                isEnabled = true
                setOnClickListener {
                    val body = field.text.toString().trim()
                    if (body.isBlank()) {
                        field.error = t("الرسالة فارغة", "Message is empty")
                    } else {
                        sendMessage(target.employeeId, body)
                    }
                }
            })
        }

        val outgoing = receiver.outgoingMessages(binding?.storeId.orEmpty()).take(20)
        if (outgoing.isNotEmpty()) {
            card.addView(UiKit.sectionLabel(this, p, t("حالة الإرسال", "Delivery status")))
            outgoing.forEach { row ->
                val employeeName = messageEmployees.firstOrNull { it.employeeId == row.employeeId }?.employeeName
                    ?: row.employeeId
                val stateText = when (row.state) {
                    "SENT" -> t("✓ تم الإرسال", "✓ Sent")
                    "FAILED" -> t("تعذر الإرسال", "Failed")
                    "RETRY" -> t("إعادة محاولة تلقائية", "Automatic retry")
                    else -> t("قيد الإرسال…", "Sending…")
                }
                card.addView(UiKit.subtitle(this, p,
                    "$employeeName • $stateText\n${row.body}\n${formatTime(row.createdAt)}"
                ))
                if (row.state == "FAILED") {
                    card.addView(UiKit.button(this, p, t("إعادة محاولة الإرسال", "Retry sending"), false).apply {
                        setOnClickListener {
                            if (receiver.retryOutgoingMessage(row.localId)) {
                                ReceiverReportService.requestImmediateSync(this@ReportReceiverActivity)
                                notice = t("تمت إعادة الرسالة إلى صف الإرسال ✓", "Message returned to the delivery queue ✓")
                                render()
                            }
                        }
                    })
                }
            }
        }

        card.addView(UiKit.sectionLabel(this, p, t("الردود المستلمة", "Received replies")))
        val cachedReplies = receiver.receivedMessageReplies(binding?.storeId.orEmpty()).map {
            ReplyRow(it.employeeId, it.body, it.createdAt)
        }
        val visibleReplies = (messageReplies + cachedReplies)
            .distinctBy { "${it.employeeId}|${it.createdAt}|${it.body}" }
            .sortedByDescending { it.createdAt }
        if (visibleReplies.isEmpty()) {
            card.addView(UiKit.subtitle(this, p, t("لا توجد ردود.", "No replies.")))
        } else {
            visibleReplies.take(80).forEach { row ->
                val whenText = if (row.createdAt > 0L) " • ${formatTime(row.createdAt)}" else ""
                card.addView(UiKit.subtitle(this, p, "${row.employeeId}$whenText\n${row.body}"))
            }
        }
        content.addView(card)
    }


    private fun scanGrant() {
        startActivityForResult(
            Intent(this, QrScannerActivity::class.java)
                .putExtra(QrScannerActivity.EXTRA_PROMPT, t("امسح QR الربط النهائي", "Scan final link QR")),
            REQ_GRANT
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_GRANT || resultCode != RESULT_OK) return
        val raw = data?.getStringExtra(QrScannerActivity.EXTRA_RESULT).orEmpty()
        val grant = ReportProtocol.decodeRemoteGrant(raw, receiver.receiverId)
        if (grant == null) {
            showError(t("QR غير صالح", "Invalid QR"), t("الرمز غير صالح أو منتهي.", "The code is invalid or expired."))
            return
        }
        invalidateRemoteRequests()
        val binding = receiver.upsertBinding(grant)
        section = Section.STORES
        notice = t(
            "تم حفظ ارتباط ${binding.storeName}. جارٍ التحقق من الخادم.",
            "${binding.storeName} link saved. Verifying with server."
        )
        render()
        ReceiverReportService.ensureStarted(this)
        startLocalReportChannels()
        if (binding.serverUrl.isNotBlank()) {
            refreshCapabilities(silent = false, refreshCurrentSection = false)
        } else {
            notice = t("تم ربط المحل محليًا. التقارير القريبة تعمل عبر LAN/Bluetooth.", "Store linked locally. Nearby reports work over LAN/Bluetooth.")
            render()
        }
    }

    private fun refreshStoreBindings(silent: Boolean, refreshCurrentSection: Boolean) {
        val seed = receiver.activeBinding() ?: receiver.storeBindings().firstOrNull() ?: return
        if (seed.serverUrl.isBlank() || storesInFlight) return
        storesInFlight = true
        val generation = ++storesGeneration
        if (!silent && alive()) render()

        Thread {
            val result = CentralServerClient.receiverStores(seed.serverUrl, receiver.receiverId, receiver.secret)
            runOnUiThread {
                if (generation != storesGeneration) return@runOnUiThread
                storesInFlight = false
                if (!alive()) return@runOnUiThread
                if (result.isSuccess) {
                    val now = System.currentTimeMillis()
                    val remote = result.getOrThrow().map { x ->
                        ReceiverStoreBinding(
                            storeId = x.storeId,
                            serverUrl = seed.serverUrl,
                            storeName = x.storeName,
                            branchId = x.branchId,
                            active = x.active,
                            canReceiveReports = x.canReceiveReports,
                            canMessageEmployees = x.canMessageEmployees,
                            canManageStore = false,
                            linkedAt = now,
                            lastServerRefreshAt = now,
                            storeLastSeenAt = x.storeLastSeenAt
                        )
                    }
                    receiver.syncBindingsFromServer(remote)
                    receiver.activeBinding()?.let { lastServerRefreshAt = it.lastServerRefreshAt }
                    if (!silent) notice = t("تم تحديث قائمة المحلات ✓", "Store list refreshed ✓")
                    render()
                    if (refreshCurrentSection && section != Section.STORES) {
                        refreshCapabilities(silent = true, refreshCurrentSection = true)
                    }
                } else {
                    if (!silent) showError(t("تعذر تحديث المحلات", "Could not refresh stores"), networkMessage(result.exceptionOrNull()))
                    render()
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun refreshCapabilities(silent: Boolean, refreshCurrentSection: Boolean) {
        val binding = receiver.activeBinding()
        if (binding == null || binding.serverUrl.isBlank()) {
            if (!silent) showError(t("غير مرتبط", "Not linked"), t("اختر أو أضف محلًا أولًا.", "Select or add a store first."))
            return
        }
        if (capabilitiesInFlight) return
        capabilitiesInFlight = true
        val generation = ++capabilitiesGeneration
        val requestedStoreId = binding.storeId
        val requestedServer = binding.serverUrl
        if (!silent && alive()) render()

        Thread {
            val result = CentralServerClient.receiverCapabilities(
                requestedServer, receiver.receiverId, receiver.secret, requestedStoreId
            )
            runOnUiThread {
                if (generation != capabilitiesGeneration) return@runOnUiThread
                capabilitiesInFlight = false
                if (!alive()) return@runOnUiThread
                val current = receiver.activeBinding()
                if (current == null || current.serverUrl != requestedServer ||
                    (requestedStoreId.isNotBlank() && current.storeId != requestedStoreId)) return@runOnUiThread

                if (result.isSuccess) {
                    val x = result.getOrThrow()
                    val now = System.currentTimeMillis()
                    receiver.updateActiveBinding(
                        storeId = x.storeId.ifBlank { requestedStoreId },
                        storeName = x.storeName,
                        branchId = x.branchId,
                        canReceiveReports = x.canReceiveReports,
                        canMessageEmployees = x.canMessageEmployees,
                        canManageStore = false,
                        lastServerRefreshAt = now,
                        storeLastSeenAt = current.storeLastSeenAt
                    )
                    lastServerRefreshAt = now
                    notice = if (silent) "" else t("تم تحديث صلاحيات المحل والحالة ✓", "Store permissions and status refreshed ✓")
                    render()
                    if (refreshCurrentSection) refreshSelectedSection(silent = true)
                    if (requestedStoreId.isBlank()) refreshStoreBindings(silent = true, refreshCurrentSection = false)
                } else {
                    if (!silent) showError(t("تعذر التحديث", "Refresh failed"), networkMessage(result.exceptionOrNull()))
                    render()
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun refreshSelectedSection(silent: Boolean) {
        when (section) {
            Section.STORES -> refreshStoreBindings(silent, refreshCurrentSection = false)
            Section.STATUS -> Unit
            Section.REPORTS -> if (receiver.canReceiveReports) refreshReports(silent)
            Section.MESSAGES -> if (receiver.canMessageEmployees) refreshMessages(silent)
        }
    }

    private fun refreshReports(silent: Boolean) {
        val binding = receiver.activeBinding() ?: return
        if (!receiver.canReceiveReports || binding.serverUrl.isBlank() || binding.storeId.isBlank() || reportsInFlight) return
        reportsInFlight = true
        val generation = ++reportsGeneration
        val storeId = binding.storeId
        if (alive()) render()

        Thread {
            val result = CentralServerClient.receiverInbox(binding.serverUrl, receiver.receiverId, receiver.secret, storeId)
            runOnUiThread {
                if (generation != reportsGeneration || receiver.activeBinding()?.storeId != storeId) return@runOnUiThread
                reportsInFlight = false
                if (!alive()) return@runOnUiThread
                if (result.isSuccess) {
                    result.getOrThrow().forEach { remote ->
                        val item = receiver.receive(remote.packageText, storeId)
                        if (item != null) {
                            CentralServerClient.confirmRemoteReport(
                                binding.serverUrl,
                                receiver.receiverId,
                                receiver.secret,
                                item.transferId,
                                item.confirmationCode,
                                storeId
                            )
                        }
                    }
                    markActiveServerContact(storeId)
                    if (!silent) notice = t("تم تحديث تقارير المحل ✓", "Store reports refreshed ✓")
                } else if (!silent) {
                    showError(t("تعذر تحديث التقارير", "Could not refresh reports"), networkMessage(result.exceptionOrNull()))
                }
                render()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun refreshMessages(silent: Boolean) {
        val binding = receiver.activeBinding() ?: return
        if (!receiver.canMessageEmployees || binding.serverUrl.isBlank() || binding.storeId.isBlank()) return
        val storeId = binding.storeId

        loadCachedEmployees(storeId)
        messageReplies = receiver.receivedMessageReplies(storeId)
            .map { ReplyRow(it.employeeId, it.body, it.createdAt) }
        if (alive()) render()

        ReceiverReportService.requestImmediateSync(this)

        if (messagesInFlight) return
        messagesInFlight = true
        val generation = ++messagesGeneration

        Thread {
            val employees = CentralServerClient.receiverEmployees(
                binding.serverUrl, receiver.receiverId, receiver.secret, storeId
            )
            if (employees.isSuccess) {
                receiver.cacheEmployees(storeId, employees.getOrThrow())
            }

            val replies = CentralServerClient.receiverMessagesInbox(
                binding.serverUrl, receiver.receiverId, receiver.secret, storeId
            )
            if (replies.isSuccess) {
                receiver.cacheMessageReplies(storeId, replies.getOrThrow())
            }

            runOnUiThread {
                if (generation != messagesGeneration || receiver.activeBinding()?.storeId != storeId) return@runOnUiThread
                messagesInFlight = false
                if (!alive()) return@runOnUiThread
                loadCachedEmployees(storeId)
                messageReplies = receiver.receivedMessageReplies(storeId)
                    .map { ReplyRow(it.employeeId, it.body, it.createdAt) }
                if (employees.isSuccess || replies.isSuccess) {
                    markActiveServerContact(storeId)
                    if (!silent) notice = t("تم تحديث الموظفين والرسائل ✓", "Employees and messages refreshed ✓")
                } else if (!silent) {
                    showError(
                        t("تعذر تحديث الرسائل", "Could not refresh messages"),
                        networkMessage(employees.exceptionOrNull() ?: replies.exceptionOrNull())
                    )
                }
                render()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun sendMessage(employeeId: String, body: String) {
        val binding = receiver.activeBinding() ?: return
        if (!receiver.canMessageEmployees || binding.storeId.isBlank()) return
        val queued = receiver.queueOutgoingMessage(
            binding.storeId,
            employeeId,
            t("رسالة من هاتف الاستلام", "Receiver phone message"),
            body,
            "NORMAL"
        )
        if (queued == null) {
            showError(t("تعذر تجهيز الرسالة", "Message could not be queued"), t("تحقق من الموظف ونص الرسالة.", "Check the employee and message text."))
            return
        }
        notice = t(
            "تم حفظ الرسالة للإرسال فورًا ✓ يمكنك متابعة استخدام الشاشة دون انتظار.",
            "Message queued for immediate delivery ✓. You can keep using the screen without waiting."
        )
        ReceiverReportService.requestImmediateSync(this)
        render()
    }


    private fun startLocalReportChannels() {
        if (!::receiver.isInitialized) return
        if (receiver.storeBindings().any { it.active } || ReceiverReportService.nearbyPairingActive(this)) {
            ReceiverReportService.ensureStarted(this)
        }
        lanStatus = t("الخدمة الدائمة جاهزة", "Persistent service ready")
        val missing = ReceiverReportBluetoothSupport.missingPermissions(this)
        bleStatus = when {
            missing.isNotEmpty() -> t("الصلاحيات غير ممنوحة", "Permissions not granted")
            !ReceiverReportBluetoothSupport.bluetoothEnabled(this) -> t("Bluetooth غير مفعّل", "Bluetooth is off")
            else -> t("الخدمة الدائمة جاهزة", "Persistent service ready")
        }
        if (missing.isNotEmpty() && !bluetoothPermissionAsked && Build.VERSION.SDK_INT >= 31) {
            bluetoothPermissionAsked = true
            ReceiverReportBluetoothSupport.request(this, REQ_BLUETOOTH_RECEIVER)
        }
    }

    private fun stopLocalReportChannels() {
        // V140: transports belong to ReceiverReportService and intentionally survive the Activity.
    }

    private fun acceptOfflineReport(envelope: ReceiverOfflineReportProtocol.Envelope): Boolean {
        val binding = receiver.storeBindings().firstOrNull {
            it.storeId == envelope.storeId && it.active && it.canReceiveReports
        } ?: return false
        val received = receiver.receive(envelope.packageText, envelope.storeId) ?: return false
        runOnUiThread {
            if (!alive()) return@runOnUiThread
            if (receiver.activeBinding()?.storeId == envelope.storeId) {
                notice = t(
                    "تم استلام تقرير محلي من ${binding.storeName} ✓",
                    "Local report received from ${binding.storeName} ✓"
                )
                selectedReportIndex = null
                if (section == Section.REPORTS || section == Section.STATUS) render()
            }
        }
        return received.transferId == envelope.transferId
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BLUETOOTH_RECEIVER) {
            bleStatus = if (ReceiverReportBluetoothSupport.hasPermissions(this))
                t("تم منح الصلاحيات — جارٍ تشغيل BLE", "Permissions granted — starting BLE")
            else
                t("صلاحيات Bluetooth غير مكتملة", "Bluetooth permissions incomplete")
            startLocalReportChannels()
            if (alive()) render()
        }
    }

    private fun markActiveServerContact(storeId: String) {
        val current = receiver.activeBinding() ?: return
        if (current.storeId != storeId) return
        val now = System.currentTimeMillis()
        receiver.updateActiveBinding(
            current.storeId, current.storeName, current.branchId,
            current.canReceiveReports, current.canMessageEmployees, false,
            now, current.storeLastSeenAt
        )
        lastServerRefreshAt = now
    }

    private fun invalidateRemoteRequests() {
        capabilitiesGeneration++
        reportsGeneration++
        messagesGeneration++
        actionGeneration++
        capabilitiesInFlight = false
        reportsInFlight = false
        messagesInFlight = false
    }

    private fun mark(v: Boolean) = if (v) "✓" else "— ${t("غير مسموح", "Not allowed")}"

    private fun safeServerError(raw: String): String {
        if (raw.contains("Failed to connect to /", true) || raw.contains("2606:", true) || raw.contains("ENETUNREACH", true)) {
            return t("تعذر الاتصال بالخادم عبر الشبكة الحالية.", "Could not connect to the server on the current network.")
        }
        return raw.take(220)
    }

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
            raw.startsWith("HTTP 404", true) ->
                t("الخدمة المطلوبة غير متاحة على إصدار الخادم الحالي.", "The requested service is unavailable on the current server version.")
            raw.contains("صلاحية") || raw.contains("الموظف") || raw.contains("يوجد أمر") ->
                safeServerError(raw)
            else ->
                t("تعذر تنفيذ الطلب عبر الشبكة الحالية. أعد المحاولة بعد التحقق من الاتصال.", "The request could not be completed on the current network. Check connectivity and try again.")
        }
    }

    private fun showError(title: String, message: String) {
        if (!alive()) return
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(t("حسنًا", "OK"), null)
            .show()
    }


    private fun formatTime(value: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(value))

    companion object {
        private const val REQ_GRANT = 7301
        private const val REQ_BLUETOOTH_RECEIVER = 7302
        private const val KEY_SECTION = "receiver_section"
        private const val KEY_REPORT_INDEX = "receiver_report_index"
    }
}
