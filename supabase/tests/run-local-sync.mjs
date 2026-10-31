// Runs real PostgreSQL SQL/RLS locally in PGlite. Does not contact a Supabase project.
// Setup: npm install --prefix .tools/sql-node --ignore-scripts @electric-sql/pglite@0.5.13
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {randomUUID} from 'node:crypto';
import assert from 'node:assert/strict';
const require=createRequire(resolve('.tools/sql-node/package.json'));
const {PGlite}=require('@electric-sql/pglite');
const db=new PGlite();
let passed=0;
const A='10000000-0000-0000-0000-000000000001',B='10000000-0000-0000-0000-000000000002';
await db.exec(`create role anon nologin;create role authenticated nologin;create schema auth;
 create table auth.users(id uuid primary key,raw_user_meta_data jsonb not null default '{}'::jsonb);
 create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
 grant usage on schema auth to anon,authenticated;grant execute on function auth.uid() to anon,authenticated;`);
await db.exec(readFileSync('supabase/migrations/001_core.sql','utf8'));
await db.query('insert into auth.users(id,raw_user_meta_data) values($1,$2),($3,$4)',[A,{display_name:'Аня'},B,{display_name:'Борис'}]);
const legacy=randomUUID();
await db.query(`insert into public.captures(id,user_id,raw_text,created_at) values($1,$2,'Оригинал прежней версии','2020-02-03T04:05:06Z')`,[legacy,A]);
await db.exec(readFileSync('supabase/migrations/002_local_first_sync.sql','utf8'));
async function test(name,run){await run();passed++;console.log('PASS '+name)}
async function role(user,kind='authenticated'){await db.exec('reset role');await db.query("select set_config('request.jwt.claim.sub',$1,false)",[user??'']);await db.exec('set role '+kind)}
async function push(rows){return (await db.query('select public.sync_push($1::jsonb) as result',[JSON.stringify(rows)])).rows[0].result}
async function pull(after=null,limit=200){return (await db.query('select public.sync_pull($1::uuid,$2) as result',[after,limit])).rows[0].result}
async function rejected(fn,expected){let error;try{await fn()}catch(e){error=e}assert.ok(error,'Expected rejection');if(expected)assert.match(String(error.message),expected)}
function record(kind,payload,extra={}){return {id:randomUUID(),kind,payload,project_id:null,capture_id:null,created_at:'2021-01-02T03:04:05Z',updated_at:'2021-01-02T03:04:05Z',deleted_at:null,version:0,change_id:randomUUID(),...extra}}
await test('001 records survive 002 with original UUID/date/text',async()=>{await role(A);const r=(await pull()).find(x=>x.id===legacy);assert.equal(r.payload.text,'Оригинал прежней версии');assert.equal(Date.parse(r.created_at),Date.parse('2020-02-03T04:05:06Z'))});
await test('profile linked to auth user and populated from name',async()=>{assert.equal((await db.query('select display_name from public.profiles where user_id=$1',[A])).rows[0].display_name,'Аня')});
const capture=record('capture',{text:'Личная мысль\nполностью',status:'inbox',source:'text',confirmedAt:null});
const project=record('project',{title:'Результат',area:'Личное',outcome:'Готово'});
const action=record('action',{title:'Шаг',area:'Личное',energy:0,hint:'',status:'next',completedAt:null},{project_id:project.id,capture_id:capture.id});
const thought=record('thought',{title:'Мысль',body:capture.payload.text,sourceText:capture.payload.text,idea:false,time:'12:00',favorite:false},{capture_id:capture.id});
const batch=[capture,project,action,thought];
await test('atomic linked captures/projects/actions/thoughts upload',async()=>{const r=await push(batch);assert.equal(r.length,4);assert.ok(r.every(x=>x.status==='ok'&&x.record.version===1));const all=await pull();assert.equal(all.find(x=>x.id===action.id).project_id,project.id);assert.equal(all.find(x=>x.id===thought.id).capture_id,capture.id)});
await test('retry after a lost acknowledgement is idempotent',async()=>{const count=(await pull()).length;const result=await push(batch);assert.equal((await pull()).length,count);assert.ok(result.every(x=>x.record.version===1))});
let edited={...action,version:1,change_id:randomUUID(),payload:{...action.payload,title:'Изменённый шаг'}};
await test('compare-and-swap increments version',async()=>{assert.equal((await push([edited]))[0].record.version,2)});
await test('concurrent stale edit returns conflict without overwrite',async()=>{const r=(await push([{...action,version:1,change_id:randomUUID(),payload:{...action.payload,title:'Другая копия'}}]))[0];assert.equal(r.status,'conflict');assert.equal(r.record.payload.title,'Изменённый шаг');assert.equal(r.record.version,2)});
await test('original text is immutable and entire batch rolls back',async()=>{const changedProject={...project,version:1,change_id:randomUUID(),payload:{...project.payload,title:'Не должно сохраниться'}};await rejected(()=>push([changedProject,{...capture,version:1,change_id:randomUUID(),payload:{...capture.payload,text:'Подмена оригинала'}}]),/immutable_original/);assert.equal((await pull()).find(x=>x.id===project.id).payload.title,'Результат')});
await test('original created_at cannot be overwritten',async()=>{const update={...capture,version:1,change_id:randomUUID(),created_at:'2035-01-01T00:00:00Z',payload:{...capture.payload,status:'committed',confirmedAt:'2026-10-29T00:00:00Z'}};const r=(await push([update]))[0].record;assert.equal(Date.parse(r.created_at),Date.parse(capture.created_at));assert.equal(r.payload.text,capture.payload.text)});
await test('deletion is a versioned tombstone and is returned in pulls',async()=>{const deleted={...edited,version:2,change_id:randomUUID(),deleted_at:'2026-10-29T01:00:00Z'};const r=(await push([deleted]))[0].record;assert.equal(r.version,3);assert.ok((await pull()).find(x=>x.id===action.id).deleted_at)});
await test('undo deletion keeps UUID and uses CAS',async()=>{const restored={...edited,version:3,change_id:randomUUID(),deleted_at:null};const r=(await push([restored]))[0].record;assert.equal(r.id,action.id);assert.equal(r.version,4);assert.equal(r.deleted_at,null)});
await test('B cannot read A records',async()=>{await role(B);assert.equal((await pull()).length,0);assert.equal((await db.query('select * from public.sync_records where user_id=$1',[A])).rows.length,0)});
await test('B cannot attach records to A project',async()=>{await rejected(()=>push([record('action',{title:'Чужая ссылка',status:'next',energy:0},{project_id:project.id})]),/foreign key/i);assert.equal((await pull()).length,0)});
await test('body user_id cannot impersonate another account',async()=>{const own=record('reference',{title:'Своя заметка',body:'Текст'},{user_id:A});await push([own]);assert.equal((await pull()).length,1);await role(A);assert.ok(!(await pull()).some(x=>x.id===own.id))});
await test('authenticated cannot bypass CAS with direct table UPDATE',async()=>{await rejected(()=>db.query('update public.sync_records set version=999 where id=$1',[action.id]),/permission denied/)});
await test('reference must have the expected kind',async()=>{await rejected(()=>push([record('action',{title:'Плохая ссылка',status:'next',energy:0},{project_id:capture.id})]),/invalid_reference_kind/)});
await test('empty titles and invalid kinds are rejected',async()=>{await rejected(()=>push([record('reference',{title:'  '})]),/invalid_title/);await rejected(()=>push([record('key',{title:'not allowed'})]),/invalid_record/)});
await test('calendar dates must be valid',async()=>{await rejected(()=>push([record('event',{title:'Дата',date:'2026-99-99',time:''})]));});
await test('anonymous cannot read data or invoke synchronization',async()=>{await role(null,'anon');await rejected(()=>pull(),/permission denied/);await rejected(()=>push([capture]),/permission denied/);await role(A)});
await test('pagination has no fixed 200-record truncation',async()=>{const records=Array.from({length:205},(_,i)=>record('reference',{title:'Материал '+i,body:'Полный текст'}));for(let i=0;i<records.length;i+=100)await push(records.slice(i,i+100));const ids=new Set();let after=null;while(true){const page=await pull(after,70);page.forEach(x=>ids.add(x.id));if(page.length<70)break;after=page.at(-1).id}assert.equal(ids.size,210)});
await test('legacy tables no longer accept independent writes',async()=>{await rejected(()=>db.query("insert into public.captures(user_id,raw_text) values($1,'x')",[A]),/permission denied/)});
console.log(`OK (${passed} PostgreSQL/RLS tests, PGlite 0.5.13; no live Supabase credentials used)`);
await db.close();
