from pathlib import Path
import shutil

ROOT = Path('buildsrc')
STORE = ROOT / 'store-app/src/main/java/com/attendpro/store'
MANIFEST = ROOT / 'store-app/src/main/AndroidManifest.xml'
SOURCE = Path('.source_patches/1.9.71/SystemManagement1971Activity.kt')
TARGET = STORE / 'SystemManagement1971Activity.kt'
SYSTEM = STORE / 'SystemSettingsActivity.kt'

for required in (SOURCE, SYSTEM, MANIFEST):
    if not required.exists():
        raise SystemExit(f'missing required file: {required}')

shutil.copyfile(SOURCE, TARGET)

text = SYSTEM.read_text(encoding='utf-8')

agent_anchor = 'agent.addView(UiKit.button(this, p, "دخول بوابة الوكيل", false).apply { setOnClickListener { agentLogin() } })'
agent_button = '''agent.addView(UiKit.button(this, p, "بوابة الوكيل المركزية 1.9.71", false).apply {
            setOnClickListener {
                startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "AGENT"))
            }
        })'''
if 'بوابة الوكيل المركزية 1.9.71' not in text:
    if agent_anchor not in text:
        raise SystemExit('agent gateway anchor not found; refusing unsafe patch')
    text = text.replace(agent_anchor, agent_anchor + '\n        ' + agent_button, 1)

owner_anchor = 'network.addView(UiKit.button(this, p, "إدارة الوكلاء").apply { setOnClickListener { showAgents() } })'
owner_button = '''network.addView(UiKit.button(this, p, "إدارة النظام المركزية 1.9.71").apply {
            setOnClickListener {
                startActivity(Intent(this@SystemSettingsActivity, SystemManagement1971Activity::class.java).putExtra("mode", "OWNER"))
            }
        })'''
if 'إدارة النظام المركزية 1.9.71' not in text:
    if owner_anchor not in text:
        raise SystemExit('owner dashboard anchor not found; refusing unsafe patch')
    text = text.replace(owner_anchor, owner_anchor + '\n        ' + owner_button, 1)

SYSTEM.write_text(text, encoding='utf-8')

manifest = MANIFEST.read_text(encoding='utf-8')
manifest_anchor = '<activity android:name=".SystemSettingsActivity" android:screenOrientation="portrait" android:exported="false" />'
manifest_entry = '<activity android:name=".SystemManagement1971Activity" android:screenOrientation="portrait" android:exported="false" />'
if manifest_entry not in manifest:
    if manifest_anchor not in manifest:
        raise SystemExit('manifest activity anchor not found; refusing unsafe patch')
    manifest = manifest.replace(manifest_anchor, manifest_anchor + '\n        ' + manifest_entry, 1)
MANIFEST.write_text(manifest, encoding='utf-8')

# Refuse accidental changes outside the management surface.
if not TARGET.exists() or 'class SystemManagement1971Activity' not in TARGET.read_text(encoding='utf-8'):
    raise SystemExit('management activity copy failed')

print('ATTEND-PRO 1.9.71 Android management patch applied')
print(f'created: {TARGET}')
print(f'patched: {SYSTEM}')
print(f'patched: {MANIFEST}')
