package com.attendpro.store

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
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
import com.attendpro.core.ReceiverEmployeeAdminClient
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
    private enum class Section { STORES, STATUS, REPORTS, MESSAGES, EMPLOYEES }
    private enum class EmployeeFilter { ALL, ACTIVE, DISABLED, PENDING, FAILED }

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
    private var managedEmployees: List<ReceiverEmployeeAdminClient.Employee> = emptyList()
    private var employeeEditorOpen = false
    private var editingEmployeeId: String? = null
    private var selectedEmployeeId: String? = null
    private var employeeSearchQuery = ""
    private var employeeFilter = EmployeeFilter.ALL
    private var notice = ""
    private var lastServerRefreshAt = 0L
    private var lanStatus = ""
    private var bleStatus = ""
    private var bluetoothPermissionAsked = false
    private var lanReportServer: ReceiverReportLanServer? = null
    private var bleReportServer: ReceiverReportBleServer? = null

    @Volatile private var storesInFlight = false
    @Volatile private var capabilitiesInFlight = false
    @Volatile private var reportsInFlight = false
    @Volatile private var messagesInFlight = false
    @Volatile private var employeesInFlight = false
    @Volatile private var employeeCommandInFlight = false
    @Volatile private var messageSendInFlight = false

    @Volatile private var storesGeneration = 0L
    @Volatile private var capabilitiesGeneration = 0L
    @Volatile private var reportsGeneration = 0L
    @Volatile private var messagesGeneration = 0L
    @Volatile private var employeesGeneration = 0L
    @Volatile private var actionGeneration = 0L

    private fun t(ar: String, en: String) = AppLanguage.text(this, ar, en)
    private fun alive() = !isFinishing && !isDestroyed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiver = ReportReceiverStore(this)
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

    override fun onResume() {
        super.onResume()
        if (::receiver.isInitialized) {
            startLocalReportChannels()
            if (receiver.storeBindings().isNotEmpty()) {
                refreshStoreBindings(silent = true, refreshCurrentSection = true)
            }
        }
    }

    override fun onDestroy() {
        stopLocalReportChannels()
        storesGeneration++
        capabilitiesGeneration++
        reportsGeneration++
        messagesGeneration++
        employeesGeneration++
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
            Section.EMPLOYEES -> renderEmployees()
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
                "${active?.storeName ?: "غير مرتبط"} • ${active?.branchId ?: "—"}\n${receiver.receiverName} • المحلات المرتبطة: $storeCount",
                "${active?.storeName ?: "Not linked"} • ${active?.branchId ?: "—"}\n${receiver.receiverName} • Linked stores: $storeCount"
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
            if (receiver.canManageStore) addTab(card, Section.EMPLOYEES, t("الموظفون", "Employees"))
        }
        tabs.addView(card)
    }

    private fun addTab(card: LinearLayout, target: Section, label: String) {
        card.addView(UiKit.button(this, p, label, section == target).apply {
            setOnClickListener {
                section = target
                notice = ""
                selectedReportIndex = null
                employeeEditorOpen = false
                selectedEmployeeId = null
                render()
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
        if (section == Section.EMPLOYEES && !receiver.canManageStore) section = Section.STATUS
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
        if (binding.canManageStore) parts += t("موظفون", "Employees")
        return if (parts.isEmpty()) t("بدون صلاحيات", "No permissions") else parts.joinToString(" • ")
    }

    private fun switchStore(binding: ReceiverStoreBinding) {
        if (!binding.active || binding.storeId.isBlank()) return
        invalidateRemoteRequests()
        if (!receiver.selectStore(binding.storeId)) return
        lastServerRefreshAt = binding.lastServerRefreshAt
        selectedReportIndex = null
        selectedMessageEmployeeId = null
        selectedEmployeeId = null
        managedEmployees = emptyList()
        messageEmployees = emptyList()
        messageReplies = emptyList()
        employeeEditorOpen = false
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
        storesInFlight = true
        val generation = ++storesGeneration
        render()
        Thread {
            val result = CentralServerClient.receiverUnlinkStore(
                binding.serverUrl, receiver.receiverId, receiver.secret, binding.storeId
            )
            runOnUiThread {
                if (generation != storesGeneration) return@runOnUiThread
                storesInFlight = false
                if (!alive()) return@runOnUiThread
                if (result.isSuccess) {
                    receiver.removeStoreBinding(binding.storeId)
                    invalidateRemoteRequests()
                    section = Section.STORES
                    notice = t("تم فك ارتباط المحل دون حذف بياناته ✓", "Store unlinked without deleting its data ✓")
                } else {
                    showError(t("تعذر فك الارتباط", "Could not unlink"), networkMessage(result.exceptionOrNull()))
                }
                render()
            }
        }.apply { isDaemon = true }.start()
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
                    "${mark(receiver.canMessageEmployees)} ${t("مراسلة الموظفين", "Message employees")}\n" +
                    "${mark(receiver.canManageStore)} ${t("إدارة الموظفين فقط", "Employee management only")}"
            ))
            addView(UiKit.subtitle(this@ReportReceiverActivity, p, t(
                "إدارة الموظفين لا تمنح إعدادات مدير المحل أو النظام أو الاتصال أو Bluetooth أو GPS أو التفعيل.",
                "Employee management never grants Store, system, connection, Bluetooth, GPS, or activation settings."
            )))
        })
    }

    private fun renderReports() {
        val card = UiKit.card(this, p, 9)
        val binding = receiver.activeBinding()
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

    private fun renderMessages() {
        val card = UiKit.card(this, p, 9)
        val binding = receiver.activeBinding()
        card.addView(UiKit.sectionLabel(this, p, t(
            "الرسائل — ${binding?.storeName ?: "—"}",
            "Messages — ${binding?.storeName ?: "—"}"
        )))
        card.addView(UiKit.button(this, p,
            if (messagesInFlight) t("جاري التحديث…", "Refreshing…") else t("تحديث الموظفين والردود", "Refresh employees and replies")
        ).apply {
            isEnabled = !messagesInFlight
            setOnClickListener { refreshMessages(silent = false) }
        })

        if (messageEmployees.isEmpty()) {
            card.addView(UiKit.subtitle(this, p, t("لا يوجد موظفون متاحون للمراسلة.", "No employees are available for messaging.")))
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
                if (messageSendInFlight) t("جاري الإرسال…", "Sending…") else t("إرسال الرسالة", "Send message")
            ).apply {
                isEnabled = !messageSendInFlight
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

        card.addView(UiKit.sectionLabel(this, p, t("الردود المستلمة", "Received replies")))
        if (messageReplies.isEmpty()) {
            card.addView(UiKit.subtitle(this, p, t("لا توجد ردود.", "No replies.")))
        } else {
            messageReplies.take(40).forEach { row ->
                val whenText = if (row.createdAt > 0L) " • ${formatTime(row.createdAt)}" else ""
                card.addView(UiKit.subtitle(this, p, "${row.employeeId}$whenText\n${row.body}"))
            }
        }
        content.addView(card)
    }

    private fun renderEmployees() {
        val binding = receiver.activeBinding()
        val card = UiKit.card(this, p, 9)
        card.addView(UiKit.sectionLabel(this, p, t(
            "إدارة الموظفين — ${binding?.storeName ?: "—"}",
            "Employee management — ${binding?.storeName ?: "—"}"
        )))
        card.addView(UiKit.subtitle(this, p, t(
            "الإدارة خاصة بالمحل المحدد: بحث، فلترة، تفاصيل، تعديل الاسم والفرع، تفعيل/إيقاف، ومتابعة حالة تنفيذ الأوامر.",
            "Management is scoped to the selected store: search, filters, details, name/branch editing, enable/disable, and command tracking."
        )))

        val selected = selectedEmployeeId?.let { id -> managedEmployees.firstOrNull { it.employeeId == id } }
        if (selected != null) {
            renderEmployeeDetails(card, selected)
            content.addView(card)
            return
        }

        card.addView(UiKit.button(this, p,
            if (employeesInFlight) t("جاري التحديث…", "Refreshing…") else t("تحديث قائمة الموظفين", "Refresh employee list")
        ).apply {
            isEnabled = !employeesInFlight
            setOnClickListener { refreshEmployees(silent = false) }
        })
        card.addView(UiKit.button(this, p, t("＋ إضافة موظف", "＋ Add employee"), false).apply {
            isEnabled = !employeeCommandInFlight
            setOnClickListener {
                employeeEditorOpen = true
                editingEmployeeId = null
                selectedEmployeeId = null
                render()
            }
        })

        if (employeeEditorOpen) renderEmployeeEditor(card)

        val searchField = UiKit.field(this, p, t("بحث بالاسم أو الرقم أو الفرع", "Search name, ID or branch")).apply {
            setText(employeeSearchQuery)
        }
        card.addView(searchField)
        card.addView(UiKit.button(this, p, t("تطبيق البحث", "Apply search"), false).apply {
            setOnClickListener {
                employeeSearchQuery = searchField.text.toString().trim()
                render()
            }
        })
        card.addView(UiKit.button(this, p, t("مسح البحث", "Clear search"), false).apply {
            isEnabled = employeeSearchQuery.isNotBlank()
            setOnClickListener { employeeSearchQuery = ""; render() }
        })

        val filters = UiKit.card(this, p, 6)
        addEmployeeFilter(filters, EmployeeFilter.ALL, t("الكل", "All"))
        addEmployeeFilter(filters, EmployeeFilter.ACTIVE, t("النشطون", "Active"))
        addEmployeeFilter(filters, EmployeeFilter.DISABLED, t("الموقوفون", "Disabled"))
        addEmployeeFilter(filters, EmployeeFilter.PENDING, t("أوامر معلقة", "Pending"))
        addEmployeeFilter(filters, EmployeeFilter.FAILED, t("أوامر فاشلة", "Failed"))
        card.addView(filters)

        val query = employeeSearchQuery.lowercase(Locale.getDefault())
        val visible = managedEmployees.filter { e ->
            val searchMatch = query.isBlank() ||
                e.employeeName.lowercase(Locale.getDefault()).contains(query) ||
                e.employeeId.lowercase(Locale.getDefault()).contains(query) ||
                e.branchId.lowercase(Locale.getDefault()).contains(query)
            val filterMatch = when (employeeFilter) {
                EmployeeFilter.ALL -> true
                EmployeeFilter.ACTIVE -> e.enabled
                EmployeeFilter.DISABLED -> !e.enabled
                EmployeeFilter.PENDING -> e.pendingCommand
                EmployeeFilter.FAILED -> e.commandStatus.equals("FAILED", true)
            }
            searchMatch && filterMatch
        }

        card.addView(UiKit.subtitle(this, p, t(
            "النتائج: ${visible.size} من ${managedEmployees.size}",
            "Results: ${visible.size} of ${managedEmployees.size}"
        )))

        if (visible.isEmpty()) {
            card.addView(UiKit.subtitle(this, p,
                if (managedEmployees.isEmpty()) t("لا توجد بيانات موظفين بعد.", "No employee data yet.")
                else t("لا توجد نتائج مطابقة.", "No matching employees.")))
        } else {
            visible.forEach { e ->
                val state = if (e.enabled) t("نشط", "Active") else t("موقوف", "Disabled")
                val command = commandStatusText(e.commandStatus)
                val lastSeen = if (e.lastSeenAt > 0L) formatTime(e.lastSeenAt) else t("لم يظهر بعد", "Not seen yet")
                card.addView(UiKit.button(this, p,
                    "${e.employeeName} • ${e.employeeId}\n${t("الفرع", "Branch")}: ${e.branchId} • $state\n${t("آخر ظهور", "Last seen")}: $lastSeen" +
                        if (command.isBlank()) "" else "\n${t("آخر أمر", "Last command")}: $command",
                    false
                ).apply {
                    setOnClickListener {
                        selectedEmployeeId = e.employeeId
                        employeeEditorOpen = false
                        render()
                    }
                })
            }
        }
        content.addView(card)
    }

    private fun addEmployeeFilter(parent: LinearLayout, filter: EmployeeFilter, label: String) {
        parent.addView(UiKit.button(this, p, label, employeeFilter == filter).apply {
            setOnClickListener { employeeFilter = filter; render() }
        })
    }

    private fun renderEmployeeDetails(parent: LinearLayout, e: ReceiverEmployeeAdminClient.Employee) {
        parent.addView(UiKit.button(this, p, t("← العودة لقائمة الموظفين", "← Back to employees"), false).apply {
            setOnClickListener { selectedEmployeeId = null; employeeEditorOpen = false; render() }
        })
        parent.addView(UiKit.title(this, p, "${e.employeeName} • ${e.employeeId}", 20f))
        val state = if (e.enabled) t("نشط", "Active") else t("موقوف", "Disabled")
        val connection = when {
            e.lastSeenAt > 0L && !e.pendingLink -> t("مرتبط وظهر على الخادم", "Linked and seen by server")
            e.pendingLink -> t("مسجل في المحل — بانتظار اكتمال/تحديث الربط", "Registered at Store — awaiting link/update")
            else -> t("لا توجد قراءة حديثة", "No recent reading")
        }
        val lastSeen = if (e.lastSeenAt > 0L) formatTime(e.lastSeenAt) else t("لم يظهر بعد", "Not seen yet")
        val command = commandStatusText(e.commandStatus).ifBlank { t("لا يوجد", "None") }
        val commandAt = if (e.commandUpdatedAt > 0L) formatTime(e.commandUpdatedAt) else t("—", "—")
        parent.addView(UiKit.subtitle(this, p, t(
            "الفرع: ${e.branchId}\nالحالة: $state\nالاتصال: $connection\nآخر ظهور: $lastSeen\nآخر أمر: $command — ${pendingActionLabel(e.pendingAction)}\nوقت الأمر: $commandAt",
            "Branch: ${e.branchId}\nStatus: $state\nConnection: $connection\nLast seen: $lastSeen\nLast command: $command — ${pendingActionLabel(e.pendingAction)}\nCommand time: $commandAt"
        )))
        if (e.commandStatus.equals("FAILED", true) && e.commandError.isNotBlank()) {
            parent.addView(UiKit.subtitle(this, p,
                "${t("سبب الفشل", "Failure reason")}: ${safeServerError(e.commandError)}"))
        }

        if (employeeEditorOpen && editingEmployeeId == e.employeeId) {
            renderEmployeeEditor(parent)
        } else {
            parent.addView(UiKit.button(this, p, t("تعديل الاسم / الفرع", "Edit name / branch"), false).apply {
                isEnabled = !e.pendingCommand && !employeeCommandInFlight
                setOnClickListener {
                    employeeEditorOpen = true
                    editingEmployeeId = e.employeeId
                    render()
                }
            })
            parent.addView(UiKit.button(this, p,
                if (e.enabled) t("إيقاف الموظف", "Disable employee") else t("تفعيل الموظف", "Enable employee"),
                false
            ).apply {
                isEnabled = !e.pendingCommand && !employeeCommandInFlight
                setOnClickListener {
                    if (e.enabled) confirmDisableEmployee(e) else submitEmployeeStatus(e, true)
                }
            })
            if (e.commandStatus.equals("FAILED", true) && !e.pendingCommand) {
                parent.addView(UiKit.button(this, p, t("إعادة محاولة آخر أمر", "Retry last command"), false).apply {
                    isEnabled = !employeeCommandInFlight
                    setOnClickListener { retryEmployeeCommand(e) }
                })
            }
        }
    }

    private fun retryEmployeeCommand(e: ReceiverEmployeeAdminClient.Employee) {
        if (!e.commandStatus.equals("FAILED", true) || e.pendingCommand || employeeCommandInFlight) return
        when (e.pendingAction.uppercase(Locale.US)) {
            "ADD" -> submitEmployeeEdit(
                null,
                e.employeeId,
                e.commandEmployeeName.ifBlank { e.employeeName },
                e.commandBranchId.ifBlank { e.branchId }
            )
            "UPDATE" -> submitEmployeeEdit(
                e,
                e.employeeId,
                e.commandEmployeeName.ifBlank { e.employeeName },
                e.commandBranchId.ifBlank { e.branchId }
            )
            "STATUS" -> submitEmployeeStatus(e, e.commandEnabled ?: !e.enabled)
            else -> showError(t("إعادة المحاولة", "Retry"), t(
                "لا توجد تفاصيل كافية لإعادة هذا الأمر تلقائيًا.",
                "There is not enough information to retry this command automatically."
            ))
        }
    }

    private fun renderEmployeeEditor(parent: LinearLayout) {
        val existing = editingEmployeeId?.let { id -> managedEmployees.firstOrNull { it.employeeId == id } }
        val box = UiKit.card(this, p, 7)
        box.addView(UiKit.sectionLabel(this, p, if (existing == null) t("إضافة موظف", "Add employee") else t("تعديل موظف", "Edit employee")))
        val id = UiKit.field(this, p, t("رقم الموظف", "Employee ID")).apply {
            setText(existing?.employeeId.orEmpty())
            isEnabled = existing == null
        }
        val name = UiKit.field(this, p, t("اسم الموظف", "Employee name")).apply { setText(existing?.employeeName.orEmpty()) }
        val branch = UiKit.field(this, p, t("الفرع", "Branch")).apply { setText(existing?.branchId ?: "MAIN") }
        box.addView(id)
        box.addView(name)
        box.addView(branch)
        box.addView(UiKit.button(this, p,
            if (employeeCommandInFlight) t("جاري إرسال الأمر…", "Sending command…") else t("إرسال الأمر", "Send command")
        ).apply {
            isEnabled = !employeeCommandInFlight
            setOnClickListener {
                val employeeId = id.text.toString().trim()
                val employeeName = name.text.toString().trim()
                val branchId = branch.text.toString().trim().ifBlank { "MAIN" }
                if (employeeId.isBlank()) {
                    id.error = t("مطلوب", "Required")
                    return@setOnClickListener
                }
                if (employeeName.length < 2) {
                    name.error = t("الاسم غير مكتمل", "Name is incomplete")
                    return@setOnClickListener
                }
                submitEmployeeEdit(existing, employeeId, employeeName, branchId)
            }
        })
        box.addView(UiKit.button(this, p, t("إلغاء", "Cancel"), false).apply {
            isEnabled = !employeeCommandInFlight
            setOnClickListener { employeeEditorOpen = false; editingEmployeeId = null; render() }
        })
        parent.addView(box)
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
                            canManageStore = x.canManageStore,
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
                        canManageStore = x.canManageStore,
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
            Section.EMPLOYEES -> if (receiver.canManageStore) refreshEmployees(silent)
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
                    result.getOrThrow().forEach { receiver.receive(it.packageText, storeId) }
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
        if (!receiver.canMessageEmployees || binding.serverUrl.isBlank() || binding.storeId.isBlank() || messagesInFlight) return
        messagesInFlight = true
        val generation = ++messagesGeneration
        val storeId = binding.storeId
        if (alive()) render()

        Thread {
            val employees = CentralServerClient.receiverEmployees(binding.serverUrl, receiver.receiverId, receiver.secret, storeId)
            val replies = if (employees.isSuccess) {
                CentralServerClient.receiverMessagesInbox(binding.serverUrl, receiver.receiverId, receiver.secret, storeId)
            } else Result.failure(employees.exceptionOrNull() ?: IllegalStateException("messages unavailable"))

            runOnUiThread {
                if (generation != messagesGeneration || receiver.activeBinding()?.storeId != storeId) return@runOnUiThread
                messagesInFlight = false
                if (!alive()) return@runOnUiThread
                if (employees.isSuccess && replies.isSuccess) {
                    messageEmployees = employees.getOrThrow()
                    messageReplies = replies.getOrThrow().map { ReplyRow(it.employeeId, it.body, it.createdAt) }
                    if (selectedMessageEmployeeId !in messageEmployees.map { it.employeeId }) selectedMessageEmployeeId = null
                    markActiveServerContact(storeId)
                    if (!silent) notice = t("تم تحديث رسائل المحل ✓", "Store messages refreshed ✓")
                } else if (!silent) {
                    showError(t("تعذر تحديث الرسائل", "Could not refresh messages"),
                        networkMessage(employees.exceptionOrNull() ?: replies.exceptionOrNull()))
                }
                render()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun sendMessage(employeeId: String, body: String) {
        val binding = receiver.activeBinding() ?: return
        if (messageSendInFlight || !receiver.canMessageEmployees || binding.storeId.isBlank()) return
        messageSendInFlight = true
        val generation = actionGeneration
        val storeId = binding.storeId
        if (alive()) render()
        Thread {
            val result = CentralServerClient.receiverSendEmployeeMessage(
                binding.serverUrl, receiver.receiverId, receiver.secret,
                employeeId, t("رسالة من الإدارة", "Management message"), body, "NORMAL", false,
                storeId = storeId
            )
            runOnUiThread {
                if (generation != actionGeneration || receiver.activeBinding()?.storeId != storeId) return@runOnUiThread
                messageSendInFlight = false
                if (!alive()) return@runOnUiThread
                if (result.isSuccess) {
                    markActiveServerContact(storeId)
                    notice = t("تم إرسال الرسالة لهذا المحل ✓", "Message sent for this store ✓")
                } else {
                    showError(t("تعذر إرسال الرسالة", "Message failed"), networkMessage(result.exceptionOrNull()))
                }
                render()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun refreshEmployees(silent: Boolean) {
        val binding = receiver.activeBinding() ?: return
        if (!receiver.canManageStore || binding.serverUrl.isBlank() || binding.storeId.isBlank() || employeesInFlight) return
        employeesInFlight = true
        val generation = ++employeesGeneration
        val storeId = binding.storeId
        if (alive()) render()

        Thread {
            val result = ReceiverEmployeeAdminClient.list(binding.serverUrl, receiver.receiverId, receiver.secret, storeId)
            runOnUiThread {
                if (generation != employeesGeneration || receiver.activeBinding()?.storeId != storeId) return@runOnUiThread
                employeesInFlight = false
                if (!alive()) return@runOnUiThread
                if (result.isSuccess) {
                    managedEmployees = result.getOrThrow()
                    if (selectedEmployeeId !in managedEmployees.map { it.employeeId }) selectedEmployeeId = null
                    markActiveServerContact(storeId)
                    if (!silent) notice = t("تم تحديث موظفي المحل ✓", "Store employees refreshed ✓")
                } else if (!silent) {
                    showError(t("إدارة الموظفين", "Employee management"), networkMessage(result.exceptionOrNull()))
                }
                render()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun submitEmployeeEdit(
        existing: ReceiverEmployeeAdminClient.Employee?,
        employeeId: String,
        name: String,
        branchId: String
    ) {
        val binding = receiver.activeBinding() ?: return
        if (employeeCommandInFlight || !receiver.canManageStore || binding.storeId.isBlank()) return
        employeeCommandInFlight = true
        val generation = actionGeneration
        val storeId = binding.storeId
        render()
        Thread {
            val result = if (existing == null) {
                ReceiverEmployeeAdminClient.add(
                    binding.serverUrl, receiver.receiverId, receiver.secret,
                    employeeId, name, branchId, storeId
                )
            } else {
                ReceiverEmployeeAdminClient.update(
                    binding.serverUrl, receiver.receiverId, receiver.secret,
                    employeeId, name, branchId, storeId
                )
            }
            runOnUiThread {
                if (generation != actionGeneration || receiver.activeBinding()?.storeId != storeId) return@runOnUiThread
                employeeCommandInFlight = false
                if (!alive()) return@runOnUiThread
                if (result.isSuccess) {
                    employeeEditorOpen = false
                    editingEmployeeId = null
                    notice = t(
                        "تم إرسال الأمر إلى جهاز هذا المحل — قيد الإرسال حتى يؤكد التنفيذ.",
                        "Command queued for this Store device until execution is acknowledged."
                    )
                    render()
                    refreshEmployees(silent = true)
                } else {
                    showError(t("تعذر إرسال الأمر", "Command failed"), networkMessage(result.exceptionOrNull()))
                    render()
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun confirmDisableEmployee(e: ReceiverEmployeeAdminClient.Employee) {
        if (!alive()) return
        AlertDialog.Builder(this)
            .setTitle(t("تأكيد إيقاف الموظف", "Confirm employee disable"))
            .setMessage(t("هل تريد إيقاف ${e.employeeName} في هذا المحل؟ سيُرسل الأمر لجهاز المحل.", "Disable ${e.employeeName} in this store? The command will be sent to the Store device."))
            .setPositiveButton(t("إيقاف", "Disable")) { _, _ -> submitEmployeeStatus(e, false) }
            .setNegativeButton(t("إلغاء", "Cancel"), null)
            .show()
    }

    private fun submitEmployeeStatus(e: ReceiverEmployeeAdminClient.Employee, enabled: Boolean) {
        val binding = receiver.activeBinding() ?: return
        if (employeeCommandInFlight || !receiver.canManageStore || binding.storeId.isBlank()) return
        employeeCommandInFlight = true
        val generation = actionGeneration
        val storeId = binding.storeId
        render()
        Thread {
            val result = ReceiverEmployeeAdminClient.setEnabled(
                binding.serverUrl, receiver.receiverId, receiver.secret, e.employeeId, enabled, storeId
            )
            runOnUiThread {
                if (generation != actionGeneration || receiver.activeBinding()?.storeId != storeId) return@runOnUiThread
                employeeCommandInFlight = false
                if (!alive()) return@runOnUiThread
                if (result.isSuccess) {
                    notice = t(
                        "تم إرسال أمر ${if (enabled) "التفعيل" else "الإيقاف"} لهذا المحل — بانتظار تأكيد جهاز المحل.",
                        "${if (enabled) "Enable" else "Disable"} command queued for this store until acknowledgement."
                    )
                    render()
                    refreshEmployees(silent = true)
                } else {
                    showError(t("تعذر إرسال الأمر", "Command failed"), networkMessage(result.exceptionOrNull()))
                    render()
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun startLocalReportChannels() {
        if (!::receiver.isInitialized) return

        if (lanReportServer?.isRunning() != true) {
            lanReportServer?.stop()
            lanReportServer = ReceiverReportLanServer(
                receiver.receiverId,
                receiver.secret,
                ::acceptOfflineReport
            ) { status ->
                runOnUiThread {
                    if (!alive()) return@runOnUiThread
                    lanStatus = status
                    if (section == Section.STATUS) render()
                }
            }.also { server ->
                server.start()
                lanStatus = if (server.isRunning()) t("جاهز", "Ready") else lanStatus
            }
        }

        val missing = ReceiverReportBluetoothSupport.missingPermissions(this)
        if (missing.isNotEmpty()) {
            bleStatus = t("الصلاحيات غير ممنوحة", "Permissions not granted")
            if (!bluetoothPermissionAsked && Build.VERSION.SDK_INT >= 31) {
                bluetoothPermissionAsked = true
                ReceiverReportBluetoothSupport.request(this, REQ_BLUETOOTH_RECEIVER)
            }
            return
        }
        if (!ReceiverReportBluetoothSupport.bluetoothEnabled(this)) {
            bleStatus = t("Bluetooth غير مفعّل", "Bluetooth is off")
            return
        }
        if (bleReportServer?.isRunning() != true) {
            bleReportServer?.stop()
            bleReportServer = ReceiverReportBleServer(
                this,
                receiver.receiverId,
                receiver.secret,
                ::acceptOfflineReport
            ) { status ->
                runOnUiThread {
                    if (!alive()) return@runOnUiThread
                    bleStatus = status
                    if (section == Section.STATUS) render()
                }
            }.also { it.start() }
        }
    }

    private fun stopLocalReportChannels() {
        lanReportServer?.stop()
        bleReportServer?.stop()
        lanReportServer = null
        bleReportServer = null
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
            current.canReceiveReports, current.canMessageEmployees, current.canManageStore,
            now, current.storeLastSeenAt
        )
        lastServerRefreshAt = now
    }

    private fun invalidateRemoteRequests() {
        capabilitiesGeneration++
        reportsGeneration++
        messagesGeneration++
        employeesGeneration++
        actionGeneration++
        capabilitiesInFlight = false
        reportsInFlight = false
        messagesInFlight = false
        employeesInFlight = false
        employeeCommandInFlight = false
        messageSendInFlight = false
    }

    private fun mark(v: Boolean) = if (v) "✓" else "— ${t("غير مسموح", "Not allowed")}"

    private fun commandStatusText(status: String): String = when (status.uppercase(Locale.US)) {
        "PENDING" -> t("قيد الإرسال", "Pending")
        "DISPATCHED" -> t("وصل لجهاز المحل", "Reached Store device")
        "APPLIED" -> t("تم التنفيذ", "Applied")
        "FAILED" -> t("فشل التنفيذ", "Failed")
        else -> ""
    }

    private fun pendingActionLabel(action: String): String = when (action.uppercase(Locale.US)) {
        "ADD" -> t("إضافة", "Add")
        "UPDATE" -> t("تعديل", "Edit")
        "STATUS" -> t("تغيير الحالة", "Status change")
        else -> if (action.isBlank()) "" else t("إدارة", "Management")
    }

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
