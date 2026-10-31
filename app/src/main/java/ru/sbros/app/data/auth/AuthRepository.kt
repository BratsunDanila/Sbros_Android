package ru.sbros.app.data.auth

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import ru.sbros.app.core.network.*
import ru.sbros.app.core.security.SecretStore
import java.net.URI
import java.util.UUID

data class AccountUser(val id: String,val name: String,val email: String)
data class AuthState(val user: AccountUser?=null,val restoring: Boolean=true,val busy: Boolean=false,val error: String?=null,val needsLogin: Boolean=false)
class AuthFailure(val publicMessage: String): Exception(publicMessage)
class SupabaseConfig(url: String,val publicKey: String) {
    val url=url.trim().trimEnd('/')
    val configured: Boolean get()=runCatching {val uri=URI(this.url);uri.scheme=="https"&&!uri.host.isNullOrBlank()&&uri.userInfo==null&&uri.rawQuery==null&&uri.fragment==null&&uri.path.isNullOrEmpty()&&publicKey.isNotBlank()&&!publicKey.startsWith("sb_secret_")&&(
        publicKey.startsWith("sb_publishable_")||JSONObject(String(android.util.Base64.decode(publicKey.split('.')[1],android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP))).optString("role")=="anon")}.getOrDefault(false)
}
private class Session(val user: AccountUser,val access: String,val refresh: String,val expiresAt: Long) {
    fun json()=JSONObject().put("id",user.id).put("name",user.name).put("email",user.email).put("access",access).put("refresh",refresh).put("expires",expiresAt).toString()
    override fun toString()="Session(credentials=[redacted])"
}
class AuthRepository(val config: SupabaseConfig,private val secrets: SecretStore,private val http: HttpTransport,private val background: CoroutineScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)) {
    val state=MutableStateFlow(AuthState())
    private val mutex=Mutex()
    private var session: Session?=null
    suspend fun restore()=mutex.withLock {
        try {
            val raw=withContext(Dispatchers.IO){secrets.read("session")}
            if(raw!=null){val p=JSONObject(raw);val id=UUID.fromString(p.getString("id")).toString();session=Session(AccountUser(id,p.optString("name"),p.optString("email")),p.getString("access"),p.getString("refresh"),p.getLong("expires"))}
            state.value=AuthState(session?.user,restoring=false)
        } catch(e: Exception){state.value=AuthState(restoring=false,error="Не удалось открыть сохранённый вход. Войди снова; записи аккаунта остаются на устройстве.")}
    }
    suspend fun signIn(email: String,password: String)=authenticate(false,"",email,password)
    suspend fun signUp(name: String,email: String,password: String)=authenticate(true,name,email,password)
    private suspend fun authenticate(register: Boolean,name: String,email: String,password: String): Boolean {
        if(!mutex.tryLock())return false
        try {
            if(!config.configured)throw AuthFailure("Облако пока не настроено. Можно продолжить без аккаунта.")
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()||email.length>254)throw AuthFailure("Проверь адрес почты.")
            if(password.length<8||password.length>1024)throw AuthFailure("Используй пароль от 8 символов.")
            if(register&&(name.isBlank()||name.length>120))throw AuthFailure("Укажи имя до 120 символов.")
            state.value=state.value.copy(busy=true,error=null)
            val body=JSONObject().put("email",email.trim()).put("password",password)
            if(register)body.put("data",JSONObject().put("display_name",name.trim()))
            val result=http.request(config.url+if(register)"/auth/v1/signup" else "/auth/v1/token?grant_type=password","POST",mapOf("apikey" to config.publicKey),body.toString())
            if(result.status !in 200..299)throw authError(result)
            val json=JSONObject(result.body)
            if(!json.has("access_token"))throw AuthFailure("Подтверждение почты включено в Supabase. Для этого прототипа отключи Confirm Email в настройках проекта, затем войди.")
            val next=parseSession(json)
            withContext(Dispatchers.IO){secrets.write("session",next.json())}
            session=next;state.value=AuthState(next.user,restoring=false);return true
        } catch(e: Exception){if(e is CancellationException)throw e;state.value=state.value.copy(restoring=false,busy=false,error=(e as? AuthFailure)?.publicMessage?:"Не удалось связаться с облаком. Проверь интернет и повтори.");return false}
        finally {state.value=state.value.copy(busy=false);mutex.unlock()}
    }
    private fun parseSession(json: JSONObject): Session {
        val user=json.getJSONObject("user");val id=UUID.fromString(user.getString("id")).toString()
        val access=json.getString("access_token");val refresh=json.getString("refresh_token");require(access.isNotBlank()&&refresh.isNotBlank())
        return Session(AccountUser(id,user.optJSONObject("user_metadata")?.optString("display_name")?:"",user.optString("email")),access,refresh,json.optLong("expires_at",System.currentTimeMillis()/1000+json.optLong("expires_in",3600)))
    }
    private fun authError(result: HttpResult): AuthFailure {
        val code=runCatching {JSONObject(result.body).optString("error_code",JSONObject(result.body).optString("code"))}.getOrDefault("")
        return AuthFailure(when {result.status==429->"Слишком много попыток. Подожди и повтори.";code=="email_not_confirmed"->"В проекте включено подтверждение почты. Проверь настройку Confirm Email.";code in listOf("user_already_exists","email_exists")->"Аккаунт с такой почтой уже существует. Попробуй войти.";result.status in listOf(400,401,422)->"Проверь почту и пароль. Не удалось выполнить вход.";else->"Облако временно недоступно. Повтори позже."})
    }
    private suspend fun refresh(force: Boolean=false) {
        val current=session?:throw AuthFailure("Войди в аккаунт для синхронизации.")
        if(!force&&current.expiresAt>System.currentTimeMillis()/1000+60)return
        val response=http.request(config.url+"/auth/v1/token?grant_type=refresh_token","POST",mapOf("apikey" to config.publicKey),JSONObject().put("refresh_token",current.refresh).toString())
        if(response.status !in 200..299){if(response.status in listOf(400,401,403)){state.value=state.value.copy(needsLogin=true);throw AuthFailure("Войди снова. Локальные записи сохранены.")};throw authError(response)}
        val next=parseSession(JSONObject(response.body));check(next.user.id==current.user.id)
        withContext(Dispatchers.IO){secrets.write("session",next.json())};session=next;state.value=state.value.copy(user=next.user,needsLogin=false)
    }
    suspend fun request(userId: String,path: String,body: String): HttpResult=mutex.withLock {
        if(!config.configured)throw AuthFailure("Облако не настроено.")
        check(session?.user?.id==userId){"Account changed"}
        if(state.value.needsLogin)throw AuthFailure("Войди снова. Локальные записи сохранены.")
        refresh()
        suspend fun send()=http.request(config.url+path,"POST",mapOf("apikey" to config.publicKey,"Authorization" to "Bearer ${session!!.access}"),body)
        var result=send()
        if(result.status==401){refresh(true);result=send();if(result.status==401){state.value=state.value.copy(needsLogin=true);throw AuthFailure("Войди снова. Локальные записи сохранены.")}}
        result
    }
    suspend fun signOut(): Boolean=mutex.withLock {
        state.value=state.value.copy(busy=true,error=null)
        val current=session
        // Local sign-out works offline. Do not erase local account data.
        try{withContext(Dispatchers.IO){secrets.remove("session")}}catch(e: Exception){state.value=state.value.copy(busy=false,error="Не удалось завершить вход на устройстве. Повтори попытку.");return@withLock false}
        session=null;state.value=AuthState(restoring=false)
        if(current!=null&&config.configured)background.launch {runCatching {http.request(config.url+"/auth/v1/logout?scope=local","POST",mapOf("apikey" to config.publicKey,"Authorization" to "Bearer ${current.access}"),null)}}
        true
    }
    fun clearError(){state.value=state.value.copy(error=null)}
}
