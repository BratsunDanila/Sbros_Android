package ru.sbros.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretStore {
    fun read(name: String): String?
    fun write(name: String,value: String)
    fun remove(name: String)
}

/** Authentication tags bind ciphertext to its purpose. No secrets in preferences, Room or backups. */
class KeystoreSecretStore(context: Context,private val alias: String="sbros.stage2.aes.v1"): SecretStore {
    private val folder=context.noBackupFilesDir
    private fun file(name: String): AtomicFile {require(name in setOf("session","tokenharbor"));return AtomicFile(File(folder,"secret-$name.bin"))}
    private fun key(create: Boolean): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let{return it}
        check(create){"Secure storage unavailable"}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    @Synchronized override fun write(name: String,value: String) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key(true));updateAAD("sbros:$name:v1".toByteArray())}
        val plain=value.toByteArray(Charsets.UTF_8)
        val encrypted=try{cipher.doFinal(plain)}finally{plain.fill(0)}
        val data=ByteBuffer.allocate(2+cipher.iv.size+encrypted.size).put(1.toByte()).put(cipher.iv.size.toByte()).put(cipher.iv).put(encrypted).array()
        val atomic=file(name);val stream=atomic.startWrite()
        try {stream.write(data);atomic.finishWrite(stream)}catch(e: Exception){atomic.failWrite(stream);throw e}
    }
    @Synchronized override fun read(name: String): String? {
        val atomic=file(name);if(!atomic.baseFile.exists()&&!File(atomic.baseFile.path+".bak").exists())return null
        val data=atomic.readFully();require(data.size in 30..1_048_576)
        val buffer=ByteBuffer.wrap(data);require(buffer.get().toInt()==1)
        val size=buffer.get().toInt();require(size==12&&buffer.remaining()>size+16)
        val iv=ByteArray(size).also {buffer.get(it)};val encrypted=ByteArray(buffer.remaining()).also {buffer.get(it)}
        val plain=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(false),GCMParameterSpec(128,iv));updateAAD("sbros:$name:v1".toByteArray())}.doFinal(encrypted)
        return try{plain.toString(Charsets.UTF_8)}finally{plain.fill(0)}
    }
    @Synchronized override fun remove(name: String){file(name).delete()}
}
