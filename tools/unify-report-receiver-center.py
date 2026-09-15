from pathlib import Path
p=Path('buildsrc/store-app/src/main/java/com/attendpro/store/ReportsActivity.kt')
s=p.read_text()
s=s.replace('companion object { const val EXTRA_STORE_ADMIN_SESSION = "store_admin_session" }','companion object { const val EXTRA_STORE_ADMIN_SESSION = "store_admin_session"; private const val REQUEST_REPORT_RECEIVER_QR = 9207 }')
s=s.replace('import com.attendpro.core.ReportProtocol','import com.attendpro.core.ReportProtocol\nimport com.attendpro.core.QrScannerActivity')
s=s.replace('share.addView(UiKit.sectionLabel(this, p, t("المشاركة المؤكدة", "Verified sharing")))','share.addView(UiKit.sectionLabel(this, p, t("استلام التقارير وإدارة الصلاحيات", "Report receiving and permissions")))')
s=s.replace('share.addView(UiKit.subtitle(this, p, t("يمكن لمالك العمل منح صلاحية لهواتف محددة. التقرير الموجه لهاتف مصرح يُشفّر بمفتاح ذلك الهاتف، ثم يظهر رمز تأكيد عند نجاح الاستلام.", "The Store owner can authorize specific phones. A report sent to an authorized phone is encrypted for that phone and confirmed after successful receipt.")))','share.addView(UiKit.subtitle(this, p, t("اربط هاتف الاستلام ثم حدد صلاحياته بدقة: التقارير، مراسلة الموظفين، أو إدارة الموظفين.", "Link a receiver phone, then choose its exact permissions: reports, employee messaging, or employee management.")))\n        share.addView(UiKit.button(this, p, t("هواتف الاستلام والصلاحيات", "Receiver phones and permissions"), false).apply { setOnClickListener { showReceiverCenter(events) } })')
s=s.replace('info(t("لا توجد هواتف مصرح لها", "No authorized phones"), t("من إدارة المحل ← هواتف استلام التقارير، أضف هاتف المراقبة بمسح QR الخاص به أولًا.", "From Store Management → report receiver phones, add the monitoring phone by scanning its QR first."))\n            return','showReceiverCenter(events)\n            return')
insert='''
    private fun startReceiverQrScanner() {
        startActivityForResult(Intent(this, QrScannerActivity::class.java).putExtra(QrScannerActivity.EXTRA_PROMPT, t("وجّه الكاميرا إلى QR تعريف هاتف الاستلام", "Point the camera at the receiver phone identification QR")), REQUEST_REPORT_RECEIVER_QR)
    }

    private fun receiverPermissionsLabel(r: com.attendpro.core.AuthorizedReportReceiver): String {
        val items=mutableListOf<String>()
        if(r.canReceiveReports) items+=t("استلام التقارير","Reports")
        if(r.canMessageEmployees) items+=t("مراسلة الموظفين","Employee messaging")
        if(r.canManageStore) items+=t("إدارة الموظفين","Employee management")
        return items.joinToString(" + ").ifBlank{t("بدون صلاحيات","No permissions")}
    }

    private fun showReceiverCenter(events: List<AttendanceEvent>) {
        val receivers=repo.authorizedReportReceivers()
        if(receivers.isEmpty()){startReceiverQrScanner();return}
        val labels=mutableListOf(t("＋ ربط هاتف استلام جديد — فتح الكاميرا","＋ Link new receiver phone — open camera"))
        labels.addAll(receivers.map{"${if(it.active) "●" else "○"} ${it.name} • ${receiverPermissionsLabel(it)}"})
        AlertDialog.Builder(this).setTitle(t("هواتف الاستلام والصلاحيات","Receiver phones and permissions")).setItems(labels.toTypedArray()){_,which->if(which==0)startReceiverQrScanner() else showReceiverActions(events,receivers[which-1])}.setNegativeButton(t("إغلاق","Close"),null).show()
    }

    private fun showReceiverActions(events: List<AttendanceEvent>, r: com.attendpro.core.AuthorizedReportReceiver) {
        val actions=arrayOf(t("تحديد نوع الصلاحيات","Choose permissions"),t("إرسال التقرير الآن","Send report now"),if(r.active)t("إيقاف الهاتف","Disable phone")else t("تفعيل الهاتف","Enable phone"),t("حذف الهاتف","Remove phone"))
        AlertDialog.Builder(this).setTitle("${r.name} • ${receiverPermissionsLabel(r)}").setItems(actions){_,a->when(a){
            0->editReceiverPermissions(events,r)
            1->if(r.active&&r.canReceiveReports)shareToReceiver(events,r)else info(t("لا يمكن الإرسال","Cannot send"),t("فعّل الهاتف وصلاحية استلام التقارير أولًا.","Enable the phone and report permission first."))
            2->{repo.setReportReceiverActive(r.receiverId,!r.active);syncReceiverActive(r.receiverId,!r.active);showReceiverCenter(events)}
            3->AlertDialog.Builder(this).setTitle(t("حذف الهاتف؟","Remove phone?")).setMessage(t("سيتم سحب صلاحيات الهاتف من هذا المحل.","This phone's permissions will be revoked.")).setPositiveButton(t("حذف","Remove")){_,_->repo.setReportReceiverActive(r.receiverId,false);syncReceiverActive(r.receiverId,false);repo.removeReportReceiver(r.receiverId);buildUi()}.setNegativeButton(t("إلغاء","Cancel"),null).show()
        }}.setNegativeButton(t("رجوع","Back"),null).show()
    }

    private fun editReceiverPermissions(events: List<AttendanceEvent>, r: com.attendpro.core.AuthorizedReportReceiver) {
        val labels=arrayOf(t("استلام التقارير","Receive reports"),t("مراسلة الموظفين","Message employees"),t("إدارة الموظفين","Employee management"))
        val checked=booleanArrayOf(r.canReceiveReports,r.canMessageEmployees,r.canManageStore)
        AlertDialog.Builder(this).setTitle(t("حدد صلاحيات ${r.name}","Choose ${r.name} permissions")).setMultiChoiceItems(labels,checked){_,i,v->checked[i]=v}.setMessage(t("يمكن اختيار صلاحية واحدة أو أكثر. إدارة الموظفين تشمل العرض والإضافة والتعديل والتفعيل/الإيقاف فقط، ولا تمنح إعدادات المحل أو صلاحيات مالك النظام.","Select one or more. Employee management covers listing, adding, editing and enable/disable only; it does not grant Store settings or System Owner privileges.")).setPositiveButton(t("حفظ الصلاحيات","Save permissions")){_,_->
            repo.setReportReceiverPermissions(r.receiverId,checked[0],checked[1],checked[2])
            if(repo.isCentralActivationActive()&&repo.serverUrl.isNotBlank())Thread{CentralServerClient.setReceiverPermissions(repo.serverUrl,repo.centralAccessToken,repo.storeId,DeviceIdentity(this),r.receiverId,checked[0],checked[1],checked[2])}.start()
            info(t("تم حفظ الصلاحيات ✓","Permissions saved ✓"),t("تم تحديث نوع صلاحيات هاتف الاستلام.","Receiver permissions were updated."));buildUi()
        }.setNegativeButton(t("إلغاء","Cancel"),null).show()
    }

    private fun syncReceiverActive(receiverId:String,active:Boolean){if(repo.isCentralActivationActive()&&repo.serverUrl.isNotBlank())Thread{CentralServerClient.setReceiverActive(repo.serverUrl,repo.centralAccessToken,repo.storeId,DeviceIdentity(this),receiverId,active)}.start()}

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=REQUEST_REPORT_RECEIVER_QR||resultCode!=RESULT_OK)return
        val invite=ReportProtocol.decodeInvite(data?.getStringExtra(QrScannerActivity.EXTRA_RESULT).orEmpty())
        if(invite==null){info(t("QR غير صالح","Invalid QR"),t("اعرض QR تعريف هاتف الاستلام ثم أعد المسح.","Show the receiver identification QR and scan again."));return}
        if(!repo.authorizeReportReceiver(invite)){info(t("تعذر الربط","Link failed"),t("لم يتم قبول رمز الهاتف.","The phone QR was not accepted."));return}
        repo.setReportReceiverPermissions(invite.receiverId,true,false,false)
        if(repo.isCentralActivationActive()&&repo.serverUrl.isNotBlank())Thread{
            val result=CentralServerClient.registerReceiver(repo.serverUrl,repo.centralAccessToken,repo.storeId,DeviceIdentity(this),invite.receiverId,invite.name,invite.secret)
            if(result.isSuccess)CentralServerClient.setReceiverPermissions(repo.serverUrl,repo.centralAccessToken,repo.storeId,DeviceIdentity(this),invite.receiverId,true,false,false)
            runOnUiThread{
                if(result.isSuccess){val grant=ReportProtocol.RemoteReceiverGrant(invite.receiverId,repo.serverUrl,repo.storeName,repo.branchId,System.currentTimeMillis()+10*60_000L);val bmp=runCatching{com.attendpro.core.QrCodeTools.bitmap(ReportProtocol.encodeRemoteGrant(grant),700)}.getOrNull();if(bmp!=null)AlertDialog.Builder(this).setTitle(t("تم قبول الهاتف ✓ — QR الربط النهائي","Phone accepted ✓ — final linking QR")).setMessage(t("بعد مسح QR النهائي افتح الهاتف من مركز الصلاحيات وحدد نوع الصلاحيات المطلوبة.","After scanning the final QR, open this phone in the permissions center and choose the required permissions.")).setView(android.widget.ImageView(this).apply{setImageBitmap(bmp);adjustViewBounds=true}).setPositiveButton(t("تحديد الصلاحيات","Choose permissions")){_,_->val rr=repo.authorizedReportReceivers().firstOrNull{it.receiverId==invite.receiverId};if(rr!=null)editReceiverPermissions(emptyList(),rr)}.show()}else info(t("تمت الإضافة محليًا","Added locally"),t("تعذر تسجيل الخادم الآن.","Server registration unavailable."));buildUi()
            }
        }.start() else {info(t("تمت إضافة الهاتف","Phone added"),t("تم حفظه محليًا بصلاحية التقارير فقط. افتح مركز الصلاحيات لتعديلها.","Saved locally with reports-only permission. Open the permissions center to change it."));buildUi()}
    }
'''
s=s.replace('    private fun chooseAuthorizedReceiver(events: List<AttendanceEvent>) {',insert+'\n    private fun chooseAuthorizedReceiver(events: List<AttendanceEvent>) {')
p.write_text(s)