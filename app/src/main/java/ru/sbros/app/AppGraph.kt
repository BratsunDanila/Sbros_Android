package ru.sbros.app

import android.app.Application
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.sbros.app.data.*
import ru.sbros.app.data.local.*
import ru.sbros.app.core.network.HttpsTransport
import ru.sbros.app.core.network.NetworkMonitor
import ru.sbros.app.core.security.KeystoreSecretStore
import ru.sbros.app.data.auth.*
import ru.sbros.app.data.sync.*
import ru.sbros.app.data.ai.*

class SbrosApplication: Application() {
    val graph: AppGraph by lazy {AppGraph(this)}
}
class AppGraph(context: Context) {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    val settings=SettingsRepository(context.applicationContext)
    val database=SbrosDatabase.open(context)
    val data=RoomDataRepository(database)
    val secrets=KeystoreSecretStore(context.applicationContext)
    val transport=HttpsTransport()
    val network=NetworkMonitor(context.applicationContext,scope)
    val auth=AuthRepository(SupabaseConfig(BuildConfig.SUPABASE_URL,BuildConfig.SUPABASE_PUBLISHABLE_KEY),secrets,transport,scope)
    val sync=SyncEngine(data,auth)
    val scheduler=SyncScheduler(context)
    val ai=AiRepository(secrets,TokenHarborProvider(HttpsTransport(readTimeoutMillis=90_000)))
    val activeScope=combine(settings.mode,auth.state){mode,state->when {mode in listOf("demo","legacy")->mode;mode=="account"&&state.user!=null->"user:${state.user.id}";else->"local"}}.stateIn(scope,SharingStarted.Eagerly,"local")
    init {
        scope.launch {runCatching {data.discardWeeklyReviews()}}
        scope.launch {auth.restore()}
        scope.launch {ai.initialize()}
        scope.launch {
            auth.state.map {it.user?.id}.distinctUntilChanged().collectLatest {id->
                if(id!=null){data.enableSync(id);scheduler.periodic(id);scheduler.enqueue(id)
                    data.observe("user:$id").map {data.transaction {data.dao.records("user:$id").filter{it.dirty&&it.kind in syncKinds}.map{it.changeId}.sorted()}}.distinctUntilChanged().collect {if(it.isNotEmpty())scheduler.enqueue(id)}
                }
            }
        }
    }
    suspend fun authenticated() {
        val user=auth.state.value.user?:return
        data.enableSync(user.id)
        if(data.countLocalRecords()>0)settings.pendingImport(user.id)
        settings.update(settings.appearance.first().copy(welcomed=true))
        settings.mode("account")
    }
    suspend fun authenticate(register: Boolean,name: String,email: String,password: String): Boolean = scope.async {
        val success=if(register)auth.signUp(name,email,password)else auth.signIn(email,password)
        if(success)authenticated()
        success
    }.await()
    suspend fun signOut(): Boolean {
        val success=auth.signOut()
        if(success){scheduler.cancel();settings.pendingImport("");settings.mode("local")}
        return success
    }
    suspend fun importLocal() {
        val user=auth.state.value.user?:error("No account")
        data.importLocal(user.id)
        check(auth.state.value.user?.id==user.id)
        settings.pendingImport("");settings.mode("account");scheduler.enqueue(user.id)
    }
    suspend fun keepLocal(){settings.pendingImport("");settings.mode("local")}
}
