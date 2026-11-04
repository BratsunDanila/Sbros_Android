package ru.sbros.app.core.network

import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpResult(val status: Int,val body: String) {override fun toString()="HttpResult(status=$status, body=[redacted])"}
fun interface HttpTransport {suspend fun request(url: String,method: String,headers: Map<String,String>,body: String?): HttpResult}

class HttpsTransport(private val readTimeoutMillis: Int=20_000): HttpTransport {
    companion object {
        private val executor=Executors.newCachedThreadPool {task->Thread(task,"sbros-http").apply {isDaemon=true}}
    }
    override suspend fun request(url: String,method: String,headers: Map<String,String>,body: String?): HttpResult = suspendCancellableCoroutine { continuation ->
        val activeConnection=AtomicReference<HttpsURLConnection?>()
        val operation=executor.submit {
            var connection: HttpsURLConnection?=null
            try {
                if(!continuation.isActive)return@submit
                val target=URL(url);require(target.protocol=="https"&&target.userInfo==null)
                val opened=target.openConnection() as HttpsURLConnection
                connection=opened;activeConnection.set(opened)
                if(!continuation.isActive)return@submit
                val result=execute(opened,method,headers,body)
                continuation.resume(result)
            } catch(e: Exception) {
                if(continuation.isActive)continuation.resumeWithException(e)
            } finally {
                activeConnection.set(null)
                connection?.disconnect()
            }
        }
        continuation.invokeOnCancellation {
            operation.cancel(true)
            // Never wait for a socket/SSL lock on the UI thread while cancelling.
            activeConnection.get()?.let {connection->executor.execute {connection.disconnect()}}
        }
    }
    private fun execute(connection: HttpsURLConnection,method: String,headers: Map<String,String>,body: String?): HttpResult {
            connection.instanceFollowRedirects=false
            connection.connectTimeout=15_000;connection.readTimeout=readTimeoutMillis;connection.requestMethod=method
            connection.setRequestProperty("Accept","application/json")
            headers.forEach {(key,value)->require(!value.contains('\n')&&!value.contains('\r'));connection.setRequestProperty(key,value)}
            if(body!=null){connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json");connection.outputStream.use {it.write(body.toByteArray(Charsets.UTF_8))}}
            val status=connection.responseCode
            val source=if(status in 200..299)connection.inputStream else connection.errorStream
            val bytes=source?.use {input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val count=input.read(buffer);if(count<0)break;check(out.size()+count<=8_388_608){"Response too large"};out.write(buffer,0,count)};out.toByteArray()}?:byteArrayOf()
            return HttpResult(status,bytes.toString(Charsets.UTF_8))
    }
}
