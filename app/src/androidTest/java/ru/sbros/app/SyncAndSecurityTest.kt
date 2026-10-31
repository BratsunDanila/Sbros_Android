package ru.sbros.app

import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.sbros.app.core.network.*
import ru.sbros.app.core.security.*
import ru.sbros.app.data.*
import ru.sbros.app.data.ai.*
import ru.sbros.app.data.auth.*
import ru.sbros.app.data.local.*
import ru.sbros.app.data.sync.*
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.util.UUID

private class MemorySecrets: SecretStore {
    val values=mutableMapOf<String,String>()
    override fun read(name: String)=values[name]
    override fun write(name: String,value: String){values[name]=value}
    override fun remove(name: String){values.remove(name)}
}
/** Protocol fixture only. SQL implementation is independently tested with actual PostgreSQL/RLS. */
private class CloudFixture: HttpTransport {
    val user="10000000-0000-0000-0000-000000000001"
    var online=true;var loseAck=false;var refreshes=0;var expiresIn=3600L
    var onPush: (suspend ()->Unit)?=null
    val records=mutableMapOf<String,JSONObject>()
    val requests=mutableListOf<String>()
    private fun session()=JSONObject().put("access_token","fixture-access").put("refresh_token","fixture-refresh-$refreshes").put("expires_in",expiresIn)
        .put("user",JSONObject().put("id",user).put("email","test@example.test").put("user_metadata",JSONObject().put("display_name","Тест"))).toString()
    override suspend fun request(url: String,method: String,headers: Map<String,String>,body: String?): HttpResult {
        requests+=url
        if(!online)throw IOException("offline")
        if(url.contains("grant_type=refresh_token")){refreshes++;expiresIn=3600;return HttpResult(200,session())}
        if(url.contains("/auth/v1/token")||url.endsWith("/signup"))return HttpResult(200,session())
        if(url.contains("/logout"))return HttpResult(204,"")
        if(url.endsWith("sync_push")) {
            val changes=JSONObject(body!!).getJSONArray("p_changes");val out=JSONArray()
            for(i in 0 until changes.length()) {
                val change=changes.getJSONObject(i);val id=change.getString("id");val prior=records[id]
                if(prior!=null&&prior.getString("change_id")==change.getString("change_id"))out.put(JSONObject().put("status","ok").put("record",prior))
                else if(prior!=null&&prior.getLong("version")!=change.getLong("version"))out.put(JSONObject().put("status","conflict").put("record",prior))
                else {val next=JSONObject(change.toString()).put("version",(prior?.getLong("version")?:0)+1);records[id]=next;out.put(JSONObject().put("status","ok").put("record",next))}
            }
            onPush?.let{onPush=null;it()}
            if(loseAck){loseAck=false;throw IOException("connection lost after commit")}
            return HttpResult(200,out.toString())
        }
        if(url.endsWith("sync_pull")) {
            val p=JSONObject(body!!);val after=if(p.isNull("p_after"))null else p.getString("p_after")
            return HttpResult(200,JSONArray(records.values.filter{after==null||it.getString("id")>after}.sortedBy{it.getString("id")}.take(p.getInt("p_limit"))).toString())
        }
        error("Unexpected fixture route")
    }
}

