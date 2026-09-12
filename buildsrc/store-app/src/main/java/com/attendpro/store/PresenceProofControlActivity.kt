package com.attendpro.store

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
import com.attendpro.core.SecretCodec
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
        val e = repo.employees().firstOrNull { it.employeeId == employeeId } ?: return
        val secret = SecretCodec.decode(e.pairingSecret) ?: ByteArray(0)
        val localSent = ChallengeDispatch1928.send(this, employeeId, secret, method, action = action)
        if(!repo.hasCentralCredentials() || repo.serverUrl.isBlank()) {
            AlertDialog.Builder(this).setMessage(if(localSent) "تم إرسال طلب الإثبات إلى $name محليًا عبر Bluetooth/Wi‑Fi." else "تعذر الإرسال المحلي. تأكد من Bluetooth أو اتصال الشبكة المحلية.").setPositiveButton("حسنًا",null).show(); return
        }
        Thread {
            val r=CentralServerClient.createPresenceChallenge(repo.serverUrl,repo.centralAccessToken,repo.storeId,DeviceIdentity(this),employeeId,method,action)
            runOnUiThread {
                val message = when {
                    r.isSuccess && localSent -> "تم إرسال طلب الإثبات إلى $name محليًا وعبر الخادم."
                    r.isSuccess -> "تم إرسال طلب الإثبات إلى $name عبر الخادم."
                    localSent -> "تم إرسال الطلب محليًا إلى $name؛ تعذر الخادم وسيستمر الطلب المحلي."
                    else -> "تعذر الإرسال المحلي والخادم: ${r.exceptionOrNull()?.message ?: "خطأ"}"
                }
                AlertDialog.Builder(this).setMessage(message).setPositiveButton("حسنًا",null).show()
            }
        }.start()
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
        val pending=goAsync(); Thread { try {
            val repo=StoreRepository(context); val id=prefs.getString("employeeId","").orEmpty()
            val e=repo.employees().firstOrNull{it.employeeId==id && it.active && it.companionEnabled}
            if(e!=null) {
                val method=runCatching{AttendanceMethod.valueOf(prefs.getString("method",AttendanceMethod.PHONE_BLE_BIOMETRIC.name)!!)}.getOrDefault(AttendanceMethod.PHONE_BLE_BIOMETRIC)
                val action=runCatching{AttendanceAction.valueOf(prefs.getString("action",AttendanceAction.CHECK_IN.name)!!)}.getOrDefault(AttendanceAction.CHECK_IN)
                val secret=SecretCodec.decode(e.pairingSecret) ?: ByteArray(0)
                ChallengeDispatch1928.send(context,e.employeeId,secret,method,action=action)
                if(repo.hasCentralCredentials() && repo.serverUrl.isNotBlank()) CentralServerClient.createPresenceChallenge(repo.serverUrl,repo.centralAccessToken,repo.storeId,DeviceIdentity(context),e.employeeId,method,action)
            }
        } finally { PresenceProofScheduler.schedule(context); pending.finish() } }.start()
    }
}
