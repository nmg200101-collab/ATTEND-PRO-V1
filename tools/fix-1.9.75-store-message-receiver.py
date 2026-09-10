from pathlib import Path

p = Path('buildsrc/store-app/src/main/java/com/attendpro/store/StoreMessagePoll1975.kt')
s = p.read_text(encoding='utf-8')
old = 'override fun onReceive(context: Context) {'
new = 'override fun onReceive(context: Context, intent: Intent) {'
if old in s:
    s = s.replace(old, new, 1)
elif new not in s:
    raise SystemExit('StoreMessagePoll1975 onReceive signature not found')
p.write_text(s, encoding='utf-8')
print('Fixed StoreMessagePoll1975 BroadcastReceiver signature')
