package ru.sbros.app.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class HttpResult(val status: Int,val body: String) {override fun toString()="HttpResult(status=$status, body=[redacted])"}
fun interface HttpTransport {suspend fun request(url: String,method: String,headers: Map<String,String>,body: String?): HttpResult}

class HttpsTransport(private val readTimeoutMillis: Int=20_000): HttpTransport {
    override suspend fun request(url: String,method: String,headers: Map<String,String>,body: String?)=withContext(Dispatchers.IO) {
        val target=URL(url);require(target.protocol=="https"&&target.userInfo==null)
        val connection=target.openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects=false
            connection.connectTimeout=15_000;connection.readTimeout=readTimeoutMillis;connection.requestMethod=method
            connection.setRequestProperty("Accept","application/json")
            headers.forEach {(key,value)->require(!value.contains('\n')&&!value.contains('\r'));connection.setRequestProperty(key,value)}
            if(body!=null){connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json");connection.outputStream.use {it.write(body.toByteArray(Charsets.UTF_8))}}
            val status=connection.responseCode
            val source=if(status in 200..299)connection.inputStream else connection.errorStream
            val bytes=source?.use {input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val count=input.read(buffer);if(count<0)break;check(out.size()+count<=8_388_608){"Response too large"};out.write(buffer,0,count)};out.toByteArray()}?:byteArrayOf()
            HttpResult(status,bytes.toString(Charsets.UTF_8))
        } finally {connection.disconnect()}
    }
}
