from pathlib import Path

ROOT=Path("buildsrc")

def read(rel): return (ROOT/rel).read_text(encoding='utf-8')
def write(rel,s): (ROOT/rel).write_text(s,encoding='utf-8')
def must_replace(s, old, new, label, count=1):
    if s.count(old) < count:
        raise SystemExit(f'missing anchor {label}: found {s.count(old)}')
    return s.replace(old,new,count)

# ---- Store MainActivity ----
rel='store-app/src/main/java/com/attendpro/store/MainActivity.kt'
s=read(rel)
s=must_replace(s,'    private fun buildElegantUi() {\n','    private fun buildStoreMainTemplate1978() {\n','rename store elegant')

insert_before='    private fun buildStoreMainTemplate1978() {\n'
helpers=r'''    private fun storeHomeTemplate1978(): String = getSharedPreferences("attend_home_template_1978", MODE_PRIVATE)
        .getString("store_template", "MAIN") ?: "MAIN"

    private fun setStoreHomeTemplate1978(value: String) {
        getSharedPreferences("attend_home_template_1978", MODE_PRIVATE).edit().putString("store_template", value).apply()
    }

    private fun storeTemplateTitle1978(value: String = storeHomeTemplate1978()): String = when (value) {
        "SECTIONS" -> "الأقسام"
        "CLASSIC" -> "الكلاسيكي"
        else -> "الرئيسي"
    }

    private fun buildElegantUi() {
        when (storeHomeTemplate1978()) {
            "SECTIONS" -> buildStoreSectionsTemplate1978()
            "CLASSIC" -> buildUi()
            else -> buildStoreMainTemplate1978()
        }
    }

    private fun homeTemplateChip1978(): TextView = TextView(this).apply {
        text = "النموذج: ${storeTemplateTitle1978()} ▾"
        textSize = 12.5f
        gravity = Gravity.CENTER
        setTextColor(android.graphics.Color.WHITE)
        setPadding(UiKit.dp(this@MainActivity, 12), UiKit.dp(this@MainActivity, 7), UiKit.dp(this@MainActivity, 12), UiKit.dp(this@MainActivity, 7))
        background = GradientDrawable().apply {
            cornerRadius = UiKit.dp(this@MainActivity, 18).toFloat()
            setColor(android.graphics.Color.argb(38,255,255,255))
            setStroke(UiKit.dp(this@MainActivity, 1), android.graphics.Color.argb(110,255,255,255))
        }
        setOnClickListener { showStoreHomeTemplatePicker1978() }
    }

    private fun showStoreHomeTemplatePicker1978() {
        val ids = arrayOf("MAIN", "SECTIONS", "CLASSIC")
        val labels = arrayOf(
            "الرئيسي — لوحة الحضور اليومية المتوازنة",
            "الأقسام — وصول سريع للخدمات على شكل أقسام",
            "الكلاسيكي — عرض تفصيلي تقليدي"
        )
        val current = ids.indexOf(storeHomeTemplate1978()).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("نمط الشاشة الرئيسية")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                setStoreHomeTemplate1978(ids[which]); dialog.dismiss(); buildElegantUi(); refreshDashboard()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun addStoreTabs1978(root: LinearLayout) {
        val box = UiKit.card(this, p, 7)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; gravity = Gravity.CENTER }
        fun tab(label: String, selected: Boolean = false, action: () -> Unit): TextView = TextView(this).apply {
            text = label; textSize = 12.2f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (selected) android.graphics.Color.WHITE else p.text)
            setPadding(UiKit.dp(this@MainActivity, 5), UiKit.dp(this@MainActivity, 10), UiKit.dp(this@MainActivity, 5), UiKit.dp(this@MainActivity, 10))
            background = GradientDrawable().apply { cornerRadius=UiKit.dp(this@MainActivity,14).toFloat(); setColor(if(selected) p.primary else p.surface2) }
            layoutParams = LinearLayout.LayoutParams(0, UiKit.dp(this@MainActivity, 42), 1f).apply { marginStart=UiKit.dp(this@MainActivity,2); marginEnd=UiKit.dp(this@MainActivity,2) }
            setOnClickListener { action() }
        }
        row.addView(tab("الرئيسية", true) { })
        row.addView(tab("الحضور") { showAttendanceMethods("تسجيل الحضور والانصراف") })
        row.addView(tab("الاتصال") { showConnectionCenter() })
        row.addView(tab("الإدارة") { requireStoreOwner("إدارة المحل") { showStoreOwnerHub() } })
        box.addView(row); root.addView(box)
    }

    private fun buildStoreSectionsTemplate1978() {
        window.statusBarColor = p.bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(UiKit.dp(this@MainActivity,12),UiKit.dp(this@MainActivity,12),UiKit.dp(this@MainActivity,12),UiKit.dp(this@MainActivity,30)); setBackgroundColor(p.bg)
        }
        val header = UiKit.heroCard(this,p,12)
        header.addView(TextView(this).apply {
            text="⋮"; textSize=28f; gravity=Gravity.CENTER; setTextColor(android.graphics.Color.WHITE); contentDescription="القائمة"
            layoutParams=LinearLayout.LayoutParams(UiKit.dp(this@MainActivity,48),UiKit.dp(this@MainActivity,44)).apply{gravity=Gravity.END}
            setOnClickListener{showStoreMainMenu1976()}
        })
        header.addView(homeTemplateChip1978().apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity=Gravity.START } })
        header.addView(UiKit.title(this,p,"ATTEND PRO",22f).apply{gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE)})
        storeSummary=UiKit.subtitle(this,p,storeSummaryText()).apply{gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE)}
        header.addView(storeSummary)
        header.addView(UiKit.subtitle(this,p,"لوحة الأقسام • ${attendProVersionName()}").apply{gravity=Gravity.CENTER;setTextColor(android.graphics.Color.argb(220,255,255,255))})
        root.addView(header)
        addStoreTabs1978(root)

        status=TextView(this).apply{text="النظام جاهز";textSize=12.5f;setTextColor(p.muted);gravity=Gravity.CENTER}
        counts=TextView(this).apply{textSize=15.5f;setTextColor(p.text);setTypeface(typeface,Typeface.BOLD);gravity=Gravity.CENTER;setLineSpacing(0f,1.12f)}
        val today=UiKit.card(this,p,10); today.addView(UiKit.sectionLabel(this,p,"ملخص اليوم")); today.addView(counts); root.addView(today)

        fun sectionTile(title:String,subtitle:String,action:()->Unit)=UiKit.card(this,p,11).apply{
            addView(UiKit.title(this@MainActivity,p,title,16.5f).apply{gravity=Gravity.CENTER})
            addView(UiKit.subtitle(this@MainActivity,p,subtitle).apply{gravity=Gravity.CENTER;maxLines=3})
            UiKit.makeInteractive(this,this@MainActivity,p);setOnClickListener{action()}
        }
        linkedEmployeesSummaryView=UiKit.subtitle(this,p,"جاري تحميل الحضور…")
        connectionSummaryView=UiKit.subtitle(this,p,"جاري فحص الاتصال…")
        val row1=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;gravity=Gravity.TOP}
        val a=sectionTile("الحضور الآن","الموظفون الموجودون وحالتهم"){showLiveAttendanceNow()}; a.addView(linkedEmployeesSummaryView.apply{gravity=Gravity.CENTER})
        val b=sectionTile("الأجهزة والاتصال","Bluetooth • Wi‑Fi • الخادم"){showConnectionCenter()}; b.addView(connectionSummaryView.apply{gravity=Gravity.CENTER})
        a.layoutParams=LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply{marginEnd=UiKit.dp(this@MainActivity,4)}
        b.layoutParams=LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply{marginStart=UiKit.dp(this@MainActivity,4)}
        row1.addView(a);row1.addView(b);root.addView(row1)

        val operations=UiKit.card(this,p,10); operations.addView(UiKit.sectionLabel(this,p,"الأقسام"))
        val row2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
        row2.addView(UiKit.button(this,p,"الموظفون",false).apply{layoutParams=LinearLayout.LayoutParams(0,UiKit.dp(this@MainActivity,50),1f).apply{marginEnd=4};setOnClickListener{requireStoreOwner("الموظفون"){startActivity(Intent(this@MainActivity,StoreSettingsActivity::class.java))}}})
        row2.addView(UiKit.button(this,p,"الرسائل",false).apply{layoutParams=LinearLayout.LayoutParams(0,UiKit.dp(this@MainActivity,50),1f).apply{marginStart=4};setOnClickListener{startActivity(Intent(this@MainActivity,StoreMessages1975Activity::class.java))}})
        operations.addView(row2)
        val row3=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
        row3.addView(UiKit.button(this,p,"التقارير",false).apply{layoutParams=LinearLayout.LayoutParams(0,UiKit.dp(this@MainActivity,50),1f).apply{marginEnd=4};setOnClickListener{startActivity(Intent(this@MainActivity,ReportsActivity::class.java))}})
        row3.addView(UiKit.button(this,p,"إدارة المحل",false).apply{layoutParams=LinearLayout.LayoutParams(0,UiKit.dp(this@MainActivity,50),1f).apply{marginStart=4};setOnClickListener{requireStoreOwner("إدارة المحل"){showStoreOwnerHub()}}})
        operations.addView(row3);root.addView(operations)

        recentAttendanceSummaryView=UiKit.subtitle(this,p,"لا توجد عملية اليوم").apply{gravity=Gravity.CENTER}
        val recent=UiKit.card(this,p,8);recent.addView(UiKit.sectionLabel(this,p,"آخر حركة"));recent.addView(recentAttendanceSummaryView);UiKit.makeInteractive(recent,this,p);recent.setOnClickListener{showConnectionAttendanceHistory()};root.addView(recent)
        root.addView(UiKit.card(this,p,7).apply{addView(status)})
        setContentView(ScrollView(this).apply{isFillViewport=true;setBackgroundColor(p.bg);addView(root)})
    }

'''
s=s.replace(insert_before,helpers+insert_before,1)

