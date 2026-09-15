from pathlib import Path

client=Path('buildsrc/core/src/main/java/com/attendpro/core/CentralServerClient.kt')
s=client.read_text()
s=s.replace('data class ReceiverEmployee(val employeeId: String, val employeeName: String, val branchId: String, val lastSeenAt: Long)', '''data class ReceiverEmployee(
        val employeeId: String, val employeeName: String, val branchId: String, val lastSeenAt: Long,
        val enabled: Boolean = true, val pendingLink: Boolean = false
    )''')
needle='''    fun receiverSendEmployeeMessage(
'''
insert='''    fun receiverManagedEmployees(serverUrl: String, receiverId: String, secret: String): Result<List<ReceiverEmployee>> = runCatching {
        requireHttps(serverUrl)
        val o = request(serverUrl, "/api/v1/monitor/employees/manage-list", "POST", JSONObject().apply {
            put("receiverId", receiverId); put("secret", secret)
        })
        val a = o.optJSONArray("employees") ?: JSONArray()
        (0 until a.length()).map { i ->
            val x = a.getJSONObject(i)
            ReceiverEmployee(x.optString("employeeId"), x.optString("employeeName"), x.optString("branchId", "MAIN"),
                x.optLong("lastSeenAt", 0L), x.optBoolean("enabled", true), x.optBoolean("pendingLink", false))
        }
    }

    fun receiverAddEmployee(serverUrl: String, receiverId: String, secret: String, employeeId: String, employeeName: String, branchId: String): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/monitor/employees/add", "POST", JSONObject().apply {
            put("receiverId", receiverId); put("secret", secret); put("employeeId", employeeId)
            put("employeeName", employeeName); put("branchId", branchId)
        }); Unit
    }

    fun receiverUpdateEmployee(serverUrl: String, receiverId: String, secret: String, employeeId: String, employeeName: String, branchId: String): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/monitor/employees/update", "POST", JSONObject().apply {
            put("receiverId", receiverId); put("secret", secret); put("employeeId", employeeId)
            put("employeeName", employeeName); put("branchId", branchId)
        }); Unit
    }

    fun receiverSetEmployeeEnabled(serverUrl: String, receiverId: String, secret: String, employeeId: String, enabled: Boolean): Result<Unit> = runCatching {
        requireHttps(serverUrl)
        request(serverUrl, "/api/v1/monitor/employees/status", "POST", JSONObject().apply {
            put("receiverId", receiverId); put("secret", secret); put("employeeId", employeeId); put("enabled", enabled)
        }); Unit
    }

'''
if 'fun receiverManagedEmployees' not in s:
    s=s.replace(needle,insert+needle)
client.write_text(s)

activity=Path('buildsrc/store-app/src/main/java/com/attendpro/store/ReportReceiverActivity.kt')
a=activity.read_text()
a=a.replace('t("هاتف الإدارة والاستلام", "Management & Receiver Phone")','t("هاتف الاستلام وإدارة الموظفين", "Receiver & Employee Management Phone")')
a=a.replace('t("إعدادات مدير المحل", "Store Manager settings")','t("إدارة الموظفين", "Employee management")')
old='''            management.addView(UiKit.sectionLabel(this, p, t("إعدادات مدير المحل", "Store Manager settings")))
            management.addView(UiKit.subtitle(this, p, t(
                "يمكن تعديل الدوام والسماح وبعض إعدادات الصوت والمزامنة. يصل التعديل إلى هاتف المحل عبر الخادم ويطبّقه التطبيق المعتمد.",
                "You can adjust shifts, grace period, selected voice settings and sync. Changes are delivered through the server and applied by the authorized Store app."
            )))
            management.addView(UiKit.button(this, p, t("فتح إعدادات المحل عن بُعد", "Open remote Store settings")).apply { setOnClickListener { editRemoteStoreSettings() } })
'''
new='''            management.addView(UiKit.sectionLabel(this, p, t("إدارة الموظفين", "Employee management")))
            management.addView(UiKit.subtitle(this, p, t(
                "إضافة وتعديل وتفعيل الموظفين فقط. لا تمنح هذه الصلاحية إعدادات المحل أو صلاحيات مالك النظام، وربط هاتف الموظف الجديد يبقى من جهاز المحل المعتمد.",
                "Add, edit and enable employees only. This does not grant Store settings or System Owner privileges; pairing a new Employee phone remains on the authorized Store device."
            )))
            management.addView(UiKit.button(this, p, t("فتح إدارة الموظفين", "Open employee management")).apply { setOnClickListener { openEmployeeManagement() } })
'''
if old not in a:
    raise SystemExit('management card block not found')
