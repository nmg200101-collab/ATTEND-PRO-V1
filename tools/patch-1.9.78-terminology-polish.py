from pathlib import Path

root = Path('buildsrc')
files = [
    root / 'store-app/src/main/java/com/attendpro/store/MainActivity.kt',
    root / 'employee-app/src/main/java/com/attendpro/employee/MainActivity.kt',
    root / 'employee-app/src/main/java/com/attendpro/employee/EmployeeMessages1975Activity.kt',
]
replacements = {
    'إدارة صاحب المحل / المدير': 'إدارة المحل',
    'التي سمح بها صاحب العمل': 'المعتمدة من إدارة المحل',
    'التي حددها مالك المحل': 'التي اعتمدتها إدارة المحل',
    'اختصارات المدير': 'اختصارات إدارة المحل',
    'إدارة صاحب المحل': 'إدارة المحل',
}
for path in files:
    text = path.read_text(encoding='utf-8')
    for old, new in replacements.items():
        text = text.replace(old, new)
    path.write_text(text, encoding='utf-8')
print('ATTEND-PRO 1.9.78 terminology polish applied')
