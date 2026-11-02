package ru.sbros.app.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import ru.sbros.app.R
import ru.sbros.app.data.CAPTURE_TEXT_LIMIT
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.core.speech.appendSpeech

@Composable fun CaptureScreen(text: String, change: (String)->Unit, settings: ()->Unit, speech: ()->Unit, later: ()->Unit, analyze: ()->Unit,saving: Boolean=false,
    speechBusy: Boolean=false,speechListening: Boolean=false,speechPartial: String="",speechMessage: String?=null,speechLevel: Float=0f) {
    val c=LocalSbrosColors.current
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    LaunchedEffect(speechBusy) {focus.clearFocus(force=true);keyboard?.hide()}
    val displayedText=appendSpeech(text,speechPartial)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // CSS breakpoints refer to the whole phone, including browser status/navigation.
        val compact=maxHeight<690.dp; val tiny=maxHeight<500.dp; val narrow=maxWidth<355.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=if(maxWidth<355.dp)15.dp else 20.dp).padding(top=13.dp,bottom=21.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Spacer(Modifier.height(if(compact)3.dp else 35.dp))
            Eyebrow(stringResource(R.string.capture_eyebrow))
            Spacer(Modifier.height(13.dp))
            val size=if(tiny)29f else if(compact)32f else 39f
            Copy(stringResource(R.string.capture_title_first),size,weight=760,line=size*1.08f,tracking=-size*.065f,align=TextAlign.Center)
            Copy(stringResource(R.string.capture_title_second),size,c.primary,760,line=size*1.08f,tracking=-size*.065f,align=TextAlign.Center)
            Spacer(Modifier.height(if(compact)8.dp else 15.dp))
            Copy(stringResource(R.string.capture_sub),if(compact)10.5f else 11f,c.muted2,line=18f,align=TextAlign.Center)
            MicrophoneVisual(if(tiny)132.dp else if(compact)155.dp else 224.dp,compact,{
                focus.clearFocus(force=true);keyboard?.hide();speech()
            },speechListening,speechLevel)
            if(speechListening) {
                Row(Modifier.height(26.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                    listOf(.4f,.7f,1f,.7f,.4f).forEach {weight->
                        val barHeight by animateFloatAsState(4f+speechLevel.coerceIn(0f,1f)*20f*weight,tween(80),label="voice level")
                        Box(Modifier.width(4.dp).height(barHeight.dp).background(c.primary,RoundedCornerShape(3.dp)))
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
            Copy(if(speechListening)"Завершить диктовку" else if(speechBusy)"Распознаём…" else stringResource(R.string.speak),13f,weight=700,tracking=-.26f)
            Spacer(Modifier.height(6.dp))
            if(speechMessage!=null)Copy(speechMessage,11f,c.muted2,align=TextAlign.Center,line=17f)
            else if(!tiny) Copy(stringResource(R.string.type_below),11f,c.muted2)
            Spacer(Modifier.height(if(compact)8.dp else 20.dp))
            Paper(Modifier.fillMaxWidth(),21.dp) {
                Column(Modifier.padding(horizontal=if(compact)12.dp else 15.dp).padding(top=if(compact)10.dp else 15.dp,bottom=if(compact)7.dp else 10.dp)) {
                    BasicTextField(displayedText,change,Modifier.fillMaxWidth().heightIn(min=if(compact)57.dp else 73.dp).focusProperties {canFocus=!speechBusy&&!saving}.semantics { contentDescription="Текст сброса" },readOnly=speechBusy||saving,
                        textStyle=TextStyle(fontFamily=Onest,fontSize=12.sp,lineHeight=18.7.sp,color=c.ink),cursorBrush=SolidColor(c.primary),
                        decorationBox={ inner -> Box { if(displayedText.isEmpty()) Copy(stringResource(R.string.capture_hint),12f,c.muted); inner() } })
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Copy(stringResource(if(text.isBlank())R.string.no_categories else R.string.imperfect),9f,c.muted,modifier=Modifier.weight(1f))
                        if(displayedText.length>=CAPTURE_TEXT_LIMIT*4/5) Copy("${displayedText.length} / 5 000",9f,if(displayedText.length>CAPTURE_TEXT_LIMIT)c.primary else c.muted)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if(displayedText.length>CAPTURE_TEXT_LIMIT) {
                Copy("Сократи запись до 5 000 символов перед сохранением или разбором. Текст не обрезается.",10f,c.primary,line=15f)
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ActionButton(if(saving)"Сохраняем…" else stringResource(R.string.later),later,Modifier.width(if(narrow)94.dp else 112.dp),primary=false,enabled=text.isNotBlank()&&text.length<=CAPTURE_TEXT_LIMIT&&!saving&&!speechBusy)
                ActionButton(stringResource(R.string.analyze),analyze,Modifier.weight(1f),enabled=text.isNotBlank()&&text.length<=CAPTURE_TEXT_LIMIT&&!saving&&!speechBusy,icon=R.drawable.ic_arrowright)
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(top=9.dp,end=16.dp)) { IconAction(R.drawable.ic_sliders,stringResource(R.string.settings),settings) }
    }
}

@Composable fun MicrophoneVisual(height: Dp=224.dp, compact: Boolean=false, click: ()->Unit,listening: Boolean=false,level: Float=0f) {
    val c=LocalSbrosColors.current
    val pulse by animateFloatAsState(if(listening)1f+level.coerceIn(0f,1f)*.12f else 1f,tween(100),label="microphone volume")
    val outer=if(compact)152.dp else 212.dp; val middle=if(compact)129.dp else 168.dp; val inner=if(compact)105.dp else 127.dp; val mic=if(compact)79.dp else 97.dp
    Box(Modifier.fillMaxWidth().height(height),contentAlignment=Alignment.Center) {
        Box(Modifier.size(outer).scale(pulse).background(Brush.radialGradient(listOf(c.accent.copy(alpha=.58f),Color.Transparent)),CircleShape).border(1.dp,c.primary.copy(alpha=if(listening).30f else .12f),CircleShape))
        Box(Modifier.size(middle).background(c.accent.copy(alpha=.31f),CircleShape).border(1.dp,c.primary.copy(alpha=.19f),CircleShape))
        Box(Modifier.size(inner).background(c.accent.copy(alpha=.58f),CircleShape).border(1.dp,c.primary.copy(alpha=.12f),CircleShape))
        Box(Modifier.size(mic).shadow(16.dp,RoundedCornerShape(if(compact)28.dp else 35.dp),ambientColor=c.primary.copy(alpha=.19f),spotColor=c.primary.copy(alpha=.19f)).background(c.primary,RoundedCornerShape(if(compact)28.dp else 35.dp)).border(1.dp,lerp(c.primary,Color.White,.3f),RoundedCornerShape(if(compact)28.dp else 35.dp)).clickable(onClick=click).semantics { contentDescription=if(listening)"Завершить диктовку" else "Голосовой ввод" },contentAlignment=Alignment.Center) {
            if(listening)Box(Modifier.size(if(compact)22.dp else 26.dp).background(c.onPrimary,RoundedCornerShape(5.dp)))
            else Glyph(R.drawable.ic_mic_hero,if(compact)29.dp else 35.dp,c.onPrimary)
        }
        Copy("✧",23f,c.primary.copy(alpha=.5f),modifier=Modifier.align(Alignment.CenterEnd).padding(end=35.dp).offset(y=(-26).dp))
        Copy("✳",23f,c.primary.copy(alpha=.4f),modifier=Modifier.align(Alignment.CenterStart).padding(start=30.dp).offset(y=40.dp))
    }
}