a=a.replace(old,new)
needle2='''    private fun editRemoteStoreSettings() {
'''
methods='''    private fun openEmployeeManagement() {
        if (receiver.serverUrl.isBlank()) { editServerUrl(); return }
        Thread {
            val result = CentralServerClient.receiverManagedEmployees(receiver.serverUrl, receiver.receiverId, receiver.secret)
            runOnUiThread {
                if (result.isFailure) { info(t("إدارة الموظفين", "Employee management"), result.exceptionOrNull()?.message ?: t("تعذر جلب الموظفين", "Unable to load employees")); return@runOnUiThread }
                val employees=result.getOrThrow()
                val labels=mutableListOf(t("＋ إضافة موظف", "＋ Add employee"))
                labels.addAll(employees.map { e ->
                    val state=if(!e.enabled) t("موقوف", "Disabled") else if(e.pendingLink) t("بانتظار ربط الهاتف", "Awaiting phone pairing") else t("نشط", "Active")
                    "${e.employeeName} • ${e.employeeId} • $state"
                })
                AlertDialog.Builder(this).setTitle(t("إدارة الموظفين", "Employee management")).setItems(labels.toTypedArray()) { _, which ->
                    if(which==0) editManagedEmployee(null) else showManagedEmployeeActions(employees[which-1])
                }.setNegativeButton(t("إغلاق", "Close"), null).show()
            }
        }.start()
    }

    private fun editManagedEmployee(employee: CentralServerClient.ReceiverEmployee?) {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,8,24,0) }
        val id=UiKit.field(this,p,t("رقم الموظف", "Employee ID")).apply { setText(employee?.employeeId.orEmpty()); isEnabled=employee==null }
        val name=UiKit.field(this,p,t("اسم الموظف", "Employee name")).apply { setText(employee?.employeeName.orEmpty()) }
        val branch=UiKit.field(this,p,t("الفرع", "Branch")).apply { setText(employee?.branchId ?: "MAIN") }
        box.addView(id); box.addView(name); box.addView(branch)
        val dialog=AlertDialog.Builder(this).setTitle(if(employee==null)t("إضافة موظف", "Add employee") else t("تعديل الموظف", "Edit employee"))
            .setView(box).setPositiveButton(t("حفظ", "Save"),null).setNegativeButton(t("إلغاء", "Cancel"),null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val eid=id.text.toString().trim(); val ename=name.text.toString().trim(); val bid=branch.text.toString().trim().ifBlank { "MAIN" }
            if(eid.isBlank()){ id.error=t("رقم الموظف مطلوب", "Employee ID is required"); return@setOnClickListener }
            if(ename.length<2){ name.error=t("أدخل اسم الموظف", "Enter employee name"); return@setOnClickListener }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
            Thread {
                val r=if(employee==null) CentralServerClient.receiverAddEmployee(receiver.serverUrl,receiver.receiverId,receiver.secret,eid,ename,bid)
                else CentralServerClient.receiverUpdateEmployee(receiver.serverUrl,receiver.receiverId,receiver.secret,eid,ename,bid)
                runOnUiThread { if(r.isSuccess){ dialog.dismiss(); info(t("تم الحفظ ✓", "Saved ✓"), if(employee==null)t("تم إنشاء سجل الموظف. اربط هاتف الموظف من جهاز المحل المعتمد.", "Employee record created. Pair the Employee phone from the authorized Store device.") else t("تم تحديث بيانات الموظف.", "Employee details updated.")) } else { name.error=r.exceptionOrNull()?.message ?: t("تعذر الحفظ", "Unable to save"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true } }
            }.start()
        } }
        dialog.show()
    }

    private fun showManagedEmployeeActions(employee: CentralServerClient.ReceiverEmployee) {
        val actions=mutableListOf(t("تعديل الاسم والفرع", "Edit name and branch"), if(employee.enabled)t("إيقاف الموظف", "Disable employee") else t("تفعيل الموظف", "Enable employee"))
        if(receiver.canMessageEmployees) actions.add(t("إرسال رسالة", "Send message"))
        AlertDialog.Builder(this).setTitle("${employee.employeeName} • ${employee.employeeId}").setItems(actions.toTypedArray()) { _, which ->
            when(which){
                0 -> editManagedEmployee(employee)
                1 -> setManagedEmployeeEnabled(employee,!employee.enabled)
                2 -> if(receiver.canMessageEmployees) composeRemoteEmployeeMessage(employee)
            }
        }.setNegativeButton(t("إلغاء", "Cancel"),null).show()
    }

    private fun setManagedEmployeeEnabled(employee: CentralServerClient.ReceiverEmployee, enabled: Boolean) {
        Thread {
            val r=CentralServerClient.receiverSetEmployeeEnabled(receiver.serverUrl,receiver.receiverId,receiver.secret,employee.employeeId,enabled)
            runOnUiThread { if(r.isSuccess){ info(t("تم التحديث ✓", "Updated ✓"), if(enabled)t("تم تفعيل الموظف.", "Employee enabled.") else t("تم إيقاف الموظف إداريًا دون حذف بيانات الاقتران.", "Employee administratively disabled without deleting pairing data.")) } else info(t("تعذر التحديث", "Update failed"),r.exceptionOrNull()?.message ?: t("خطأ", "Error")) }
        }.start()
    }

'''
if 'private fun openEmployeeManagement()' not in a:
    a=a.replace(needle2,methods+needle2)
activity.write_text(a)
print('V122 employee-only management patch applied')
