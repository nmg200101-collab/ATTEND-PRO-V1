from pathlib import Path
import hashlib, json, re, sys

STATE = Path('/tmp/attend1976-pairing-guard.json')
FILES = [
    'buildsrc/employee-app/src/main/java/com/attendpro/employee/PairingDiscovery.kt',
    'buildsrc/employee-app/src/main/java/com/attendpro/employee/BleDirectLinkServer.kt',
    'buildsrc/employee-app/src/main/java/com/attendpro/employee/PresenceService.kt',
    'buildsrc/store-app/src/main/java/com/attendpro/store/PairingBeacon.kt',
    'buildsrc/store-app/src/main/java/com/attendpro/store/BleDirectLinkClient.kt',
    'buildsrc/core/src/main/java/com/attendpro/core/CentralServerClient.kt',
]
FUNCTIONS = {
    'buildsrc/employee-app/src/main/java/com/attendpro/employee/MainActivity.kt': [
        'scanProvision','showManualProvision','showPairingCenter','acceptProvision','startPresence','startBackgroundPresence'
    ],
    'buildsrc/store-app/src/main/java/com/attendpro/store/MainActivity.kt': [
        'directBleConfig','handleBlePayload','handleLanConfirm','isLanConnected','isAuthenticatedPresenceConnected',
        'isEmployeeActuallyConnected','testEmployeePhoneLink','showProvisionQr','showConnectionCenter'
    ],
}

def sha_text(text: str) -> str:
    return hashlib.sha256(text.encode('utf-8')).hexdigest()

def sha_file(path: str) -> str:
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def extract_function(path: str, name: str) -> str:
    s = Path(path).read_text(encoding='utf-8')
    m = re.search(r'\bfun\s+' + re.escape(name) + r'\s*\(', s)
    if not m:
        raise SystemExit(f'missing protected function {name} in {path}')
    start = s.rfind('\n', 0, m.start()) + 1
    brace = s.find('{', m.end())
    if brace < 0:
        raise SystemExit(f'missing body for protected function {name} in {path}')
    depth = 0
    i = brace
    mode = 'code'
    while i < len(s):
        if mode == 'code':
            if s.startswith('//', i): mode = 'line_comment'; i += 2; continue
            if s.startswith('/*', i): mode = 'block_comment'; i += 2; continue
            if s.startswith('"""', i): mode = 'triple'; i += 3; continue
            c = s[i]
            if c == '"': mode = 'string'; i += 1; continue
            if c == "'": mode = 'char'; i += 1; continue
            if c == '{': depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0:
                    return s[start:i+1]
            i += 1
        elif mode == 'line_comment':
            if s[i] == '\n': mode = 'code'
            i += 1
        elif mode == 'block_comment':
            if s.startswith('*/', i): mode = 'code'; i += 2
            else: i += 1
        elif mode == 'triple':
            if s.startswith('"""', i): mode = 'code'; i += 3
            else: i += 1
        else:
            quote = '"' if mode == 'string' else "'"
            if s[i] == '\\': i += 2; continue
            if s[i] == quote: mode = 'code'
            i += 1
    raise SystemExit(f'unbalanced protected function {name} in {path}')

def snapshot():
    state = {
        'files': {p: sha_file(p) for p in FILES},
        'functions': {p: {n: sha_text(extract_function(p,n)) for n in names} for p,names in FUNCTIONS.items()},
    }
    STATE.write_text(json.dumps(state, sort_keys=True, indent=2), encoding='utf-8')
    print(f'Pairing guard snapshot saved: {len(FILES)} files, {sum(map(len,FUNCTIONS.values()))} functions')

def verify():
    if not STATE.exists(): raise SystemExit('pairing guard snapshot missing')
    state = json.loads(STATE.read_text(encoding='utf-8'))
    for p, expected in state['files'].items():
        actual = sha_file(p)
        if actual != expected: raise SystemExit(f'PAIRING FILE CHANGED: {p}')
    for p, funcs in state['functions'].items():
        for name, expected in funcs.items():
            actual = sha_text(extract_function(p,name))
            if actual != expected: raise SystemExit(f'PAIRING FUNCTION CHANGED: {p}:{name}')
    print('Protected BLE/QR/GATT/presence implementation unchanged')

if len(sys.argv) != 2 or sys.argv[1] not in {'snapshot','verify'}:
    raise SystemExit('usage: guard-1.9.76-pairing.py snapshot|verify')
(snapshot if sys.argv[1] == 'snapshot' else verify)()
