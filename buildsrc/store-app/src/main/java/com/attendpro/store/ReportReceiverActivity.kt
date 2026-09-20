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
import com.attendpro.core.CentralServerClient
import com.attendpro.core.QrCodeTools
import com.attendpro.core.QrScannerActivity
import com.attendpro.core.ReceiverEmployeeAdminClient
import com.attendpro.core.ReportProtocol
import com.attendpro.core.ReportReceiverStore
import com.attendpro.core.UiKit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportReceiverActivity : Activity() {
    private lateinit var receiver: ReportReceiverStore
    private val p by lazy { UiKit.palette(this) }
    private var tab=0
    private fun t(ar:String,en:String)=AppLanguage.text(this,ar,en)

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);receiver=ReportReceiverStore(this);handleIncoming(intent);buildUi()}
    override fun onNewIntent(intent:Intent?){super.onNewIntent(intent);if(intent!=null)handleIncoming(intent);buildUi()}
    override fun onResume(){super.onResume();if(::receiver.isInitialized&&receiver.serverUrl.isNotBlank())refreshRemote(true)}
    private fun handleIncoming(i:Intent?){if(i?.action==Intent.ACTION_SEND&&i.type=="text/plain")i.getStringExtra(Intent.EXTRA_TEXT)?.takeIf{it.isNotBlank()}?.let{receiver.receive(it)}}

    private fun buildUi(){
        window.statusBarColor=p.bg
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;layoutDirection=if(AppLanguage.isEnglish(this@ReportReceiverActivity))View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL;setPadding(14,18,14,28);setBackgroundColor(p.bg)}
        val linked=receiver.serverUrl.isNotBlank()
        root.addView(UiKit.heroCard(this,p,10).apply{
            addView(UiKit.title(this@ReportReceiverActivity,p,t("هاتف الاستلام وإدارة الموظفين","Receiver & Employee Management"),24f).apply{gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE)})
            addView(UiKit.subtitle(this@ReportReceiverActivity,p,t("${receiver.capabilityStoreName.ifBlank{"غير مرتبط"}} • ${receiver.capabilityBranchId.ifBlank{"—"}}\n${receiver.receiverName} • ${if(linked)"متصل بالخادم" else "غير مرتبط"}","${receiver.capabilityStoreName.ifBlank{"Not linked"}} • ${receiver.capabilityBranchId.ifBlank{"—"}}\n${receiver.receiverName} • ${if(linked)"Server linked" else "Not linked"}")).apply{gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE)})
        })
        val tabs=UiKit.card(this,p,7)
        listOf(t("التفعيل","Activation"),t("التقارير","Reports"),t("الموظفون والرسائل","Employees & messages")).forEachIndexed{i,s->tabs.addView(UiKit.button(this,p,s,tab==i).apply{setOnClickListener{tab=i;buildUi()}})}
        root.addView(tabs)
        when(tab){0->activation(root,linked);1->reports(root);else->management(root)}
        root.addView(UiKit.card(this,p,6).apply{addView(UiKit.button(this@ReportReceiverActivity,p,t("رجوع","Back"),false).apply{setOnClickListener{finish()}})})
        setContentView(ScrollView(this).apply{isFillViewport=true;setBackgroundColor(p.bg);addView(root)})
    }

    private fun activation(root:LinearLayout,linked:Boolean){
        root.addView(UiKit.card(this,p,9).apply{
            addView(UiKit.sectionLabel(this@ReportReceiverActivity,p,t("تفعيل الهاتف","Phone activation")))
            addView(UiKit.title(this@ReportReceiverActivity,p,if(linked)t("✓ الهاتف مرتبط","✓ Phone linked") else t("غير مرتبط","Not linked"),20f))
            addView(UiKit.subtitle(this@ReportReceiverActivity,p,t("اسم الهاتف: ${receiver.receiverName}\nالمعرف: ${receiver.receiverId}\nالمحل: ${receiver.capabilityStoreName.ifBlank{"—"}}\nالفرع: ${receiver.capabilityBranchId.ifBlank{"—"}}\nالخادم: ${if(linked)receiver.serverUrl else "—"}","Phone: ${receiver.receiverName}\nID: ${receiver.receiverId}\nStore: ${receiver.capabilityStoreName.ifBlank{"—"}}\nBranch: ${receiver.capabilityBranchId.ifBlank{"—"}}\nServer: ${if(linked)receiver.serverUrl else "—"}")))
            addView(UiKit.button(this@ReportReceiverActivity,p,t("إظهار QR تعريف هذا الهاتف","Show this phone identity QR")).apply{setOnClickListener{showInviteQr()}})
            addView(UiKit.button(this@ReportReceiverActivity,p,t("مسح QR الربط النهائي","Scan final link QR"),false).apply{setOnClickListener{scanGrant()}})
            if(linked)addView(UiKit.button(this@ReportReceiverActivity,p,t("تحديث الصلاحيات والحالة","Refresh permissions and status"),false).apply{setOnClickListener{refreshRemote(false)}})
        })
        root.addView(UiKit.card(this,p,9).apply{
            addView(UiKit.sectionLabel(this@ReportReceiverActivity,p,t("الصلاحيات الممنوحة لهذا الهاتف","Permissions granted to this phone")))
            addView(UiKit.subtitle(this@ReportReceiverActivity,p,"${mark(receiver.canReceiveReports)} ${t("استلام التقارير","Receive reports")}\n${mark(receiver.canMessageEmployees)} ${t("مراسلة الموظفين","Message employees")}\n${mark(receiver.canManageStore)} ${t("إدارة الموظفين","Employee management")}"))
        })
    }
    private fun mark(v:Boolean)=if(v)"✓" else "— ${t("غير مسموح","Not allowed")}"

    private fun reports(root:LinearLayout){
        val c=UiKit.card(this,p,9);c.addView(UiKit.sectionLabel(this,p,t("التقارير","Reports")))
        if(!receiver.canReceiveReports){c.addView(UiKit.subtitle(this,p,t("ليس لديك صلاحية استلام التقارير من هذا المحل.","You do not have permission to receive reports from this Store.")));root.addView(c);return}
        c.addView(UiKit.button(this,p,t("تحديث التقارير","Refresh reports")).apply{setOnClickListener{refreshRemote(false)}})
        val items=receiver.receivedReports();c.addView(UiKit.subtitle(this,p,if(items.isEmpty())t("لا توجد تقارير مستلمة بعد.","No reports received yet.") else t("التقارير المستلمة: ${items.size}","Received reports: ${items.size}")))
        items.take(20).forEach{x->val d=SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(Date(x.receivedAt));c.addView(UiKit.button(this,p,"${x.storeName} • ${x.branchId}\n${x.periodLabel} • $d",false).apply{setOnClickListener{AlertDialog.Builder(this@ReportReceiverActivity).setTitle("${x.storeName} — ${x.periodLabel}").setMessage(x.reportText).setPositiveButton(t("إغلاق","Close"),null).show()}})}
        root.addView(c)
    }

    private fun management(root:LinearLayout){
        if(receiver.canMessageEmployees)root.addView(UiKit.card(this,p,9).apply{addView(UiKit.sectionLabel(this@ReportReceiverActivity,p,t("مراسلة الموظفين","Employee messaging")));addView(UiKit.button(this@ReportReceiverActivity,p,t("اختيار موظف وإرسال رسالة","Choose employee and send message")).apply{setOnClickListener{chooseEmployeeForMessage()}});addView(UiKit.button(this@ReportReceiverActivity,p,t("الردود المستلمة","Received replies"),false).apply{setOnClickListener{showReplies()}})})
        if(receiver.canManageStore)root.addView(UiKit.card(this,p,9).apply{addView(UiKit.sectionLabel(this@ReportReceiverActivity,p,t("إدارة الموظفين","Employee management")));addView(UiKit.subtitle(this@ReportReceiverActivity,p,t("إضافة وتعديل وتفعيل الموظفين فقط. لا تمنح هذه الصلاحية إعدادات النظام أو المالك أو الاتصال.","Add, edit and enable employees only. This permission does not grant system, owner or connection settings.")));addView(UiKit.button(this@ReportReceiverActivity,p,t("فتح إدارة الموظفين","Open employee management")).apply{setOnClickListener{openEmployees()}})})
        if(!receiver.canMessageEmployees&&!receiver.canManageStore)root.addView(UiKit.card(this,p,9).apply{addView(UiKit.sectionLabel(this@ReportReceiverActivity,p,t("الموظفون والرسائل","Employees & messages")));addView(UiKit.subtitle(this@ReportReceiverActivity,p,t("لا توجد صلاحيات إدارة ممنوحة لهذا الهاتف.","No management permissions are granted to this phone.")))})
    }

    private fun showInviteQr(){val raw=ReportProtocol.encodeInvite(receiver.newInvite());val qr=runCatching{QrCodeTools.bitmap(raw,700)}.getOrElse{info("QR",it.message?:"Error");return};val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;addView(UiKit.subtitle(this@ReportReceiverActivity,p,t("امسح هذا الرمز من تبويب إضافة هاتف في جهاز المحل.","Scan this code from Add phone on the Store device.")));addView(ImageView(this@ReportReceiverActivity).apply{setImageBitmap(qr);adjustViewBounds=true;layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,700)})};AlertDialog.Builder(this).setTitle(t("QR تعريف الهاتف","Phone identity QR")).setView(box).setPositiveButton(t("إغلاق","Close"),null).show()}
    private fun scanGrant(){startActivityForResult(Intent(this,QrScannerActivity::class.java).putExtra(QrScannerActivity.EXTRA_PROMPT,t("امسح QR الربط النهائي","Scan final link QR")),REQ_GRANT)}
    @Deprecated("Deprecated in Java") override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r!=REQ_GRANT||c!=RESULT_OK)return;val raw=d?.getStringExtra(QrScannerActivity.EXTRA_RESULT).orEmpty();val g=ReportProtocol.decodeRemoteGrant(raw,receiver.receiverId)?:run{info(t("QR غير صالح","Invalid QR"),t("الرمز غير صالح أو منتهي.","The code is invalid or expired."));return};receiver.serverUrl=g.serverUrl;refreshRemote(false)}

    private fun refreshRemote(silent:Boolean){if(receiver.serverUrl.isBlank()){if(!silent)info(t("غير مرتبط","Not linked"),t("اربط الهاتف أولًا من تبويب التفعيل.","Link the phone first from Activation."));return};Thread{val r=CentralServerClient.receiverCapabilities(receiver.serverUrl,receiver.receiverId,receiver.secret);if(r.isSuccess){val x=r.getOrThrow();receiver.canReceiveReports=x.canReceiveReports;receiver.canMessageEmployees=x.canMessageEmployees;receiver.canManageStore=x.canManageStore;receiver.capabilityStoreName=x.storeName;receiver.capabilityBranchId=x.branchId;if(receiver.canReceiveReports)CentralServerClient.receiverInbox(receiver.serverUrl,receiver.receiverId,receiver.secret).getOrNull()?.forEach{receiver.receive(it.packageText)}};runOnUiThread{if(!silent)info(if(r.isSuccess)t("تم التحديث ✓","Updated ✓") else t("تعذر التحديث","Refresh failed"),r.exceptionOrNull()?.message?:t("تم تحديث الصلاحيات.","Permissions refreshed."));buildUi()}}.start()}

    private fun openEmployees(){Thread{val r=ReceiverEmployeeAdminClient.list(receiver.serverUrl,receiver.receiverId,receiver.secret);runOnUiThread{if(r.isFailure){info(t("إدارة الموظفين","Employee management"),r.exceptionOrNull()?.message?:"Error");return@runOnUiThread};val es=r.getOrThrow();val labels=mutableListOf(t("＋ إضافة موظف","＋ Add employee"));labels.addAll(es.map{"${it.employeeName} • ${it.employeeId} • ${if(it.enabled)t("نشط","Active") else t("موقوف","Disabled")}"});AlertDialog.Builder(this).setTitle(t("إدارة الموظفين","Employee management")).setItems(labels.toTypedArray()){_,i->if(i==0)editEmployee(null) else employeeActions(es[i-1])}.setNegativeButton(t("إغلاق","Close"),null).show()}}.start()}
    private fun editEmployee(e:ReceiverEmployeeAdminClient.Employee?){val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,8,24,0)};val id=UiKit.field(this,p,t("رقم الموظف","Employee ID")).apply{setText(e?.employeeId.orEmpty());isEnabled=e==null};val name=UiKit.field(this,p,t("اسم الموظف","Employee name")).apply{setText(e?.employeeName.orEmpty())};val branch=UiKit.field(this,p,t("الفرع","Branch")).apply{setText(e?.branchId?:"MAIN")};box.addView(id);box.addView(name);box.addView(branch);val d=AlertDialog.Builder(this).setTitle(if(e==null)t("إضافة موظف","Add employee")else t("تعديل موظف","Edit employee")).setView(box).setPositiveButton(t("حفظ","Save"),null).setNegativeButton(t("إلغاء","Cancel"),null).create();d.setOnShowListener{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{val eid=id.text.toString().trim();val n=name.text.toString().trim();val b=branch.text.toString().trim().ifBlank{"MAIN"};if(eid.isBlank()||n.length<2)return@setOnClickListener;Thread{val r=if(e==null)ReceiverEmployeeAdminClient.add(receiver.serverUrl,receiver.receiverId,receiver.secret,eid,n,b)else ReceiverEmployeeAdminClient.update(receiver.serverUrl,receiver.receiverId,receiver.secret,eid,n,b);runOnUiThread{if(r.isSuccess){d.dismiss();openEmployees()}else info(t("تعذر الحفظ","Save failed"),r.exceptionOrNull()?.message?:"Error")}}.start()}};d.show()}
    private fun employeeActions(e:ReceiverEmployeeAdminClient.Employee){AlertDialog.Builder(this).setTitle("${e.employeeName} • ${e.employeeId}").setItems(arrayOf(t("تعديل","Edit"),if(e.enabled)t("إيقاف","Disable")else t("تفعيل","Enable"))){_,i->if(i==0)editEmployee(e) else Thread{val r=ReceiverEmployeeAdminClient.setEnabled(receiver.serverUrl,receiver.receiverId,receiver.secret,e.employeeId,!e.enabled);runOnUiThread{if(r.isSuccess)openEmployees()else info(t("تعذر التحديث","Update failed"),r.exceptionOrNull()?.message?:"Error")}}.start()}.setNegativeButton(t("إلغاء","Cancel"),null).show()}

    private fun chooseEmployeeForMessage(){Thread{val r=CentralServerClient.receiverEmployees(receiver.serverUrl,receiver.receiverId,receiver.secret);runOnUiThread{val es=r.getOrNull().orEmpty();if(es.isEmpty()){info(t("الموظفون","Employees"),t("لا يوجد موظفون متاحون.","No employees available."));return@runOnUiThread};AlertDialog.Builder(this).setTitle(t("اختر الموظف","Choose employee")).setItems(es.map{"${it.employeeName} • ${it.employeeId}"}.toTypedArray()){_,i->val e=es[i];val f=UiKit.field(this,p,t("اكتب الرسالة","Write message"));AlertDialog.Builder(this).setTitle(e.employeeName).setView(f).setPositiveButton(t("إرسال","Send")){_,_->Thread{CentralServerClient.receiverSendEmployeeMessage(receiver.serverUrl,receiver.receiverId,receiver.secret,e.employeeId,t("رسالة من الإدارة","Management message"),f.text.toString(),"NORMAL",false)}.start()}.setNegativeButton(t("إلغاء","Cancel"),null).show()}.show()}}.start()}
    private fun showReplies(){Thread{val r=CentralServerClient.receiverMessagesInbox(receiver.serverUrl,receiver.receiverId,receiver.secret);runOnUiThread{val x=r.getOrNull().orEmpty();info(t("ردود الموظفين","Employee replies"),if(x.isEmpty())t("لا توجد ردود.","No replies.")else x.take(30).joinToString("\n\n"){"${it.employeeId}: ${it.body}"})}}.start()}
    private fun info(title:String,msg:String){AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton(t("حسنًا","OK"),null).show()}
    companion object{private const val REQ_GRANT=7301}
}