old='''        header.addView(UiKit.title(this, p, "ATTEND PRO", 21f).apply {
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        })
'''
new='''        header.addView(homeTemplateChip1978().apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.START } })
        header.addView(UiKit.title(this, p, "ATTEND PRO", 21f).apply {
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        })
'''
s=must_replace(s,old,new,'store main selector')
s=must_replace(s,'        root.addView(header)\n\n        status = TextView(this).apply {\n','        root.addView(header)\n        addStoreTabs1978(root)\n\n        status = TextView(this).apply {\n','store main tabs')

start=s.find('        val systemOwner = UiKit.heroCard(this, p, 11).apply {')
if start<0: raise SystemExit('store systemOwner hero not found')
end=s.find('        root.addView(systemOwner)\n',start)
if end<0: raise SystemExit('store systemOwner hero end missing')
end += len('        root.addView(systemOwner)\n')
s=s[:start]+s[end:]

old='''        val header = UiKit.heroCard(this, p, 16).apply { gravity = Gravity.CENTER_HORIZONTAL }
        header.addView(ImageView(this).apply {
'''
new='''        val header = UiKit.heroCard(this, p, 16).apply { gravity = Gravity.CENTER_HORIZONTAL }
        header.addView(TextView(this).apply {
            text="⋮"; textSize=28f; gravity=Gravity.CENTER; setTextColor(android.graphics.Color.WHITE); contentDescription="القائمة"
            layoutParams=LinearLayout.LayoutParams(UiKit.dp(this@MainActivity,48),UiKit.dp(this@MainActivity,44)).apply{gravity=Gravity.END}
            setOnClickListener{showStoreMainMenu1976()}
        })
        header.addView(homeTemplateChip1978().apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity=Gravity.START } })
        header.addView(ImageView(this).apply {
'''
s=must_replace(s,old,new,'store classic selector')
pos=s.find('    private fun buildUi() {')
ri=s.find('        root.addView(header)\n',pos)
s=s[:ri]+s[ri:].replace('        root.addView(header)\n','        root.addView(header)\n        addStoreTabs1978(root)\n',1)

