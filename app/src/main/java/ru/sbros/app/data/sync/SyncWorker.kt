package ru.sbros.app.data.sync

import android.content.Context
import androidx.work.*
import ru.sbros.app.SbrosApplication
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context,parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result {
        val graph=(applicationContext as SbrosApplication).graph
        graph.auth.state.first {!it.restoring}
        val user=inputData.getString("user")?:return Result.failure()
        if(graph.auth.state.value.user?.id!=user)return Result.success()
        return when(graph.sync.run(user)){SyncOutcome.SUCCESS,SyncOutcome.NEEDS_LOGIN->Result.success();SyncOutcome.RETRY->Result.retry()}
    }
}
class SyncScheduler(context: Context) {
    private val work=WorkManager.getInstance(context.applicationContext)
    private val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun enqueue(user: String,manual: Boolean=false) {
        work.enqueueUniqueWork("sbros-sync-$user",if(manual)ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,OneTimeWorkRequestBuilder<SyncWorker>().setInputData(workDataOf("user" to user)).setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).addTag("sbros-cloud").build())
    }
    fun periodic(user: String) {
        work.enqueueUniquePeriodicWork("sbros-periodic-$user",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<SyncWorker>(15,TimeUnit.MINUTES).setInputData(workDataOf("user" to user)).setConstraints(constraints).addTag("sbros-cloud").build())
    }
    fun cancel(){work.cancelAllWorkByTag("sbros-cloud")}
}
