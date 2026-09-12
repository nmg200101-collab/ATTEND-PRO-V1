#!/usr/bin/env python3
from pathlib import Path
R=Path('.')
main=R/'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt'
s=main.read_text(encoding='utf-8')
def req(old,new,count=1):
    global s
    if s.count(old)<count: raise SystemExit('missing anchor: '+old[:100])
    s=s.replace(old,new,count)
# BLE authenticated advertisement must count as live Bluetooth connection for UI.
req('if(!BleProtocol.verify(payload,employee.employeeId,secret))continue\n', 'if(!BleProtocol.verify(payload,employee.employeeId,secret))continue\n            if (channel.startsWith("Bluetooth", ignoreCase = true)) {\n                val bleNow = System.currentTimeMillis()\n                authenticatedPresenceAt[employee.employeeId] = bleNow\n                repo.markCompanionLinked(employee.employeeId, "Bluetooth BLE • موثّق", bleNow)\n            }\n',1)
# GPS recognition is visible connection state, but remains excluded as attendance proof elsewhere.
req('''    private fun isEmployeeActuallyConnected(employeeId: String, now: Long = System.currentTimeMillis()): Boolean =\n        isAuthenticatedPresenceConnected(employeeId, now) || isDirectBleUiConnected(employeeId, now) ||\n            isLanConnected(employeeId, now) || isServerPresenceConnected(employeeId, now)\n''','''    private fun isEmployeeActuallyConnected(employeeId: String, now: Long = System.currentTimeMillis()): Boolean =\n        isAuthenticatedPresenceConnected(employeeId, now) || isDirectBleUiConnected(employeeId, now) ||\n            isLanConnected(employeeId, now) || isServerPresenceConnected(employeeId, now) ||\n            isGpsRecognizedFresh(employeeId, now)\n''')
req('''                    if (isDirectBleUiConnected(id, now)) add("Bluetooth BLE • ACK")\n                    if (isLanConnected(id, now)) add("Wi‑Fi/Hotspot • ACK")\n                    if (isServerPresenceConnected(id, now)) add("Server • Heartbeat")\n''','''                    if (isDirectBleUiConnected(id, now)) add("Bluetooth BLE • ACK")\n                    else if (isAuthenticatedPresenceConnected(id, now)) add("Bluetooth BLE • موثّق")\n                    if (isLanConnected(id, now)) add("Wi‑Fi/Hotspot • ACK")\n                    if (isServerPresenceConnected(id, now)) add("Server • Heartbeat")\n                    if (isGpsRecognizedFresh(id, now)) add("GPS • داخل/قرب النطاق")\n''')
req('''            val statusText = when {\n                directAckAge <= 11_000L -> "● متصل ومؤكد عبر Bluetooth ACK"\n                lanAckAge <= 8_000L -> "● متصل ومؤكد عبر LAN ACK"\n                age <= 12_000L -> "◌ مكتشف/قريب فقط — بانتظار ACK"\n''','''            val statusText = when {\n                directAckAge <= 11_000L -> "● متصل ومؤكد عبر Bluetooth ACK"\n                isAuthenticatedPresenceConnected(employee.employeeId, now) -> "● متصل عبر Bluetooth BLE موثّق"\n                lanAckAge <= 8_000L -> "● متصل ومؤكد عبر LAN ACK"\n                isGpsRecognizedFresh(employee.employeeId, now) -> "● متعرّف عبر GPS داخل/قرب النطاق"\n                age <= 12_000L -> "◌ مكتشف/قريب فقط — بانتظار ACK"\n''')
# Add owner control entry.
req('''        OwnerShortcut("presence_challenge", "طلب إثبات حضور"),\n''','''        OwnerShortcut("presence_challenge", "طلب إثبات حضور الآن"),\n        OwnerShortcut("presence_control", "التحكم بطلبات إثبات الحضور"),\n''')
req('''            "presence_challenge" -> showPresenceChallenge()\n''','''            "presence_challenge" -> showPresenceChallenge()\n            "presence_control" -> startActivity(Intent(this, PresenceProofControlActivity::class.java))\n''')
# Add to top overflow near update item.
req('''            t("البحث عن تحديثات التطبيق", "Check for app updates") to {\n                DistributionUpdateManager.check(this, repo.serverUrl.ifBlank { DistributionUpdateManager.DEFAULT_SERVER }, "store", manual = true)\n            },\n''','''            t("البحث عن تحديثات التطبيق", "Check for app updates") to {\n                DistributionUpdateManager.check(this, repo.serverUrl.ifBlank { DistributionUpdateManager.DEFAULT_SERVER }, "store", manual = true)\n            },\n            t("التحكم بإثبات الحضور", "Presence proof control") to {\n                startActivity(Intent(this, PresenceProofControlActivity::class.java))\n            },\n''')
main.write_text(s,encoding='utf-8')