old='''        showLayeredMenu1977("القائمة", listOf(
            "الإشعارات" to { startActivity(Intent(this, StoreMessages1975Activity::class.java)) },
            "دليل مستخدم إدارة المحل" to { showStoreUserGuide1976() },
            "الإعدادات" to { startActivity(Intent(this, StoreSettingsActivity::class.java)) }
        ))
'''
new='''        showLayeredMenu1977("القائمة", listOf(
            "الإشعارات" to { startActivity(Intent(this, StoreMessages1975Activity::class.java)) },
            "نمط الشاشة الرئيسية" to { showStoreHomeTemplatePicker1978() },
            "دليل مستخدم إدارة المحل" to { showStoreUserGuide1976() },
            "الإعدادات" to { startActivity(Intent(this, StoreSettingsActivity::class.java)) },
            "منطقة إدارة النظام" to { startActivity(Intent(this, SystemSettingsActivity::class.java).putExtra("OWNER_ONLY_1978", true)) }
        ))
'''
s=must_replace(s,old,new,'store menu 1978')

start=s.find('        val systemOwner = UiKit.card(this, p, 12)')
if start>=0:
    end=s.find('        root.addView(systemOwner)\n',start)
    if end<0: raise SystemExit('activation system owner end missing')
    end += len('        root.addView(systemOwner)\n')
    tiny='''        val advancedAdmin = TextView(this).apply {
            text = "إدارة النظام"; textSize = 11f; gravity = Gravity.CENTER; setTextColor(p.muted)
            setPadding(0, UiKit.dp(this@MainActivity, 8), 0, UiKit.dp(this@MainActivity, 8))
            setOnClickListener { startActivity(Intent(this@MainActivity, SystemSettingsActivity::class.java).putExtra("OWNER_ONLY_1978", true)) }
        }
        root.addView(advancedAdmin)
'''
    s=s[:start]+tiny+s[end:]
