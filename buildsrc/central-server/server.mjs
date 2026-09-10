import http from 'node:http';
import { readFileSync, writeFileSync, renameSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { randomBytes, randomUUID, createHash, scryptSync, timingSafeEqual, createCipheriv, createDecipheriv, createPublicKey, verify as verifySignature } from 'node:crypto';

const PORT = Number(process.env.PORT || 8080);
const DB_FILE = resolve(process.env.ATTEND_PRO_DB_FILE || './data/central-data.json');
const OWNER_KEY = String(process.env.ATTEND_PRO_OWNER_API_KEY || '');
const DATA_KEY_B64 = String(process.env.ATTEND_PRO_DATA_KEY || '');
const OFFLINE_LEASE_HOURS = Math.min(168, Math.max(1, Number(process.env.ATTEND_PRO_OFFLINE_LEASE_HOURS || 72)));
if (OWNER_KEY.length < 32) throw new Error('ATTEND_PRO_OWNER_API_KEY must be at least 32 characters');
const DATA_KEY = Buffer.from(DATA_KEY_B64, 'base64');
if (DATA_KEY.length !== 32) throw new Error('ATTEND_PRO_DATA_KEY must be a base64 encoded 32-byte key');
mkdirSync(dirname(DB_FILE), { recursive: true });

const emptyDb = () => ({ version: 1, activationRequests: [], stores: [], events: [], reports: [] });
function loadDb() {
  if (!existsSync(DB_FILE)) return emptyDb();
  const parsed = JSON.parse(readFileSync(DB_FILE, 'utf8'));
  if (!parsed || typeof parsed !== 'object') throw new Error('ATTEND PRO database is invalid');
  for (const key of ['activationRequests','stores','events','reports']) if (!Array.isArray(parsed[key])) parsed[key] = [];
  return parsed;
}
let db = loadDb();
function saveDb() { const tmp = DB_FILE + '.tmp'; writeFileSync(tmp, JSON.stringify(db, null, 2), { mode: 0o600 }); renameSync(tmp, DB_FILE); }

function sha256(v) { return createHash('sha256').update(String(v)).digest(); }
function b64u(buf) { return Buffer.from(buf).toString('base64url'); }
function safeEqualText(a, b) { const aa = sha256(a), bb = sha256(b); return timingSafeEqual(aa, bb); }
function hashSecret(secret) { const salt = randomBytes(16); const out = scryptSync(String(secret), salt, 32); return `scrypt$${b64u(salt)}$${b64u(out)}`; }
function verifySecret(stored, secret) { try { const [v,s,h] = stored.split('$'); if (v !== 'scrypt') return false; const out=scryptSync(String(secret),Buffer.from(s,'base64url'),32); return timingSafeEqual(out,Buffer.from(h,'base64url')); } catch { return false; } }
function pinHash(pin) { return 'sha256:' + b64u(sha256(String(pin).trim())); }
function encryptAtRest(text) { const iv=randomBytes(12); const c=createCipheriv('aes-256-gcm',DATA_KEY,iv); const enc=Buffer.concat([c.update(String(text),'utf8'),c.final()]); return `v1.${b64u(iv)}.${b64u(c.getAuthTag())}.${b64u(enc)}`; }
function decryptAtRest(blob) { const [v,ivb,tagb,encb]=String(blob).split('.'); if(v!=='v1') throw new Error('bad encrypted token'); const d=createDecipheriv('aes-256-gcm',DATA_KEY,Buffer.from(ivb,'base64url')); d.setAuthTag(Buffer.from(tagb,'base64url')); return Buffer.concat([d.update(Buffer.from(encb,'base64url')),d.final()]).toString('utf8'); }
function iso(ms) { return ms ? new Date(ms).toISOString() : ''; }

const rate = new Map();
function rateLimit(ip, key, max=60, windowMs=60_000) { const now=Date.now(), id=`${ip}:${key}`, x=rate.get(id)||{start:now,count:0}; if(now-x.start>windowMs){x.start=now;x.count=0} x.count++; rate.set(id,x); return x.count<=max; }
function json(res, code, obj) { const body=JSON.stringify(obj); res.writeHead(code,{ 'content-type':'application/json; charset=utf-8','content-length':Buffer.byteLength(body),'cache-control':'no-store','x-content-type-options':'nosniff','referrer-policy':'no-referrer','content-security-policy':"default-src 'none'" }); res.end(body); }
async function readJson(req) { return await new Promise((resolveBody,reject)=>{ let data='', size=0; req.on('data',c=>{size+=c.length;if(size>1_000_000){reject(new Error('request too large'));req.destroy();return}data+=c}); req.on('end',()=>{req._rawBody=data;try{resolveBody(data?JSON.parse(data):{})}catch{reject(new Error('invalid json'))}}); req.on('error',reject); }); }
function bearer(req) { const h=String(req.headers.authorization||''); return h.startsWith('Bearer ')?h.slice(7).trim():''; }
function requireOwner(req) { const t=bearer(req); return t.length>=32 && safeEqualText(t,OWNER_KEY); }
function storeByToken(token) { if(!token) return null; const h=b64u(sha256(token)); return db.stores.find(x=>x.tokenHash===h) || null; }
function activeStoreByToken(token) { const s=storeByToken(token); if(!s || s.status!=='ACTIVE' || s.expiresAt<=Date.now()) return null; return s; }
function leaseUntilFor(store) { return Math.min(Number(store?.expiresAt||0), Date.now() + OFFLINE_LEASE_HOURS * 3600000); }
function receiverAuth(receiverId, secret) { for (const s of db.stores) { const r=(s.receivers||[]).find(x=>x.receiverId===receiverId && x.active); if(r && verifySecret(r.secretHash,secret)) return {store:s,receiver:r}; } return null; }

function validDevicePublicKey(value) {
  try { const key=createPublicKey({key:Buffer.from(String(value),'base64url'),format:'der',type:'spki'}); return key.asymmetricKeyType==='ec'; } catch { return false; }
}
function verifyDeviceProof(req, publicKeyB64, expectedStoreId, path, rawBody, subject) {
  try {
    const storeId=String(req.headers['x-ap-store']||''), ts=String(req.headers['x-ap-time']||''), nonce=String(req.headers['x-ap-nonce']||''), bodyHash=String(req.headers['x-ap-body-sha256']||''), sig=String(req.headers['x-ap-signature']||'');
    if(storeId!==expectedStoreId || !nonce || nonce.length<12 || !sig) return false;
    const time=Number(ts); if(!Number.isFinite(time) || Math.abs(Date.now()-time)>5*60_000) return false;
    const actualHash=createHash('sha256').update(String(rawBody||'')).digest('hex'); if(actualHash!==bodyHash) return false;
    subject.usedNonces=(subject.usedNonces||[]).filter(x=>Date.now()-x.time<10*60_000);
    if(subject.usedNonces.some(x=>x.nonce===nonce)) return false;
    const canonical=`${req.method}
${path}
${ts}
${nonce}
${bodyHash}`;
    const key=createPublicKey({key:Buffer.from(publicKeyB64,'base64url'),format:'der',type:'spki'});
    const ok=verifySignature('sha256',Buffer.from(canonical,'utf8'),key,Buffer.from(sig,'base64url'));
    if(!ok) return false;
    subject.usedNonces.push({nonce,time:Date.now()}); subject.usedNonces=subject.usedNonces.slice(-200); saveDb();
    return true;
  } catch { return false; }
}

async function handler(req,res) {
  const ip=String(req.headers['x-forwarded-for']||req.socket.remoteAddress||'').split(',')[0].trim();
  const url=new URL(req.url,'http://localhost'); const path=url.pathname;
  if(path==='/health' && req.method==='GET') return json(res,200,{ok:true,service:'ATTEND-PRO-Central',time:new Date().toISOString()});
  if(!rateLimit(ip,path, path.startsWith('/api/v1/activation')?20:120)) return json(res,429,{error:'طلبات كثيرة، حاول لاحقًا'});
  try {
    if(path==='/api/v1/activation/request' && req.method==='POST') {
      const b=await readJson(req); const storeId=String(b.storeId||'').trim(); const devicePublicKey=String(b.devicePublicKey||'').trim(); if(storeId.length<8) return json(res,400,{error:'معرف المحل غير صالح'}); if(!validDevicePublicKey(devicePublicKey)) return json(res,400,{error:'هوية الجهاز غير صالحة'});
      const pollSecret=b64u(randomBytes(32)), requestId='ACT-'+randomUUID();
      db.activationRequests.unshift({requestId,storeId,storeName:String(b.storeName||'محل').slice(0,120),branchId:String(b.branchId||'MAIN').slice(0,64),devicePublicKey,pollSecretHash:hashSecret(pollSecret),status:'PENDING',createdAt:Date.now(),approvedAt:0,licenseId:'',encryptedAccessToken:'',expiresAt:0,maxEmployees:10,reason:'',usedNonces:[]});
      db.activationRequests=db.activationRequests.slice(0,10000); saveDb(); return json(res,200,{requestId,pollSecret,status:'PENDING'});
    }
    if(path==='/api/v1/activation/status' && req.method==='POST') {
      const b=await readJson(req); const a=db.activationRequests.find(x=>x.requestId===String(b.requestId||'')); if(!a || !verifySecret(a.pollSecretHash,String(b.pollSecret||''))) return json(res,403,{error:'طلب التفعيل أو سر الفحص غير صحيح'});
      if(!verifyDeviceProof(req,a.devicePublicKey,a.storeId,path,req._rawBody||'',a)) return json(res,403,{error:'تعذر التحقق من هوية جهاز المحل'});
      if(a.status==='APPROVED') { const store=db.stores.find(x=>x.storeId===a.storeId); return json(res,200,{status:'APPROVED',licenseId:a.licenseId,accessToken:decryptAtRest(a.encryptedAccessToken),expiresAt:a.expiresAt,maxEmployees:a.maxEmployees,leaseUntil:leaseUntilFor(store),serverTime:Date.now()}); }
      return json(res,200,{status:a.status,reason:a.reason||'',serverTime:Date.now()});
    }
    if(path==='/api/v1/admin/activation/pending' && req.method==='GET') {
      if(!requireOwner(req)) return json(res,403,{error:'غير مصرح'});
      const requests=db.activationRequests.filter(x=>x.status==='PENDING').slice(0,200).map(x=>({requestId:x.requestId,storeId:x.storeId,storeName:x.storeName,branchId:x.branchId,createdAt:x.createdAt})); return json(res,200,{requests});
    }
    if(path==='/api/v1/admin/activation/approve' && req.method==='POST') {
      if(!requireOwner(req)) return json(res,403,{error:'غير مصرح'}); const b=await readJson(req); const a=db.activationRequests.find(x=>x.requestId===String(b.requestId||'')); if(!a) return json(res,404,{error:'الطلب غير موجود'});
      const days=Math.min(3650,Math.max(1,Number(b.days)||365)), maxEmployees=Math.min(10000,Math.max(1,Number(b.maxEmployees)||100)); const expiresAt=Date.now()+days*86400000; const token=b64u(randomBytes(32)); const licenseId='LIC-'+randomUUID();
      let store=db.stores.find(x=>x.storeId===a.storeId); if(!store){store={storeId:a.storeId,storeName:a.storeName,branchId:a.branchId,devicePublicKey:a.devicePublicKey,licenseId,tokenHash:b64u(sha256(token)),status:'ACTIVE',createdAt:Date.now(),expiresAt,maxEmployees,lastSeenAt:0,receivers:[],usedNonces:[]};db.stores.push(store)} else {Object.assign(store,{storeName:a.storeName,branchId:a.branchId,devicePublicKey:a.devicePublicKey,licenseId,tokenHash:b64u(sha256(token)),status:'ACTIVE',expiresAt,maxEmployees,usedNonces:[]})}
      Object.assign(a,{status:'APPROVED',approvedAt:Date.now(),licenseId,encryptedAccessToken:encryptAtRest(token),expiresAt,maxEmployees}); saveDb(); return json(res,200,{ok:true,licenseId,expiresAt,maxEmployees});
    }
    if(path==='/api/v1/admin/stores' && req.method==='GET') {
      if(!requireOwner(req)) return json(res,403,{error:'غير مصرح'});
      return json(res,200,{stores:db.stores.map(s=>({storeId:s.storeId,storeName:s.storeName,branchId:s.branchId,status:s.status,expiresAt:s.expiresAt,maxEmployees:s.maxEmployees,lastSeenAt:s.lastSeenAt||0})).sort((a,b)=>String(a.storeName).localeCompare(String(b.storeName),'ar'))});
    }
    if(path==='/api/v1/admin/stores/status' && req.method==='POST') {
      if(!requireOwner(req)) return json(res,403,{error:'غير مصرح'}); const b=await readJson(req); const s=db.stores.find(x=>x.storeId===String(b.storeId||'')); if(!s) return json(res,404,{error:'المحل غير موجود'}); s.status=String(b.status||'SUSPENDED')==='ACTIVE'?'ACTIVE':'SUSPENDED'; saveDb(); return json(res,200,{ok:true,status:s.status});
    }

    if(path==='/api/v1/admin/stores/renew' && req.method==='POST') {
      if(!requireOwner(req)) return json(res,403,{error:'غير مصرح'}); const b=await readJson(req); const s=db.stores.find(x=>x.storeId===String(b.storeId||'')); if(!s) return json(res,404,{error:'المحل غير موجود'});
      const days=Math.min(3650,Math.max(1,Number(b.days)||365)), maxEmployees=Math.min(10000,Math.max(1,Number(b.maxEmployees)||s.maxEmployees||100));
      s.expiresAt=Date.now()+days*86400000; s.maxEmployees=maxEmployees; s.status='ACTIVE'; saveDb(); return json(res,200,{ok:true,status:s.status,expiresAt:s.expiresAt,maxEmployees:s.maxEmployees});
    }

    if(path==='/api/v1/store/validate' && req.method==='GET') {
      const store=storeByToken(bearer(req));
      if(!store) return json(res,401,{error:'بيانات تفعيل المحل غير معروفة'});
      if(!verifyDeviceProof(req,store.devicePublicKey,store.storeId,path,'',store)) return json(res,403,{error:'توقيع الجهاز غير صالح'});
      const now=Date.now();
      const expired=Number(store.expiresAt||0)<=now;
      const status=expired?'EXPIRED':String(store.status||'SUSPENDED');
      if(status==='ACTIVE') store.lastSeenAt=now;
      saveDb();
      return json(res,200,{status,expiresAt:store.expiresAt,maxEmployees:store.maxEmployees,leaseUntil:status==='ACTIVE'?leaseUntilFor(store):0,serverTime:now,reason:status==='ACTIVE'?'':status==='EXPIRED'?'انتهت مدة تفعيل المحل':'تم تعليق المحل من صاحب النظام'});
    }

    // ATTEND-PRO 1.9.66 — employee phone link, GPS recognition and attendance bridge.
    // GPS is presence evidence only; identity verification remains a separate attendance step.
    const findEmployeeLink = (storeId, employeeId) => {
      const st=db.stores.find(x=>x.storeId===storeId); if(!st) return null;
      st.employeeLinks=Array.isArray(st.employeeLinks)?st.employeeLinks:[];
      return st.employeeLinks.find(x=>x.employeeId===employeeId)||null;
    };
    const employeeAuth = b => {
      const st=db.stores.find(x=>x.storeId===String(b.storeId||'')); if(!st || st.status!=='ACTIVE' || Number(st.expiresAt||0)<=Date.now()) return null;
      const e=findEmployeeLink(st.storeId,String(b.employeeId||'')); if(!e || !verifySecret(e.pairingSecretHash,String(b.pairingSecret||''))) return null;
      if(e.installationId && e.installationId!==String(b.installationId||'')) return null;
      return {store:st,employee:e};
    };
    const linkJson = e => ({linked:true,linkedAt:e.linkedAt||0,lastSeenAt:e.lastSeenAt||0,gpsState:e.gpsState||'UNKNOWN',gpsDistanceMeters:Number(e.gpsDistanceMeters??-1),gpsAccuracyMeters:Number(e.gpsAccuracyMeters??-1),gpsSeenAt:e.gpsSeenAt||0});
    const challengeJson = c => ({challengeId:c.challengeId,employeeId:c.employeeId,requiredMethod:c.requiredMethod,status:c.status,createdAt:c.createdAt,expiresAt:c.expiresAt,verifiedAt:c.verifiedAt||0,evidence:c.evidence||'',action:c.action||''});

    if(path==='/api/v1/employee/link' && req.method==='POST') {
      const b=await readJson(req); const st=db.stores.find(x=>x.storeId===String(b.storeId||''));
      if(!st || st.status!=='ACTIVE' || Number(st.expiresAt||0)<=Date.now()) return json(res,403,{error:'المحل غير مفعل'});
      const employeeId=String(b.employeeId||'').trim(), secret=String(b.pairingSecret||''), installationId=String(b.installationId||'').trim();
      if(employeeId.length<1 || secret.length<12 || installationId.length<4) return json(res,400,{error:'بيانات ربط الموظف غير مكتملة'});
      st.employeeLinks=Array.isArray(st.employeeLinks)?st.employeeLinks:[]; let e=st.employeeLinks.find(x=>x.employeeId===employeeId);
      if(e && !verifySecret(e.pairingSecretHash,secret)) return json(res,403,{error:'سر ربط الموظف غير صحيح'});
      const now=Date.now(); if(!e){e={employeeId,employeeName:String(b.employeeName||employeeId).slice(0,120),branchId:String(b.branchId||'MAIN').slice(0,64),pairingSecretHash:hashSecret(secret),installationId,allowedMethods:Array.isArray(b.allowedMethods)?b.allowedMethods.slice(0,20):[],linkedAt:now,lastSeenAt:now,gpsState:'UNKNOWN',gpsDistanceMeters:-1,gpsAccuracyMeters:-1,gpsSeenAt:0};st.employeeLinks.push(e)}
      else {e.employeeName=String(b.employeeName||e.employeeName).slice(0,120);e.branchId=String(b.branchId||e.branchId).slice(0,64);e.installationId=installationId;e.allowedMethods=Array.isArray(b.allowedMethods)?b.allowedMethods.slice(0,20):e.allowedMethods;e.lastSeenAt=now}
      saveDb(); return json(res,200,linkJson(e));
    }
    if(path==='/api/v1/employee/geo-observation' && req.method==='POST') {
      const b=await readJson(req), a=employeeAuth(b); if(!a) return json(res,403,{error:'ربط هاتف الموظف غير صالح'});
      const state=String(b.state||'UNKNOWN').toUpperCase(); if(!['INSIDE','NEAR','OUTSIDE','UNKNOWN'].includes(state)) return json(res,400,{error:'حالة GPS غير صالحة'});
      const observed=Math.min(Date.now()+60000,Math.max(Date.now()-10*60_000,Number(b.observedAt)||Date.now()));
      Object.assign(a.employee,{gpsState:state,gpsDistanceMeters:Math.max(-1,Math.min(100000,Number(b.distanceMeters)||0)),gpsAccuracyMeters:Math.max(-1,Math.min(10000,Number(b.accuracyMeters)||0)),gpsSeenAt:observed,lastSeenAt:Date.now()}); saveDb(); return json(res,200,{ok:true});
    }
    if(path==='/api/v1/store/employee-link' && req.method==='POST') {
      const st=activeStoreByToken(bearer(req)); if(!st) return json(res,401,{error:'تفعيل المحل غير صالح'}); const b=await readJson(req);
      if(!verifyDeviceProof(req,st.devicePublicKey,st.storeId,path,req._rawBody||'',st)) return json(res,403,{error:'توقيع الجهاز غير صالح'});
      const e=findEmployeeLink(st.storeId,String(b.employeeId||'')); return json(res,200,e?linkJson(e):{linked:false,linkedAt:0,lastSeenAt:0,gpsState:'UNKNOWN',gpsDistanceMeters:-1,gpsAccuracyMeters:-1,gpsSeenAt:0});
    }
    if(path==='/api/v1/store/presence-challenge' && req.method==='POST') {
      const st=activeStoreByToken(bearer(req)); if(!st) return json(res,401,{error:'تفعيل المحل غير صالح'}); const b=await readJson(req);
      if(!verifyDeviceProof(req,st.devicePublicKey,st.storeId,path,req._rawBody||'',st)) return json(res,403,{error:'توقيع الجهاز غير صالح'});
      const e=findEmployeeLink(st.storeId,String(b.employeeId||'')); if(!e) return json(res,404,{error:'هاتف الموظف غير مرتبط'});
      st.presenceChallenges=Array.isArray(st.presenceChallenges)?st.presenceChallenges:[]; const now=Date.now();
      const c={challengeId:'CH-'+randomUUID(),employeeId:e.employeeId,requiredMethod:String(b.requiredMethod||'PHONE_BLE_BIOMETRIC'),action:String(b.action||''),status:'PENDING',createdAt:now,expiresAt:now+2*60_000,verifiedAt:0,evidence:''}; st.presenceChallenges.unshift(c);st.presenceChallenges=st.presenceChallenges.slice(0,1000);saveDb();return json(res,200,challengeJson(c));
    }
    if(path==='/api/v1/store/presence-challenge/status' && req.method==='POST') {
      const st=activeStoreByToken(bearer(req)); if(!st) return json(res,401,{error:'تفعيل المحل غير صالح'}); const b=await readJson(req);
      if(!verifyDeviceProof(req,st.devicePublicKey,st.storeId,path,req._rawBody||'',st)) return json(res,403,{error:'توقيع الجهاز غير صالح'});
      const c=(st.presenceChallenges||[]).find(x=>x.challengeId===String(b.challengeId||''));if(!c)return json(res,404,{error:'الطلب غير موجود'});if(c.status==='PENDING'&&c.expiresAt<Date.now())c.status='EXPIRED';saveDb();return json(res,200,challengeJson(c));
    }
    if(path==='/api/v1/employee/presence-challenge/poll' && req.method==='POST') {
      const b=await readJson(req),a=employeeAuth(b);if(!a)return json(res,403,{error:'ربط هاتف الموظف غير صالح'});a.employee.lastSeenAt=Date.now();
      const c=(a.store.presenceChallenges||[]).find(x=>x.employeeId===a.employee.employeeId&&x.status==='PENDING'&&x.expiresAt>Date.now());saveDb();return c?json(res,200,{available:true,...challengeJson(c)}):json(res,200,{available:false});
    }
    if(path==='/api/v1/employee/presence-challenge/complete' && req.method==='POST') {
      const b=await readJson(req),a=employeeAuth(b);if(!a)return json(res,403,{error:'ربط هاتف الموظف غير صالح'});const c=(a.store.presenceChallenges||[]).find(x=>x.challengeId===String(b.challengeId||'')&&x.employeeId===a.employee.employeeId);if(!c)return json(res,404,{error:'الطلب غير موجود'});
      if(c.expiresAt<Date.now())c.status='EXPIRED';else if(String(b.method||'')!==c.requiredMethod)return json(res,400,{error:'طريقة الإثبات لا تطابق الطريقة المطلوبة'});else{c.status='VERIFIED';c.verifiedAt=Date.now();c.evidence=String(b.evidence||'').slice(0,500)}a.employee.lastSeenAt=Date.now();saveDb();return json(res,200,challengeJson(c));
    }
    if(path==='/api/v1/employee/attendance' && req.method==='POST') {
      const b=await readJson(req),a=employeeAuth(b);if(!a)return json(res,403,{error:'ربط هاتف الموظف غير صالح'});const action=String(b.action||'');if(!['CHECK_IN','CHECK_OUT'].includes(action))return json(res,400,{error:'نوع حركة الحضور غير صالح'});
      const method=String(b.method||'PHONE_BLE_BIOMETRIC').slice(0,64);
      const allowed=Array.isArray(a.employee.allowedMethods)?a.employee.allowedMethods:[];
      if(allowed.length && !allowed.includes(method)) return json(res,403,{error:'طريقة تسجيل الحضور غير مسموحة لهذا الموظف'});
      if(method==='GPS') return json(res,403,{error:'GPS قناة تعرف على وجود الهاتف ولا يجوز استخدامه وحده لإثبات الهوية أو تسجيل الحضور'});
      const ts=Number(b.timestampEpochMillis)||Date.now(); if(Math.abs(Date.now()-ts)>10*60_000) return json(res,400,{error:'وقت حركة الحضور خارج النافذة المسموحة'});
      const id=String(b.eventId||randomUUID());if(!db.events.some(x=>x.storeId===a.store.storeId&&x.eventId===id))db.events.push({eventId:id,storeId:a.store.storeId,employeeId:a.employee.employeeId,employeeName:String(b.employeeName||a.employee.employeeName).slice(0,120),branchId:String(b.branchId||a.employee.branchId).slice(0,64),action,method,timestampEpochMillis:ts,evidence:String(b.evidence||'').slice(0,500),serverReceivedAt:Date.now(),source:'EMPLOYEE_PHONE'});a.employee.lastSeenAt=Date.now();saveDb();return json(res,200,{ok:true,eventId:id});
    }
    if(path==='/api/v1/store/attendance/pull' && req.method==='POST') {
      const st=activeStoreByToken(bearer(req));if(!st)return json(res,401,{error:'تفعيل المحل غير صالح'});const b=await readJson(req);if(!verifyDeviceProof(req,st.devicePublicKey,st.storeId,path,req._rawBody||'',st))return json(res,403,{error:'توقيع الجهاز غير صالح'});
      const after=Math.max(0,Number(b.afterCursor)||0),limit=Math.max(1,Math.min(200,Number(b.limit)||200));const rows=db.events.filter(x=>x.storeId===st.storeId&&Number(x.serverReceivedAt||0)>after).sort((a,b)=>Number(a.serverReceivedAt||0)-Number(b.serverReceivedAt||0)).slice(0,limit);const cursor=rows.reduce((m,x)=>Math.max(m,Number(x.serverReceivedAt||0)),after);return json(res,200,{events:rows,cursor});
    }

    const store=activeStoreByToken(bearer(req));
    if(path==='/api/v1/attendance/events/batch' && req.method==='POST') {
      if(!store) return json(res,401,{error:'تفعيل المحل غير صالح أو موقوف'}); const b=await readJson(req); if(!verifyDeviceProof(req,store.devicePublicKey,store.storeId,path,req._rawBody||'',store)) return json(res,403,{error:'توقيع الجهاز غير صالح'}); const events=Array.isArray(b.events)?b.events:[]; const accepted=[];
      for(const e of events.slice(0,1000)){ const id=String(e.eventId||''); if(!id) continue; if(!db.events.some(x=>x.storeId===store.storeId && x.eventId===id)){db.events.push({...e,storeId:store.storeId,serverReceivedAt:Date.now()})} accepted.push(id); }
      if(db.events.length>200000) db.events=db.events.slice(-200000); store.lastSeenAt=Date.now(); saveDb(); return json(res,200,{acceptedIds:accepted});
    }
    if(path==='/api/v1/report-receivers/register' && req.method==='POST') {
      if(!store) return json(res,401,{error:'تفعيل المحل غير صالح'}); const b=await readJson(req); if(!verifyDeviceProof(req,store.devicePublicKey,store.storeId,path,req._rawBody||'',store)) return json(res,403,{error:'توقيع الجهاز غير صالح'}); const receiverId=String(b.receiverId||''); const secret=String(b.secret||''); if(receiverId.length<5||secret.length<16) return json(res,400,{error:'بيانات هاتف الاستلام غير صالحة'});
      store.receivers=store.receivers||[]; let r=store.receivers.find(x=>x.receiverId===receiverId); const rec={receiverId,name:String(b.name||'هاتف مراقبة').slice(0,120),secretHash:hashSecret(secret),active:true,createdAt:r?.createdAt||Date.now(),lastUsedAt:0}; if(r) Object.assign(r,rec); else store.receivers.unshift(rec); saveDb(); return json(res,200,{ok:true});
    }
    if(path==='/api/v1/report-receivers/status' && req.method==='POST') {
      if(!store) return json(res,401,{error:'تفعيل المحل غير صالح'}); const b=await readJson(req); if(!verifyDeviceProof(req,store.devicePublicKey,store.storeId,path,req._rawBody||'',store)) return json(res,403,{error:'توقيع الجهاز غير صالح'}); const r=(store.receivers||[]).find(x=>x.receiverId===String(b.receiverId||'')); if(!r) return json(res,404,{error:'الهاتف غير موجود'}); r.active=!!b.active; saveDb(); return json(res,200,{ok:true});
    }
    if(path==='/api/v1/reports/push' && req.method==='POST') {
      if(!store) return json(res,401,{error:'تفعيل المحل غير صالح'}); const b=await readJson(req); if(!verifyDeviceProof(req,store.devicePublicKey,store.storeId,path,req._rawBody||'',store)) return json(res,403,{error:'توقيع الجهاز غير صالح'}); const receiverId=String(b.receiverId||''); const r=(store.receivers||[]).find(x=>x.receiverId===receiverId&&x.active); if(!r) return json(res,403,{error:'هاتف الاستلام غير مصرح'}); const transferId=String(b.transferId||''); if(!transferId) return json(res,400,{error:'رقم النقل مفقود'});
      db.reports=db.reports.filter(x=>!(x.storeId===store.storeId&&x.transferId===transferId)); db.reports.unshift({storeId:store.storeId,receiverId,transferId,packageText:String(b.packageText||''),confirmationHash:String(b.confirmationHash||''),status:'PENDING',createdAt:Date.now(),confirmedAt:0}); db.reports=db.reports.slice(0,100000); saveDb(); return json(res,200,{ok:true});
    }
    if(path==='/api/v1/reports/confirmations' && req.method==='GET') {
      if(!store) return json(res,401,{error:'تفعيل المحل غير صالح'}); if(!verifyDeviceProof(req,store.devicePublicKey,store.storeId,path,'',store)) return json(res,403,{error:'توقيع الجهاز غير صالح'}); return json(res,200,{transferIds:db.reports.filter(x=>x.storeId===store.storeId&&x.status==='CONFIRMED').slice(0,1000).map(x=>x.transferId)});
    }

    if(path==='/api/v1/reports/inbox' && req.method==='POST') {
      const b=await readJson(req); const auth=receiverAuth(String(b.receiverId||''),String(b.secret||'')); if(!auth) return json(res,403,{error:'هاتف الاستلام غير مصرح'}); auth.receiver.lastUsedAt=Date.now(); saveDb(); const reports=db.reports.filter(x=>x.storeId===auth.store.storeId&&x.receiverId===auth.receiver.receiverId&&x.status==='PENDING').slice(0,100).map(x=>({transferId:x.transferId,packageText:x.packageText})); return json(res,200,{reports});
    }
    if(path==='/api/v1/reports/confirm' && req.method==='POST') {
      const b=await readJson(req); const auth=receiverAuth(String(b.receiverId||''),String(b.secret||'')); if(!auth) return json(res,403,{error:'هاتف الاستلام غير مصرح'}); const rpt=db.reports.find(x=>x.storeId===auth.store.storeId&&x.receiverId===auth.receiver.receiverId&&x.transferId===String(b.transferId||'')); if(!rpt) return json(res,404,{error:'التقرير غير موجود'}); if(rpt.confirmationHash && rpt.confirmationHash!==pinHash(String(b.confirmationCode||''))) return json(res,403,{error:'رمز التأكيد غير صحيح'}); rpt.status='CONFIRMED'; rpt.confirmedAt=Date.now(); saveDb(); return json(res,200,{ok:true});
    }
    if(path==='/api/v1/monitor/report' && req.method==='POST') {
      const b=await readJson(req); const auth=receiverAuth(String(b.receiverId||''),String(b.secret||'')); if(!auth) return json(res,403,{error:'هاتف الاستلام غير مصرح'});
      const period=String(b.period||'TODAY').toUpperCase(); const now=Date.now(); let start=0;
      if(period==='TODAY'){const d=new Date();d.setHours(0,0,0,0);start=d.getTime()}
      else if(period==='WEEK') start=now-7*86400000;
      else if(period==='MONTH'){const d=new Date();d.setDate(1);d.setHours(0,0,0,0);start=d.getTime()}
      const ev=db.events.filter(x=>x.storeId===auth.store.storeId&&Number(x.timestampEpochMillis||0)>=start).sort((a,b)=>Number(a.timestampEpochMillis||0)-Number(b.timestampEpochMillis||0));
      const title=period==='WEEK'?'آخر 7 أيام':period==='MONTH'?'هذا الشهر':'اليوم'; const ins=ev.filter(e=>e.action==='CHECK_IN').length, outs=ev.filter(e=>e.action==='CHECK_OUT').length;
      const rows=ev.slice(-100).reverse().map(e=>`• ${e.employeeName||e.employeeId||''} — ${e.action==='CHECK_IN'?'حضور':e.action==='CHECK_OUT'?'انصراف':e.action||''} — ${iso(Number(e.timestampEpochMillis||0))}`).join('\n');
      return json(res,200,{reportText:`ATTEND PRO — ${auth.store.storeName}\nالفرع: ${auth.store.branchId}\nالفترة: ${title}\nالحضور: ${ins} • الانصراف: ${outs} • إجمالي الحركات: ${ev.length}\n\n${rows||'لا توجد حركات في هذه الفترة.'}`});
    }
    if(path==='/api/v1/monitor/summary' && req.method==='POST') {
      const b=await readJson(req); const auth=receiverAuth(String(b.receiverId||''),String(b.secret||'')); if(!auth) return json(res,403,{error:'هاتف الاستلام غير مصرح'}); const start=new Date(); start.setHours(0,0,0,0); const ev=db.events.filter(x=>x.storeId===auth.store.storeId&&Number(x.timestampEpochMillis||0)>=start.getTime()); const recent=[...ev].sort((a,b)=>Number(b.timestampEpochMillis||0)-Number(a.timestampEpochMillis||0)).slice(0,10).map(e=>({employeeId:e.employeeId||'',employeeName:e.employeeName||'',action:e.action==='CHECK_IN'?'حضور':e.action==='CHECK_OUT'?'انصراف':e.action||'',time:iso(Number(e.timestampEpochMillis||0))})); return json(res,200,{storeName:auth.store.storeName,branchId:auth.store.branchId,checkIns:ev.filter(e=>e.action==='CHECK_IN').length,checkOuts:ev.filter(e=>e.action==='CHECK_OUT').length,todayEvents:ev.length,lastSeen:iso(auth.store.lastSeenAt),recent});
    }
    return json(res,404,{error:'المسار غير موجود'});
  } catch (e) { console.error(e); return json(res,500,{error:'خطأ داخلي آمن'}); }
}

http.createServer(handler).listen(PORT,'0.0.0.0',()=>console.log(`ATTEND PRO central server listening on :${PORT}`));