activity=R/'buildsrc/store-app/src/main/java/com/attendpro/store/PresenceProofControlActivity.kt'
activity.write_text(r'''package com.attendpro.store

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.attendpro.core.AttendanceAction
import com.attendpro.core.AttendanceMethod
import com.attendpro.core.CentralServerClient
import com.attendpro.core.DeviceIdentity
import com.attendpro.core.StoreRepository
import com.attendpro.core.UiKit
import java.util.Calendar

class PresenceProofControlActivity : Activity() {
    private lateinit var repo: StoreRepository
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val p by lazy { UiKit.palette(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = StoreRepository(this)
        render()
    }

    private fun render() {
        val employees = repo.employees().filter { it.active && it.companionEnabled }
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; layoutDirection=View.LAYOUT_DIRECTION_RTL; setPadding(UiKit.dp(this@PresenceProofControlActivity,16),UiKit.dp(this@PresenceProofControlActivity,18),UiKit.dp(this@PresenceProofControlActivity,16),UiKit.dp(this@PresenceProofControlActivity,24)); setBackgroundColor(p.bg) }
        root.addView(UiKit.title(this,p,"التحكم بطلبات إثبات الحضور"))
        root.addView(UiKit.subtitle(this,p,"أرسل طلب إثبات فورًا، أو فعّل إرسالًا تلقائيًا بوقت محدد أو بفاصل زمني. الطلب يصل كتنبّه إلى تطبيق الموظف ويُلزم بالطريقة التي يحددها صاحب المحل."))
        if (employees.isEmpty()) { root.addView(UiKit.subtitle(this,p,"لا يوجد موظف نشط مرتبط بتطبيق الموظف.")); setContentView(root); return }

        val employee = Spinner(this).apply { adapter=ArrayAdapter(this@PresenceProofControlActivity,android.R.layout.simple_spinner_dropdown_item,employees.map{it.displayName}) }
        val methodLabels=arrayOf("بصمة/وجه الهاتف","بصمة الهاتف","كلمة المرور")
        val methodValues=arrayOf(AttendanceMethod.PHONE_BLE_BIOMETRIC,AttendanceMethod.PHONE_FINGERPRINT,AttendanceMethod.PASSWORD)
        val method=Spinner(this).apply { adapter=ArrayAdapter(this@PresenceProofControlActivity,android.R.layout.simple_spinner_dropdown_item,methodLabels.toList()) }
        val action=Spinner(this).apply { adapter=ArrayAdapter(this@PresenceProofControlActivity,android.R.layout.simple_spinner_dropdown_item,listOf("إثبات حضور","إثبات انصراف")) }
        listOf("الموظف" to employee,"طريقة الإثبات" to method,"نوع الطلب" to action).forEach { (label,v) -> root.addView(UiKit.sectionLabel(this,p,label)); root.addView(v) }

        root.addView(UiKit.button(this,p,"إرسال طلب الآن",true).apply { setOnClickListener {
            val e=employees[employee.selectedItemPosition]; val m=methodValues[method.selectedItemPosition]; val a=if(action.selectedItemPosition==0) AttendanceAction.CHECK_IN else AttendanceAction.CHECK_OUT
            sendNow(e.employeeId,e.displayName,m,a)
        }})

        val enabled=CheckBox(this).apply { text="تفعيل الإرسال التلقائي"; setTextColor(p.text); isChecked=prefs.getBoolean("enabled",false) }
        root.addView(enabled)
        val modeLabels=listOf("يوميًا في وقت محدد","كل 30 دقيقة","كل ساعة","كل ساعتين","كل 4 ساعات")
        val modeValues=arrayOf(0,30,60,120,240)
        val mode=Spinner(this).apply { adapter=ArrayAdapter(this@PresenceProofControlActivity,android.R.layout.simple_spinner_dropdown_item,modeLabels); setSelection(modeValues.indexOf(prefs.getInt("interval",0)).coerceAtLeast(0)) }
        val hour=Spinner(this).apply { adapter=ArrayAdapter(this@PresenceProofControlActivity,android.R.layout.simple_spinner_dropdown_item,(0..23).map{String.format("%02d",it)}); setSelection(prefs.getInt("hour",9)) }
        val minute=Spinner(this).apply { adapter=ArrayAdapter(this@PresenceProofControlActivity,android.R.layout.simple_spinner_dropdown_item,(0..59 step 5).map{String.format("%02d",it)}); setSelection((prefs.getInt("minute",0)/5).coerceIn(0,11)) }
        root.addView(UiKit.sectionLabel(this,p,"الإرسال التلقائي")); root.addView(mode)
        val timeRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; addView(hour,LinearLayout.LayoutParams(0,-2,1f)); addView(TextView(this@PresenceProofControlActivity).apply{text=":";textSize=22f;setTextColor(p.text)}); addView(minute,LinearLayout.LayoutParams(0,-2,1f)) }
        root.addView(timeRow)
        root.addView(UiKit.subtitle(this,p,"عند اختيار الفاصل الزمني يبدأ الحساب من لحظة الحفظ. عند اختيار يومي يستخدم الوقت أعلاه. Android قد يؤخر التنفيذ دقائق قليلة لتوفير البطارية."))
        root.addView(UiKit.button(this,p,"حفظ جدول الإثبات التلقائي",false).apply { setOnClickListener {
            val e=employees[employee.selectedItemPosition]; val m=methodValues[method.selectedItemPosition]; val a=if(action.selectedItemPosition==0) AttendanceAction.CHECK_IN else AttendanceAction.CHECK_OUT
            prefs.edit().putBoolean("enabled",enabled.isChecked).putString("employeeId",e.employeeId).putString("employeeName",e.displayName).putString("method",m.name).putString("action",a.name).putInt("interval",modeValues[mode.selectedItemPosition]).putInt("hour",hour.selectedItemPosition).putInt("minute",minute.selectedItemPosition*5).apply()
            if(enabled.isChecked) PresenceProofScheduler.schedule(this@PresenceProofControlActivity) else PresenceProofScheduler.cancel(this@PresenceProofControlActivity)
            AlertDialog.Builder(this@PresenceProofControlActivity).setMessage(if(enabled.isChecked) "تم تفعيل جدول إثبات الحضور التلقائي." else "تم إيقاف الإرسال التلقائي.").setPositiveButton("حسنًا",null).show()
        }})
        root.addView(UiKit.button(this,p,"إيقاف الإرسال التلقائي",false).apply { setOnClickListener { enabled.isChecked=false; prefs.edit().putBoolean("enabled",false).apply(); PresenceProofScheduler.cancel(this@PresenceProofControlActivity) } })
        setContentView(ScrollView(this).apply{addView(root)})
    }

    private fun sendNow(employeeId:String,name:String,method:AttendanceMethod,action:AttendanceAction) {
        if(!repo.hasCentralCredentials() || repo.serverUrl.isBlank()) { AlertDialog.Builder(this).setMessage("الإرسال اليدوي المجدول يحتاج اتصال الخادم. يمكن استخدام زر إثبات الحضور في الشاشة الرئيسية للقناة المحلية.").setPositiveButton("حسنًا",null).show(); return }
        Thread { val r=CentralServerClient.createPresenceChallenge(repo.serverUrl,repo.centralAccessToken,repo.storeId,DeviceIdentity(this),employeeId,method,action); runOnUiThread { AlertDialog.Builder(this).setMessage(if(r.isSuccess) "تم إرسال طلب الإثبات إلى $name." else "تعذر الإرسال: ${r.exceptionOrNull()?.message ?: "خطأ"}").setPositiveButton("حسنًا",null).show() } }.start()
    }

    companion object { const val PREFS="presence_proof_control_rc5" }
}

object PresenceProofScheduler {
    const val ACTION="com.attendpro.store.ACTION_PRESENCE_PROOF_RC5"
    fun schedule(context:Context) {
        val prefs=context.getSharedPreferences(PresenceProofControlActivity.PREFS,Context.MODE_PRIVATE); if(!prefs.getBoolean("enabled",false)) return
        val interval=prefs.getInt("interval",0); val now=System.currentTimeMillis()
        val trigger=if(interval>0) now+interval*60_000L else Calendar.getInstance().run { val h=prefs.getInt("hour",9); val m=prefs.getInt("minute",0); set(Calendar.HOUR_OF_DAY,h);set(Calendar.MINUTE,m);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0);if(timeInMillis<=now)add(Calendar.DAY_OF_YEAR,1);timeInMillis }
        val alarm=context.getSystemService(AlarmManager::class.java)?:return
        val pi=PendingIntent.getBroadcast(context,50515,Intent(context,PresenceProofReceiver::class.java).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi)
    }
    fun cancel(context:Context) { val alarm=context.getSystemService(AlarmManager::class.java)?:return; val pi=PendingIntent.getBroadcast(context,50515,Intent(context,PresenceProofReceiver::class.java).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); alarm.cancel(pi) }
}

class PresenceProofReceiver: BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent?) {
        if(intent?.action!=PresenceProofScheduler.ACTION && intent?.action!=Intent.ACTION_BOOT_COMPLETED && intent?.action!=Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs=context.getSharedPreferences(PresenceProofControlActivity.PREFS,Context.MODE_PRIVATE); if(!prefs.getBoolean("enabled",false)) return
        if(intent.action==Intent.ACTION_BOOT_COMPLETED || intent.action==Intent.ACTION_MY_PACKAGE_REPLACED) { PresenceProofScheduler.schedule(context); return }
        val pending=goAsync(); Thread { try { val repo=StoreRepository(context); val id=prefs.getString("employeeId","").orEmpty(); val e=repo.employees().firstOrNull{it.employeeId==id && it.active && it.companionEnabled}; if(e!=null && repo.hasCentralCredentials() && repo.serverUrl.isNotBlank()) { val method=runCatching{AttendanceMethod.valueOf(prefs.getString("method",AttendanceMethod.PHONE_BLE_BIOMETRIC.name)!!)}.getOrDefault(AttendanceMethod.PHONE_BLE_BIOMETRIC); val action=runCatching{AttendanceAction.valueOf(prefs.getString("action",AttendanceAction.CHECK_IN.name)!!)}.getOrDefault(AttendanceAction.CHECK_IN); CentralServerClient.createPresenceChallenge(repo.serverUrl,repo.centralAccessToken,repo.storeId,DeviceIdentity(context),e.employeeId,method,action) } } finally { PresenceProofScheduler.schedule(context); pending.finish() } }.start()
    }
}
''',encoding='utf-8')