write(rel,s)

# ---- System Settings owner-only entry ----
rel='store-app/src/main/java/com/attendpro/store/SystemSettingsActivity.kt'
s=read(rel)
s=must_replace(s,'        repo.ensureCurrentStoreInManagement()\n        showGateway()\n','''        repo.ensureCurrentStoreInManagement()
        if (intent?.getBooleanExtra("OWNER_ONLY_1978", false) == true) showOwnerOnlyEntry1978() else showGateway()
''','owner only onCreate')
anchor='    private fun showGateway() {\n'
owner_entry=r'''    private fun showOwnerOnlyEntry1978() {
        navigationScreen1977 = "OWNER_GATE"
        val root = baseRoot("منطقة إدارة النظام", "مدخل محمي مخصص لمالك نظام ATTEND PRO")
        val gate = UiKit.card(this, p, 12)
        gate.addView(UiKit.sectionLabel(this, p, "إدارة عليا"))
        gate.addView(UiKit.subtitle(this, p, "هذه المنطقة ليست جزءًا من تشغيل المحل اليومي. الدخول يتطلب رمز مالك النظام."))
        gate.addView(UiKit.button(this, p, "دخول مالك النظام").apply { setOnClickListener { ownerLogin() } })
        root.addView(gate)
        root.addView(UiKit.button(this, p, "رجوع", false).apply { setOnClickListener { finish() } })
        display(root)
    }

'''
s=s.replace(anchor,owner_entry+anchor,1)
write(rel,s)