@RunWith(AndroidJUnit4::class)
class SyncAndSecurityTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val config get()=SupabaseConfig("https://example.supabase.co","sb_publishable_fixture")
    @Test fun authRestoresOfflineRefreshesOnceAndNeverStoresAPassword()=runBlocking {
        val http=CloudFixture();val secrets=MemorySecrets();val background=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val auth=AuthRepository(config,secrets,http,background)
            http.expiresIn=0
            assertTrue(auth.signUp("Тест","test@example.test","never-store-this-password"))
            assertFalse(secrets.values.values.any{it.contains("never-store-this-password")})
            http.online=false
            val restored=AuthRepository(config,secrets,http,background);restored.restore()
            assertEquals(http.user,restored.state.value.user?.id)
            assertFalse(restored.state.value.restoring)
            http.online=true
            listOf(async{restored.request(http.user,"/rest/v1/rpc/sync_pull","{\"p_after\":null,\"p_limit\":200}")},async{restored.request(http.user,"/rest/v1/rpc/sync_pull","{\"p_after\":null,\"p_limit\":200}")}).awaitAll()
            assertEquals(1,http.refreshes)
            assertTrue(restored.signOut());assertNull(secrets.read("session"));assertNull(restored.state.value.user)
        } finally {background.cancel()}
    }
    @Test fun missingConfigurationAndNetworkFailuresAreHonest()=runBlocking {
        val http=CloudFixture();val secrets=MemorySecrets()
        val missing=AuthRepository(SupabaseConfig("",""),secrets,http)
        assertFalse(missing.signIn("test@example.test","password123"));assertTrue(http.requests.isEmpty());assertFalse(missing.state.value.busy)
        val configured=AuthRepository(config,secrets,http);http.online=false
        assertFalse(configured.signIn("test@example.test","password123"));assertNull(secrets.read("session"));assertNotNull(configured.state.value.error)
        assertFalse(SupabaseConfig("http://example.supabase.co","sb_publishable_x").configured)
        assertFalse(SupabaseConfig("https://example.supabase.co","sb_secret_forbidden").configured)
        val started=CompletableDeferred<Unit>()
        val slow=AuthRepository(config,MemorySecrets(),HttpTransport{_,_,_,_->started.complete(Unit);awaitCancellation()})
        val attempt=launch{slow.signIn("test@example.test","password123")};started.await();attempt.cancelAndJoin()
        assertFalse(slow.state.value.busy)
    }
    @Test fun offlineAndLostAcknowledgementKeepDataAndRetryWithoutDuplicates()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,SbrosDatabase::class.java).build();val bg=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val repo=RoomDataRepository(db);val http=CloudFixture();val auth=AuthRepository(config,MemorySecrets(),http,bg)
            assertTrue(auth.signIn("test@example.test","password123"));repo.enableSync(http.user);val scope="user:${http.user}"
            repo.saveCapture(scope,"Личный оригинал\nцеликом");repo.change(scope){it.saveTask(null,"Дело","next","")}
            val engine=SyncEngine(repo,auth);http.online=false
            assertEquals(SyncOutcome.RETRY,engine.run(http.user));assertTrue(repo.transaction{repo.dao.records(scope)}.all{it.dirty})
            http.online=true;http.loseAck=true
            assertEquals(SyncOutcome.RETRY,engine.run(http.user))
            assertEquals(SyncOutcome.SUCCESS,engine.run(http.user))
            assertEquals(2,http.records.size);assertFalse(repo.transaction{repo.dao.records(scope)}.any{it.dirty})
            assertEquals("Личный оригинал\nцеликом",repo.read(scope).data.captures.single().text)
            assertTrue(repo.read("user:other").data.captures.isEmpty())
        } finally {db.close();bg.cancel()}
    }
    @Test fun anEditMadeDuringUploadIsSentInTheNextPass()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,SbrosDatabase::class.java).build();val bg=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val repo=RoomDataRepository(db);val http=CloudFixture();val auth=AuthRepository(config,MemorySecrets(),http,bg)
            auth.signIn("test@example.test","password123");repo.enableSync(http.user);val scope="user:${http.user}"
            repo.change(scope){it.saveTask(null,"До отправки","next","")}
            http.onPush={repo.change(scope){it.saveTask(1,"Правка во время отправки","next","")}}
            assertEquals(SyncOutcome.SUCCESS,SyncEngine(repo,auth).run(http.user))
            assertEquals("Правка во время отправки",http.records.values.single().getJSONObject("payload").getString("title"))
            assertFalse(repo.transaction{repo.dao.records(scope).single().dirty})
        } finally {db.close();bg.cancel()}
    }
    @Test fun conflictsPreserveBothVersionsAndRemoteDeletionDoesNotLoseOriginal()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,SbrosDatabase::class.java).build();val bg=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val repo=RoomDataRepository(db);val http=CloudFixture();val auth=AuthRepository(config,MemorySecrets(),http,bg)
            auth.signIn("test@example.test","password123");repo.enableSync(http.user);val scope="user:${http.user}"
            repo.saveCapture(scope,"Не терять оригинал");val capture=repo.read(scope).data.captures.single();repo.moveToThought(scope,capture.id)
            val engine=SyncEngine(repo,auth);engine.run(http.user)
            val remote=http.records.values.first{it.getString("kind")=="thought"}
            remote.put("version",2).put("change_id",UUID.randomUUID().toString());remote.getJSONObject("payload").put("title","Облачная копия")
            repo.change(scope){s->s.copy(thoughts=s.thoughts.map{it.copy(title="Моя копия")})}
            engine.run(http.user)
            assertEquals("Моя копия",repo.read(scope).data.thoughts.single().title)
            assertEquals(1,repo.transaction{repo.dao.conflicts(scope).size})
            engine.resolve(http.user,remote.getString("id"),true);engine.run(http.user)
            assertEquals("Моя копия",http.records[remote.getString("id")]!!.getJSONObject("payload").getString("title"))
            assertEquals(1,repo.transaction{repo.dao.records(scope).count{it.kind=="local_conflict_archive"}})
            val cloud=http.records[remote.getString("id")]!!;cloud.put("version",4).put("deleted_at","2026-10-29T00:00:00Z").put("change_id",UUID.randomUUID().toString())
            engine.run(http.user);assertTrue(repo.read(scope).data.thoughts.isEmpty());assertEquals(capture.text,repo.read(scope).data.captures.single().text)
            repo.change(scope){it.saveTask(null,"После согласования","next","")}
            assertTrue(repo.transaction{repo.dao.records(scope).first{it.kind=="local_conflict_archive"}.deletedAt==null})
        } finally {db.close();bg.cancel()}
    }
    @Test fun nativeKeystoreUsesAuthenticatedEncryptionAndPurposeSeparation() {
        val folder=File(context.cacheDir,"secret-test-${UUID.randomUUID()}").apply{mkdirs()}
        val wrapped=object: ContextWrapper(context){override fun getNoBackupFilesDir()=folder}
        val alias="sbros.test.${UUID.randomUUID()}";val secret=KeystoreSecretStore(wrapped,alias)
        try {
            val key="thk_fixture_${UUID.randomUUID()}";secret.write("tokenharbor",key);secret.write("session","separate-session-fixture")
            assertEquals(key,secret.read("tokenharbor"))
            val file=File(folder,"secret-tokenharbor.bin");val first=file.readBytes()
            assertFalse(first.toString(Charsets.UTF_8).contains(key))
            secret.write("tokenharbor",key);assertFalse(first.contentEquals(file.readBytes()))
            File(folder,"secret-session.bin").copyTo(file,overwrite=true)
            assertTrue(runCatching{secret.read("tokenharbor")}.isFailure)
            secret.remove("tokenharbor");assertNull(secret.read("tokenharbor"));assertEquals("separate-session-fixture",secret.read("session"))
        }finally {folder.deleteRecursively();KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry(alias)}}
    }
    @Test fun byokCheckUsesOnlyCatalogAndRemovalDoesNotAffectManualData()=runBlocking {
        val secrets=MemorySecrets();var calls=0;var accept=true
        val key="thk_fixture_1234567890"
        val transport=HttpTransport{url,method,headers,body->
            calls++;assertEquals("https://tokenharbor.ai/v1/models",url);assertEquals("GET",method);assertNull(body);assertEquals("Bearer $key",headers["Authorization"])
            if(accept)HttpResult(200,"{\"data\":[{\"id\":\"fixture-model-from-response\"}]}")else HttpResult(401,"key=$key")
        }
        val repo=AiRepository(secrets,TokenHarborProvider(transport));repo.initialize()
        assertTrue(repo.save(key));assertEquals(0,calls);assertFalse(repo.state.value.verified)
        assertTrue(repo.check());assertTrue(repo.state.value.verified);assertEquals(listOf("fixture-model-from-response"),repo.state.value.models)
        accept=false;assertFalse(repo.check());assertFalse(repo.state.value.verified);assertFalse(repo.state.value.message.contains(key))
        val state=emptyState().saveTask(null,"Ручное дело","next","")
        assertTrue(repo.remove());assertFalse(repo.state.value.hasKey);assertNull(secrets.read("tokenharbor"));assertEquals("Ручное дело",state.tasks.single().title)
        assertFalse(recordJson(LocalRecord().apply{payload="{\"title\":\"Дело\"}"}).toString().contains(key))
        assertFalse(HttpResult(401,key).toString().contains(key))
    }
}