manifest=R/'buildsrc/store-app/src/main/AndroidManifest.xml'
m=manifest.read_text(encoding='utf-8')
anchor='''        <activity android:name=".StoreSettingsActivity" android:screenOrientation="portrait" android:exported="false" />\n'''
if anchor not in m: raise SystemExit('manifest anchor missing')
m=m.replace(anchor,anchor+'''        <activity android:name=".PresenceProofControlActivity" android:screenOrientation="portrait" android:exported="false" />\n        <receiver android:name=".PresenceProofReceiver" android:exported="false">\n            <intent-filter>\n                <action android:name="android.intent.action.BOOT_COMPLETED" />\n                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />\n                <action android:name="com.attendpro.store.ACTION_PRESENCE_PROOF_RC5" />\n            </intent-filter>\n        </receiver>\n''')
manifest.write_text(m,encoding='utf-8')

for rel in ['buildsrc/store-app/build.gradle.kts','buildsrc/employee-app/build.gradle.kts']:
    pth=R/rel; x=pth.read_text(encoding='utf-8').replace('versionCode = 94','versionCode = 95').replace('versionName = "2.0.0-RC4"','versionName = "2.0.0-RC5"'); pth.write_text(x,encoding='utf-8')
# release docs
old=(R/'RELEASE_NOTES_V2.0.0_RC4.md').read_text(encoding='utf-8')
(R/'RELEASE_NOTES_V2.0.0_RC5.md').write_text(old.replace('2.0.0-RC4','2.0.0-RC5').replace('versionCode 94','versionCode 95')+'\n\n## RC5 connectivity + presence proof control\n- Authenticated BLE advertisements again count as Bluetooth-connected in the Store connected-devices UI.\n- Fresh GPS INSIDE/NEAR recognition appears as GPS-connected/recognized, while GPS alone still never records attendance.\n- New owner Presence Proof Control screen: manual send, daily time, 30/60/120/240-minute schedules, selected employee/method/action, boot/update rescheduling.\n- Protected QR/BLE pairing protocol files are unchanged.\n',encoding='utf-8')
oldc=(R/'FIELD_TEST_CHECKLIST_RC4.md').read_text(encoding='utf-8')
(R/'FIELD_TEST_CHECKLIST_RC5.md').write_text(oldc.replace('RC4','RC5').replace('2.0.0-RC4','2.0.0-RC5').replace('versionCode 94','versionCode 95')+'\n\n## RC5 targeted checks\n- [ ] Bluetooth only: authenticated BLE advertisement shows employee connected without Hotspot/Wi-Fi.\n- [ ] GPS fresh INSIDE/NEAR shows employee recognized/connected via GPS; OUTSIDE/stale clears.\n- [ ] GPS alone does not create attendance.\n- [ ] Owner can send proof now and configure automatic daily/interval requests.\n- [ ] Automatic schedule survives app update/reboot.\n',encoding='utf-8')
print('RC5 patch applied')