# ---- Employee MainActivity ----
rel='employee-app/src/main/java/com/attendpro/employee/MainActivity.kt'
s=read(rel)
s=must_replace(s,'    private fun buildElegantUi() {\n','    private fun buildEmployeeMainTemplate1978() {\n','rename employee elegant')
anchor='    private fun buildEmployeeMainTemplate1978() {\n'
helpers=r'''    private fun employeeHomeTemplate1978(): String = getSharedPreferences("attend_home_template_1978", MODE_PRIVATE)
        .getString("employee_template", "MAIN") ?: "MAIN"

    private fun setEmployeeHomeTemplate1978(value: String) {
        getSharedPreferences("attend_home_template_1978", MODE_PRIVATE).edit().putString("employee_template", value).apply()
    }

    private fun employeeTemplateTitle1978(value: String = employeeHomeTemplate1978()): String = when(value) {
        "SECTIONS" -> "الأقسام"
        "CLASSIC" -> "الكلاسيكي"
        else -> "الرئيسي"
    }

    private fun buildElegantUi() {
        when(employeeHomeTemplate1978()) {
            "SECTIONS" -> buildEmployeeSectionsTemplate1978()
            "CLASSIC" -> buildUi()
            else -> buildEmployeeMainTemplate1978()
        }
    }

    private fun employeeTemplateChip1978(): TextView = TextView(this).apply {
        text="النموذج: ${employeeTemplateTitle1978()} ▾";textSize=12.5f;gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE)
        setPadding(UiKit.dp(this@MainActivity,12),UiKit.dp(this@MainActivity,7),UiKit.dp(this@MainActivity,12),UiKit.dp(this@MainActivity,7))
        background=GradientDrawable().apply{cornerRadius=UiKit.dp(this@MainActivity,18).toFloat();setColor(android.graphics.Color.argb(38,255,255,255));setStroke(UiKit.dp(this@MainActivity,1),android.graphics.Color.argb(110,255,255,255))}
        setOnClickListener{showEmployeeHomeTemplatePicker1978()}
    }

    private fun showEmployeeHomeTemplatePicker1978() {
        val ids=arrayOf("MAIN","SECTIONS","CLASSIC")
        val labels=arrayOf("الرئيسي — الحضور وحالة الاتصال اليومية","الأقسام — وصول سريع للحضور والرسائل والاتصال","الكلاسيكي — العرض التفصيلي التقليدي")
        val current=ids.indexOf(employeeHomeTemplate1978()).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("نمط الشاشة الرئيسية").setSingleChoiceItems(labels,current){dialog,which->
            setEmployeeHomeTemplate1978(ids[which]);dialog.dismiss();buildElegantUi();refreshProfile();updateConnectionSummary()
        }.setNegativeButton("إلغاء",null).show()
    }

    private fun addEmployeeTabs1978(root:LinearLayout) {
        val box=UiKit.card(this,p,7)
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;gravity=Gravity.CENTER}
        fun tab(label:String,selected:Boolean=false,action:()->Unit)=TextView(this).apply{
            text=label;textSize=12.2f;gravity=Gravity.CENTER;setTypeface(typeface,Typeface.BOLD);setTextColor(if(selected)android.graphics.Color.WHITE else p.text)
            setPadding(UiKit.dp(this@MainActivity,5),UiKit.dp(this@MainActivity,10),UiKit.dp(this@MainActivity,5),UiKit.dp(this@MainActivity,10))
            background=GradientDrawable().apply{cornerRadius=UiKit.dp(this@MainActivity,14).toFloat();setColor(if(selected)p.primary else p.surface2)}
            layoutParams=LinearLayout.LayoutParams(0,UiKit.dp(this@MainActivity,42),1f).apply{marginStart=UiKit.dp(this@MainActivity,2);marginEnd=UiKit.dp(this@MainActivity,2)};setOnClickListener{action()}
        }
        row.addView(tab("الرئيسية",true){})
        row.addView(tab("الحضور"){showEmployeeAttendanceCenter()})
        row.addView(tab("الرسائل"){startActivity(Intent(this@MainActivity,EmployeeMessages1975Activity::class.java))})
        row.addView(tab("الاتصال"){showEmployeeConnectionControl1977()})
        box.addView(row);root.addView(box)
    }

    private fun buildEmployeeSectionsTemplate1978() {
        window.statusBarColor=p.bg
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(UiKit.dp(this@MainActivity,13),UiKit.dp(this@MainActivity,13),UiKit.dp(this@MainActivity,13),UiKit.dp(this@MainActivity,28));setBackgroundColor(p.bg)}
        val header=UiKit.heroCard(this,p,12)
        header.addView(TextView(this).apply{text="⋮";textSize=28f;gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE);contentDescription="القائمة";layoutParams=LinearLayout.LayoutParams(UiKit.dp(this@MainActivity,48),UiKit.dp(this@MainActivity,44)).apply{gravity=Gravity.END};setOnClickListener{showEmployeeMainMenu1976()}})
        header.addView(employeeTemplateChip1978().apply{layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{gravity=Gravity.START}})
        header.addView(UiKit.title(this,p,"ATTEND PRO",22f).apply{gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE)})
        header.addView(UiKit.subtitle(this,p,"تطبيق الموظف • لوحة الأقسام • ${attendProVersionName()}").apply{gravity=Gravity.CENTER;setTextColor(android.graphics.Color.argb(225,255,255,255))})
        root.addView(header);addEmployeeTabs1978(root)
        status=TextView(this).apply{text="جاهز";textSize=13f;setTextColor(p.muted);gravity=Gravity.CENTER}
        profile=TextView(this).apply{textSize=14f;setTextColor(p.text);setTypeface(typeface,Typeface.BOLD);gravity=Gravity.CENTER;maxLines=4}
        connectionSummary=TextView(this).apply{textSize=13f;setTextColor(p.text);gravity=Gravity.CENTER;maxLines=4}
        val identityCard=UiKit.card(this,p,10);identityCard.addView(UiKit.sectionLabel(this,p,"حسابي"));identityCard.addView(profile);root.addView(identityCard)
        val attend=UiKit.card(this,p,12);attend.addView(UiKit.title(this,p,"الحضور والانصراف",18f).apply{gravity=Gravity.CENTER})
        val ar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
        ar.addView(UiKit.button(this,p,"تسجيل حضور").apply{layoutParams=LinearLayout.LayoutParams(0,UiKit.dp(this@MainActivity,50),1f).apply{marginEnd=4};setOnClickListener{chooseAttendanceMethod(AttendanceAction.CHECK_IN)}})
        ar.addView(UiKit.button(this,p,"تسجيل انصراف",false).apply{layoutParams=LinearLayout.LayoutParams(0,UiKit.dp(this@MainActivity,50),1f).apply{marginStart=4};setOnClickListener{chooseAttendanceMethod(AttendanceAction.CHECK_OUT)}})
        attend.addView(ar);root.addView(attend)
        val communication=UiKit.card(this,p,10);communication.addView(UiKit.sectionLabel(this,p,"التواصل والاتصال"));communication.addView(connectionSummary)
        communication.addView(UiKit.button(this,p,"مركز الرسائل",false).apply{setOnClickListener{startActivity(Intent(this@MainActivity,EmployeeMessages1975Activity::class.java))}})
        communication.addView(UiKit.button(this,p,"إدارة الاتصال",false).apply{setOnClickListener{showEmployeeConnectionControl1977()}})
        root.addView(communication)
        val auto=UiKit.card(this,p,9);presenceSwitch=Switch(this).apply{text="الظهور التلقائي لجهاز المحل";textSize=14f;setTextColor(p.text);isChecked=identity.autoPresence;setOnCheckedChangeListener{_,checked->identity.autoPresence=checked;if(checked)startPresence()else{advertiser.stop();networkPresence.stop();startBackgroundPresence()}}};auto.addView(presenceSwitch);root.addView(auto)
        root.addView(UiKit.card(this,p,7).apply{addView(status)})
        setContentView(ScrollView(this).apply{isFillViewport=true;setBackgroundColor(p.bg);addView(root)})
    }

'''
s=s.replace(anchor,helpers+anchor,1)

