#!/usr/bin/env python3
from pathlib import Path
import re

ROOTS = [
    Path("buildsrc/store-app/src/main/java"),
    Path("buildsrc/employee-app/src/main/java"),
]
EXEMPT_FILES = {
    "PairingDiscovery.kt", "PairingBeacon.kt",
}
AR = re.compile(r"[\u0600-\u06ff]")
STRING = re.compile(r'"(?:\\.|[^"\\])*"')

rows = []
for root in ROOTS:
    for path in sorted(root.rglob("*.kt")):
        if path.name in EXEMPT_FILES:
            continue
        text = path.read_text(encoding="utf-8")
        for lineno, line in enumerate(text.splitlines(), 1):
            # Lines already routed through the bilingual helper are intentionally Arabic+English.
            # Do not count the Arabic source argument as an untranslated hard-coded string.
            if "t(" in line or "AppLanguage.text(" in line:
                continue
            for m in STRING.finditer(line):
                literal = m.group(0)
                if AR.search(literal):
                    rows.append((str(path), lineno, literal[:220]))

out = Path("translation-audit-rc2.txt")
with out.open("w", encoding="utf-8") as f:
    f.write("ATTEND PRO RC2 hard-coded Arabic string audit\n")
    f.write("Protected pairing files intentionally excluded from mutation audit.\n")
    f.write(f"Occurrences: {len(rows)}\n\n")
    for path, lineno, literal in rows:
        f.write(f"{path}:{lineno}: {literal}\n")

print(f"translation audit occurrences={len(rows)}")
print(out)
