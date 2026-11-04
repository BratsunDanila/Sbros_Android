package ru.sbros.app.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import ru.sbros.app.R
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.*
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch

@Composable private fun SheetTitle(title: String, subtitle: String?=null) {
    val c=LocalSbrosColors.current
    Copy(title,23f,weight=750,line=27.6f,tracking=-1f)
    if(subtitle!=null) {Spacer(Modifier.height(6.dp));Copy(subtitle,12f,c.muted2,line=19.2f)}
    Spacer(Modifier.height(20.dp))
}
@Composable fun InfoSheet(title: String, text: String, close: ()->Unit) {SheetTitle(title,text);ActionButton(stringResource(R.string.close),close,Modifier.fillMaxWidth())}
@Composable private fun Field(value: String,change: (String)->Unit,label: String,minLines: Int=1) {
    ReferenceField(value,change,label,multiline=minLines>1,minimumHeight=if(minLines>1)170.dp else 42.dp)
}
@Composable fun EventSheet(event: CalendarEvent?,date: String,save: suspend (CalendarEvent)->Boolean,delete: suspend ()->Boolean) {
    val scope=rememberCoroutineScope()
    var title by rememberSaveable(event?.id,date) {mutableStateOf(event?.title?:"")};var day by rememberSaveable(event?.id,date) {mutableStateOf(event?.date?:date)};var time by rememberSaveable(event?.id,date) {mutableStateOf(event?.time?:"")}
    var error by rememberSaveable {mutableStateOf("")}
    var submitted by remember(event?.id,date){mutableStateOf(false)}
    SheetTitle(if(event==null)"Запланировать" else "Событие","Только для дел, у которых действительно есть дата.")
    Field(title,{title=it},"Что и когда");DateField(day,{day=it});Field(time,{time=it},"Время · ЧЧ:ММ, пусто — весь день")
    if(error.isNotEmpty()) Copy(error,color=MaterialTheme.colorScheme.error)
    ActionButton(stringResource(R.string.save),{
        when {title.isBlank()||title.length>500->error="Укажи название до 500 символов";runCatching {LocalDate.parse(day)}.isFailure->error="Проверь дату: ДД.ММ.ГГГГ";time.isNotBlank()&&runCatching {LocalTime.parse(time)}.isFailure->error="Проверь время: ЧЧ:ММ"
            else->if(!submitted){submitted=true;scope.launch {if(!save(CalendarEvent(event?.id?:0,title.trim(),day,time))){submitted=false;error="Не удалось сохранить. Повтори попытку."}}}}
    },Modifier.fillMaxWidth(),enabled=!submitted)
    if(event!=null) {Spacer(Modifier.height(9.dp));DeleteRecordAction("Удалить событие","Событие исчезнет из календаря. Остальные записи и исходный сброс останутся.",enabled=!submitted,delete=delete)}
}
@Composable fun ThoughtDetail(thought: Thought,back: ()->Unit,favorite: ()->Unit,open: (String)->Unit,delete: suspend ()->Boolean) {
    val c=LocalSbrosColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=18.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {QuietAction(stringResource(R.string.thoughts),R.drawable.ic_left,c.muted2,onClick=back);IconAction(R.drawable.ic_heart,if(thought.favorite)"Убрать из избранного" else "В избранное",favorite)}
        Spacer(Modifier.height(41.dp));Eyebrow(if(thought.idea)"ИДЕЯ" else "ЗАПИСЬ ИЗ ДНЕВНИКА",size=10f)
        Spacer(Modifier.height(14.dp));Copy(thought.title,32f,weight=760,line=36.8f,tracking=-1.92f)
        Spacer(Modifier.height(13.dp));Copy(displayDate(LocalDate.now().minusDays(thought.daysAgo.toLong()).toString())+" · "+thought.time,11f,c.muted)
        Spacer(Modifier.height(27.dp));Paper(Modifier.fillMaxWidth(),25.dp,true) {Copy(thought.body,14f,line=27.3f,modifier=Modifier.padding(start=23.dp,end=23.dp,top=34.dp,bottom=26.dp))}
        Spacer(Modifier.height(16.dp))
        if(!thought.sourceText.isNullOrBlank()) QuietAction(stringResource(R.string.view_thought_source),R.drawable.ic_layers) {open("thought-source")}
        listOf(Triple("Продолжить мысль",R.drawable.ic_spark,"continue"),Triple("Похожие",R.drawable.ic_layers,"related"),Triple("В действие",R.drawable.ic_bolt,"to-action")).forEach {(label,icon,key)->
            Paper(Modifier.fillMaxWidth().clickable {open(key)},15.dp) {Row(Modifier.padding(horizontal=14.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Glyph(icon,16.dp);Copy(label,12f,weight=700)}};Spacer(Modifier.height(7.dp))
        }
        Spacer(Modifier.height(28.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            QuietAction(stringResource(R.string.edit),R.drawable.ic_edit,c.muted2){open("edit-thought")}
            DeleteRecordAction(if(thought.idea)"Удалить идею" else "Удалить запись","Запись исчезнет из «Мыслей». Связанные дела и исходный сброс останутся.",delete=delete)
        }
    }
}
@Composable fun ThoughtEditSheet(thought: Thought,mode: String,save: suspend (Thought)->Boolean,action: suspend (String)->Boolean) {
    val scope=rememberCoroutineScope()
    var submitting by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    var title by rememberSaveable(thought.id,mode){mutableStateOf(thought.title)};var body by rememberSaveable(thought.id,mode){mutableStateOf(if(mode=="continue")"" else thought.body)};var idea by rememberSaveable(thought.id,mode){mutableStateOf(thought.idea)}
    SheetTitle(when(mode){"continue"->"Продолжить мысль";"to-action"->"Из мысли — в действие";else->"Твоя мысль"},if(mode=="to-action")"Только если действительно хочешь что-то сделать. Исходная мысль останется в дневнике." else "Можно изменить всё, как тебе нравится.")
    if(mode!="continue")Field(title,{title=it},if(mode=="to-action")"Конкретное действие" else "Название")
    if(mode!="to-action") {
        Field(body,{body=it},if(mode=="continue")"Что ещё хочется добавить?" else "Запись",4)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {Pill("Дневник",!idea,{idea=false},solid=true);Pill("Идея",idea,{idea=true},solid=true)}
    }
    if(error)Copy("Не удалось сохранить. Повтори попытку.",color=MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(17.dp));ActionButton(stringResource(R.string.save),{if(!submitting){submitting=true;scope.launch {
        val success=if(mode=="to-action")action(title.trim()) else save(thought.copy(title=title.trim(),body=if(mode=="continue")thought.body+"\n\n"+body.trim() else body.trim(),idea=idea))
        if(!success){submitting=false;error=true}
    }}},Modifier.fillMaxWidth(),enabled=!submitting&&title.isNotBlank()&&title.length<=500&&(mode=="to-action"||body.isNotBlank()))
}
@Composable fun InboxSheet(inbox: List<StoredCapture>,resume: (StoredCapture)->Unit,delete: suspend (String)->Boolean,toThought: suspend (String)->Boolean,close: ()->Unit,capture: ()->Unit) {
    val scope=rememberCoroutineScope()
    val c=LocalSbrosColors.current
    SheetTitle("Входящие · ${inbox.size}","Здесь мысли ждут, пока ты захочешь с ними разобраться.")
    if(inbox.isEmpty())Copy("Всё разобрано. Можно выдохнуть.",color=c.muted2)
    inbox.forEach {capture -> key(capture.id) {
        val text=capture.text
        var acted by remember(capture.id){mutableStateOf(false)}
        Paper(Modifier.fillMaxWidth(),17.dp) {Column(Modifier.padding(15.dp)) {
            Copy(text,13f,line=20.8f)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                QuietAction("Разобрать",R.drawable.ic_spark){if(!acted){acted=true;resume(capture)}}
                QuietAction("В мысли",R.drawable.ic_feather){if(!acted){acted=true;scope.launch {if(!toThought(capture.id))acted=false}}}
                DeleteRecordAction("Удалить входящую запись","Запись исчезнет из входящих и истории сбросов. Остальные записи останутся.",enabled=!acted,iconOnly=true) {
                    val saved=delete(capture.id);if(saved)acted=true;saved
                }
            }
        }};Spacer(Modifier.height(10.dp))
    }}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        ActionButton(stringResource(R.string.close),close,Modifier.weight(1f),primary=false,minHeight=44.dp)
        ActionButton("Новая мысль",capture,Modifier.weight(1f),primary=false,icon=R.drawable.ic_plus,minHeight=44.dp)
    }
}