old='''        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_attend_pro)
'''
new='''        header.addView(employeeTemplateChip1978().apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.START } })
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_attend_pro)
'''
s=must_replace(s,old,new,'employee main selector')
s=must_replace(s,'        root.addView(header)\n\n        status = TextView(this).apply {\n','        root.addView(header)\n        addEmployeeTabs1978(root)\n\n        status = TextView(this).apply {\n','employee main tabs')

old='''        val header = UiKit.heroCard(this,p).apply { gravity = Gravity.CENTER_HORIZONTAL }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_attend_pro); layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@MainActivity,76),UiKit.dp(this@MainActivity,76)) })
'''
new='''        val header = UiKit.heroCard(this,p).apply { gravity = Gravity.CENTER_HORIZONTAL }
        header.addView(TextView(this).apply { text="⋮";textSize=28f;gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE);contentDescription="القائمة";layoutParams=LinearLayout.LayoutParams(UiKit.dp(this@MainActivity,48),UiKit.dp(this@MainActivity,44)).apply{gravity=Gravity.END};setOnClickListener{showEmployeeMainMenu1976()} })
        header.addView(employeeTemplateChip1978().apply { layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{gravity=Gravity.START} })
        header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_attend_pro); layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@MainActivity,76),UiKit.dp(this@MainActivity,76)) })
'''
s=must_replace(s,old,new,'employee classic selector')
pos=s.find('    private fun buildUi() {')
ri=s.find('        root.addView(header)\n',pos)
s=s[:ri]+s[ri:].replace('        root.addView(header)\n','        root.addView(header)\n        addEmployeeTabs1978(root)\n',1)

