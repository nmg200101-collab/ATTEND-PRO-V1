from pathlib import Path

p = Path('buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt')
if not p.exists():
    raise SystemExit('MainActivity.kt not found')

s = p.read_text(encoding='utf-8')
bad = '''info("تعذر التسجيل المباشر", (registration.exceptionOrNull()?.message ?: "خطأ") + "

تم الاحتفاظ بطلب التفعيل ويمكن لإدارة النظام اعتماده يدويًا.")'''
good = 'info("تعذر التسجيل المباشر", (registration.exceptionOrNull()?.message ?: "خطأ") + "\\n\\nتم الاحتفاظ بطلب التفعيل ويمكن لإدارة النظام اعتماده يدويًا.")'

if bad in s:
    s = s.replace(bad, good, 1)
elif good not in s:
    raise SystemExit('Expected self-registration fallback string not found')

p.write_text(s, encoding='utf-8')
print('Fixed 1.9.71 Kotlin self-registration fallback string escaping')
