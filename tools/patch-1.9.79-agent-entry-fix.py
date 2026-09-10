from pathlib import Path

P = Path('buildsrc/store-app/src/main/java/com/attendpro/store/SystemSettingsActivity.kt')
if not P.exists():
    raise SystemExit(f'missing {P}')
s = P.read_text(encoding='utf-8')

old = '''    private fun showOwnerOnlyEntry1978() {
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
new = '''    private fun showOwnerOnlyEntry1978() {
        navigationScreen1977 = "OWNER_GATE"
        val root = baseRoot("منطقة إدارة النظام", "وصول إداري محمي — مالك النظام أو الوكيل المعتمد")

        val ownerGate = UiKit.card(this, p, 12)
        ownerGate.addView(UiKit.sectionLabel(this, p, "إدارة النظام"))
        ownerGate.addView(UiKit.title(this, p, "مالك النظام", 19f))
        ownerGate.addView(UiKit.subtitle(this, p, "إدارة الوكلاء والمشتركين والصلاحيات والتفعيل المركزي. الدخول يتطلب رمز مالك النظام."))
        ownerGate.addView(UiKit.button(this, p, "دخول مالك النظام").apply { setOnClickListener { ownerLogin() } })
        root.addView(ownerGate)

        val agentGate = UiKit.card(this, p, 10)
        agentGate.addView(UiKit.sectionLabel(this, p, "بوابة الوكيل"))
        agentGate.addView(UiKit.title(this, p, "الوكيل المعتمد", 18f))
        agentGate.addView(UiKit.subtitle(this, p, "دخول منفصل بالرمز الممنوح من مالك النظام. تظهر للوكيل فقط الصلاحيات والعملاء المسموحون له."))
        agentGate.addView(UiKit.button(this, p, "دخول الوكيل", false).apply { setOnClickListener { agentLogin() } })
        root.addView(agentGate)

        val note = UiKit.card(this, p, 8)
        note.addView(UiKit.subtitle(this, p, "هذه المنطقة مخفية عن الاستخدام اليومي للمحل، ولا تمنح أي صلاحية قبل التحقق من رمز المالك أو الوكيل."))
        root.addView(note)
        root.addView(UiKit.button(this, p, "رجوع", false).apply { setOnClickListener { finish() } })
        display(root)
    }
'''
if old not in s:
    raise SystemExit('owner-only gate anchor missing; refusing unsafe patch')
s = s.replace(old, new, 1)
P.write_text(s, encoding='utf-8')

# Ensure owner central management still exposes the Agents section explicitly.
M = Path('buildsrc/store-app/src/main/java/com/attendpro/store/SystemManagement1971Activity.kt')
if not M.exists():
    raise SystemExit(f'missing {M}')
m = M.read_text(encoding='utf-8')
required = [
    'if (mode == "OWNER") sections += Triple("الوكلاء", "AGENTS", "👥")',
    '"👥 الوكلاء", { showHome("AGENTS") }',
    '"إدارة الوكلاء", { showAgents() }',
    '"＋ إضافة وكيل", { createAgentDialog() }',
]
for marker in required:
    if marker not in m:
        raise SystemExit(f'missing owner agent management marker: {marker}')

print('ATTEND-PRO 1.9.79 agent entry hotfix applied')
print('Agent login restored inside protected system area; owner agents management preserved')