old='''        showLayeredMenu1977("القائمة", listOf(
            "الإشعارات" to { startActivity(Intent(this, EmployeeMessages1975Activity::class.java)) },
            "دليل مستخدم الموظف" to { showEmployeeUserGuide1976() },
            "الإعدادات" to { showEmployeeSettings1976() }
        ))
'''
new='''        showLayeredMenu1977("القائمة", listOf(
            "الرسائل والإشعارات" to { startActivity(Intent(this, EmployeeMessages1975Activity::class.java)) },
            "نمط الشاشة الرئيسية" to { showEmployeeHomeTemplatePicker1978() },
            "دليل مستخدم الموظف" to { showEmployeeUserGuide1976() },
            "الإعدادات" to { showEmployeeSettings1976() }
        ))
'''
s=must_replace(s,old,new,'employee menu 1978')
write(rel,s)

# ---- Employee messages: new outbound + replies for all ----
rel='employee-app/src/main/java/com/attendpro/employee/EmployeeMessages1975Activity.kt'
s=read(rel)
s=must_replace(s,'        hero.addView(UiKit.subtitle(this,p,"رسائل الخادم والرسائل المباشرة من جهاز المحل").apply { gravity=Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })\n        root.addView(hero)\n','''        hero.addView(UiKit.subtitle(this,p,"مركز التواصل مع إدارة المحل وإدارة النظام").apply { gravity=Gravity.CENTER; setTextColor(android.graphics.Color.argb(225,255,255,255)) })
        root.addView(hero)
        val actions = UiKit.card(this,p,10)
        actions.addView(UiKit.button(this,p,"＋ رسالة جديدة إلى إدارة المحل").apply { setOnClickListener { composeToStore1978() } })
        actions.addView(UiKit.subtitle(this,p,"يمكنك بدء رسالة لإدارة المحل، أو الرد على أي رسالة واردة. الرد على رسالة إدارة النظام يعود إلى إدارة النظام.").apply { gravity=Gravity.CENTER })
        root.addView(actions)
''','employee msg actions')
s=must_replace(s,'                    if (!local) card.addView(UiKit.button(this,p,"رد",false).apply { setOnClickListener { reply(m) } }) else card.addView(UiKit.subtitle(this,p,"الرد على الرسائل المباشرة يُرسل عبر الخادم عند توفر الإنترنت."))\n','''                    card.addView(UiKit.button(this,p,"رد على الرسالة",false).apply { setOnClickListener { reply(m) } })
                    if (local) card.addView(UiKit.subtitle(this,p,"وصلت هذه الرسالة مباشرة بدون إنترنت. إرسال الرد يحتاج اتصالًا بالخادم حاليًا."))
''','reply all')
anchor='    private fun reply(parent: CentralServerClient.Message1975) {\n'
compose=r'''    private fun composeToStore1978() {
        if (!identity.isConfigured) { toast("اربط تطبيق الموظف بالمحل أولًا"); return }
        val field = UiKit.field(this,p,"اكتب رسالتك إلى إدارة المحل").apply { minLines=4 }
        val d = AlertDialog.Builder(this).setTitle("رسالة جديدة إلى إدارة المحل").setView(field).setPositiveButton("إرسال",null).setNegativeButton("إلغاء",null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val message=field.text.toString().trim(); if(message.isBlank()){field.error="اكتب الرسالة";return@setOnClickListener}
                d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
                Thread {
                    val r=CentralServerClient.replyEmployeeMessage(identity.serverUrl,identity.trustedStoreId,identity.employeeId,identity.pairingSecret,identity.installationId,"",message)
                    runOnUiThread { r.onSuccess { toast("تم إرسال الرسالة إلى إدارة المحل");d.dismiss();load() }.onFailure { toast("تعذر الإرسال الآن. تحقق من الإنترنت ثم أعد المحاولة");d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true } }
                }.start()
            }
        };d.show()
    }

'''
s=s.replace(anchor,compose+anchor,1)
s=s.replace('val d = AlertDialog.Builder(this).setTitle("رد على الرسالة").setView(field)', 'val target = if (parent.senderType == "SYSTEM_OWNER") "إدارة النظام" else "إدارة المحل"\n        val d = AlertDialog.Builder(this).setTitle("رد إلى $target").setView(field)',1)
write(rel,s)

print('ATTEND-PRO 1.9.78 semi-final home UI patch applied')
