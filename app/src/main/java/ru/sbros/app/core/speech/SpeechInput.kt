package ru.sbros.app.core.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable fun rememberSpeechInput(text: String,change: (String)->Unit): SpeechInput {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val currentText=rememberUpdatedState(text)
    val currentChange=rememberUpdatedState(change)
    val input=remember(context) {SpeechInput(context,{currentText.value},{currentChange.value(it)})}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if(granted)input.start() else input.permissionDenied()
        }
    }
    SideEffect {input.requestPermission={permission.launch(Manifest.permission.RECORD_AUDIO)}}
    DisposableEffect(input,owner) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP)input.cancel()}
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer);input.close()}
    }
    return input
}

class SpeechInput internal constructor(private val context: Context,private val text: ()->String,private val change: (String)->Unit) {
    var busy by mutableStateOf(false);private set
    // User-controlled dictation stays active across the system recognizer's phrases.
    var listening by mutableStateOf(false);private set
    var level by mutableFloatStateOf(0f);private set
    var partial by mutableStateOf("");private set
    var message by mutableStateOf<String?>(null);private set
    internal var requestPermission: ()->Unit={}
    private var recognizer: SpeechRecognizer?=null
    private var session=0
    private var closed=false
    private var engineListening=false
    private var awaitingResult=false
    private var accumulated=""
    private val handler=Handler(Looper.getMainLooper())
    private val restart=Runnable {if(listening&&!closed)startSegment()}
    private val timeout=Runnable {
        if(listening&&awaitingResult)nextSegment(partial)
        else {finish(partial);message="Не удалось дождаться службы распознавания. Попробуй ещё раз."}
    }

    fun toggle() {
        if(closed)return
        if(busy) {
            if(listening) {
                listening=false;level=0f;message="Завершаем диктовку…"
                handler.removeCallbacks(restart)
                if(recognizer==null||(!engineListening&&!awaitingResult)) {
                    finish(partial);message="Готово. Нажми на текст, если хочешь его поправить."
                } else {
                    handler.removeCallbacks(timeout);handler.postDelayed(timeout,12_000)
                    if(engineListening) {
                        engineListening=false;awaitingResult=true
                        try {recognizer?.stopListening()} catch(_: Exception) {finish(partial);message="Диктовка завершена."}
                    }
                }
            }
            return
        }
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermission()
        else start()
    }

    internal fun permissionDenied() {message="Разреши доступ к микрофону в настройках приложения, чтобы вводить голосом."}

    internal fun start() {
        if(closed||busy)return
        if(!SpeechRecognizer.isRecognitionAvailable(context)) {
            message="На устройстве нет службы распознавания речи. Включи её в настройках Android или введи текст вручную."
            return
        }
        accumulated=text();busy=true;listening=true
        startSegment()
    }

    private fun startSegment() {
        if(closed||!listening)return
        val id=++session
        partial="";level=0f;engineListening=false;awaitingResult=false;message="Подключаем микрофон…"
        try {
            val service=SpeechRecognizer.createSpeechRecognizer(context)
            recognizer=service
            service.setRecognitionListener(object: RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if(id==session) {
                        handler.removeCallbacks(timeout);engineListening=true
                        message="Слушаю. Паузы допустимы — закончи кнопкой."
                    }
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {if(id==session&&engineListening&&listening&&rmsdB.isFinite())level=((rmsdB+2f)/12f).coerceIn(0f,1f)}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if(id==session) {
                        engineListening=false;awaitingResult=true;level=0f
                        message=if(listening)"Сохраняю фразу и продолжаю слушать…" else "Завершаем диктовку…"
                        handler.removeCallbacks(timeout);handler.postDelayed(timeout,12_000)
                    }
                }
                override fun onPartialResults(results: Bundle?) {if(id==session)partial=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()}
                override fun onResults(results: Bundle?) {
                    if(id!=session)return
                    val result=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().ifBlank {partial}
                    if(listening)nextSegment(result)
                    else {finish(result);message="Готово. Нажми на текст, если хочешь его поправить."}
                }
                override fun onError(error: Int) {
                    if(id!=session)return
                    if(error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        if(listening)nextSegment(partial,300)
                        else {finish(partial);message="Диктовка завершена."}
                        return
                    }
                    finish(partial)
                    message=when(error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS->"Нет доступа к микрофону. Разреши его в настройках приложения."
                        SpeechRecognizer.ERROR_NETWORK,SpeechRecognizer.ERROR_NETWORK_TIMEOUT,SpeechRecognizer.ERROR_SERVER,SpeechRecognizer.ERROR_SERVER_DISCONNECTED->"Служба распознавания недоступна. Проверь интернет и попробуй ещё раз."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY->"Микрофон занят. Попробуй через несколько секунд."
                        SpeechRecognizer.ERROR_AUDIO->"Не удалось включить микрофон. Попробуй ещё раз."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE->"Русский язык недоступен. Добавь его в настройках службы распознавания Android."
                        else->"Не удалось распознать речь. Попробуй ещё раз."
                    }
                }
                override fun onEvent(eventType: Int,params: Bundle?) {}
            })
            handler.postDelayed(timeout,12_000)
            service.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
            })
        } catch(_: Exception) {finish(partial);message="Не удалось запустить распознавание. Проверь службу голосового ввода Android."}
    }

    private fun accept(result: String) {
        partial=""
        if(result.isNotBlank()) {
            accumulated=appendSpeech(accumulated,result)
            change(accumulated)
        }
    }
    private fun releaseSegment() {
        ++session
        handler.removeCallbacks(timeout);handler.removeCallbacks(restart)
        val service=recognizer;recognizer=null
        engineListening=false;awaitingResult=false;level=0f
        runCatching {service?.cancel()};runCatching {service?.destroy()}
    }
    private fun nextSegment(result: String,delay: Long=150) {
        accept(result);releaseSegment()
        message="Продолжаю диктовку…"
        handler.postDelayed(restart,delay)
    }
    private fun finish(result: String) {
        accept(result);releaseSegment()
        busy=false;listening=false
    }
    fun cancel() {if(busy){finish(partial);message=null}}
    internal fun close() {cancel();closed=true}
}

fun appendSpeech(text: String,speech: String): String=if(speech.isBlank())text else if(text.isBlank())speech.trim() else text.trimEnd()+"\n"+speech.trim()
