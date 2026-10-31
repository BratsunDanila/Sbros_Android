package ru.sbros.app.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import ru.sbros.app.R
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.ai.*

@Composable fun CaptureAnalysisScreen(state: CaptureAnalysisState,hasKey: Boolean,start: (Boolean)->Unit,manual: ()->Unit,
    edit: (CaptureProposal)->Unit,add: ()->Unit,confirm: ()->Unit,back: ()->Unit,openAi: ()->Unit) {
    val c=LocalSbrosColors.current
    var sourceExpanded by rememberSaveable(state.captureId) {mutableStateOf(false)}
    val saving=state.phase=="saving"
    BackHandler {if(!saving)back()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=18.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item("header") {
            QuietAction("К тексту",R.drawable.ic_left,c.muted2) {if(!saving)back()}
            Spacer(Modifier.height(28.dp))
            Copy(when(state.phase){"review","saving"->"Разложим\nпо полкам?";"processing"->"Немного ясности.";else->"Твой сброс."},35f,weight=750,line=39f,tracking=-2f)
            Spacer(Modifier.height(10.dp))
            Copy(when(state.phase){"review","saving"->"Это предложения. Измени всё, что хочется, и подтверди.";"processing"->"Отделяем дела от идей и мыслей. Без лишних обязательств.";else->"Сохраним смысл и найдём место для каждой мысли."},12f,c.muted2,line=19f)
            Spacer(Modifier.height(10.dp))
        }
        if(state.original.isNotBlank()&&state.phase!="saved")item("source") {
            Paper(Modifier.fillMaxWidth(),22.dp) {
                Column(Modifier.padding(17.dp)) {
                    Row(Modifier.fillMaxWidth().clickable {sourceExpanded=!sourceExpanded},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Glyph(R.drawable.ic_layers,17.dp);Copy("Исходный сброс",12f,weight=700,modifier=Modifier.weight(1f));Copy(if(sourceExpanded)"Свернуть" else "Открыть",10f,c.muted2)
                    }
                    if(sourceExpanded){Spacer(Modifier.height(12.dp));SelectionContainer {Copy(state.original,13f,line=21f)}}
                }
            }
        }
        if(state.phase in listOf("loading","processing"))item("loading") {
            Paper(Modifier.fillMaxWidth(),25.dp,true) {
                Column(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=36.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(32.dp),color=c.primary,strokeWidth=2.dp)
                    Spacer(Modifier.height(22.dp));Copy(if(state.phase=="loading")"Открываем запись…" else "Разбираем мысли…",18f,weight=700)
                    Spacer(Modifier.height(8.dp));Copy("Исходный текст уже на устройстве.",11f,c.muted2)
                }
            }
            Spacer(Modifier.height(14.dp));ActionButton("Вернуться к тексту",back,Modifier.fillMaxWidth(),primary=false)
        }
        if(state.error!=null)item("error") {
            Paper(Modifier.fillMaxWidth(),18.dp,true) {Copy(state.error,12f,c.muted2,modifier=Modifier.padding(16.dp),line=20f)}
        }
        if(state.phase in listOf("ready","consent","error"))item("ready") {
            Paper(Modifier.fillMaxWidth(),24.dp,true) {
                Column(Modifier.padding(22.dp)) {
                    Glyph(R.drawable.ic_spark,26.dp)
                    Spacer(Modifier.height(16.dp));Copy(if(hasKey)"Разберём вместе" else "Подключи ИИ",22f,weight=750,tracking=-.8f)
                    Spacer(Modifier.height(10.dp));Copy(if(hasKey)"На разбор отправится только этот текст — через Token Harbor выбранной модели. Запрос оплачивается с твоего баланса провайдера. Ничего не добавится в дела без подтверждения." else "Добавь личный ключ Token Harbor в настройках. Аккаунт «Сброса» для разбора не нужен. Можно продолжить и вручную.",12f,c.muted2,line=20f)
                    if(hasKey&&state.phase=="consent"){Spacer(Modifier.height(10.dp));Copy("Дальше кнопка «Разобрать» будет отправлять текущий сброс на такой же разбор.",11f,c.muted2,line=18f)}
                }
            }
            Spacer(Modifier.height(16.dp))
            if(hasKey)ActionButton(if(state.phase=="error")"Повторить разбор" else "Отправить и разобрать",{start(true)},Modifier.fillMaxWidth(),enabled=state.original.isNotBlank(),icon=R.drawable.ic_spark)
            else ActionButton("Настройки ИИ",openAi,Modifier.fillMaxWidth(),icon=R.drawable.ic_spark)
            Spacer(Modifier.height(9.dp));ActionButton("Разобрать вручную",manual,Modifier.fillMaxWidth(),primary=false,enabled=state.original.isNotBlank())
        }
        if(state.phase in listOf("review","saving")) {
            item("count") {
                Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    Eyebrow(if(state.model=="manual")"РУЧНОЙ РАЗБОР" else "ПРЕДЛОЖЕНИЯ ИИ",c.muted2,10f)
                    Copy("Выбрано · ${state.accepted.size}",10f,c.muted)
                }
            }
            items(state.proposals,key={it.id}) {p->ProposalCard(p,state.proposals,edit)}
            item("actions") {
                ActionButton("Добавить запись",add,Modifier.fillMaxWidth(),primary=false,enabled=!saving&&state.proposals.size<40,icon=R.drawable.ic_plus)
                Spacer(Modifier.height(18.dp))
                ActionButton(if(saving)"Сохраняем…" else "Подтвердить · ${state.accepted.size}",confirm,Modifier.fillMaxWidth(),enabled=!saving&&state.accepted.isNotEmpty(),icon=R.drawable.ic_check)
                Spacer(Modifier.height(10.dp));Copy("До подтверждения эти карточки остаются предложениями.",10f,c.muted,line=17f)
            }
        }
    }
}

@Composable private fun ProposalCard(p: CaptureProposal,all: List<CaptureProposal>,edit: (CaptureProposal)->Unit) {
    val c=LocalSbrosColors.current
    var details by rememberSaveable(p.id) {mutableStateOf(false)}
    Paper(Modifier.fillMaxWidth(),23.dp) {
        Column(Modifier.padding(17.dp)) {
            if(p.removed) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Copy(p.title.ifBlank {"Запись"},13f,c.muted,modifier=Modifier.weight(1f),maxLines=2)
                    QuietAction("Вернуть",R.drawable.ic_left) {edit(p.copy(removed=false))}
                }
            } else {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Copy(proposalKinds[p.kind]?:"Запись",10f,c.primary,750,Modifier.background(c.soft,RoundedCornerShape(12.dp)).padding(horizontal=10.dp,vertical=6.dp))
                    IconAction(R.drawable.ic_trash,"Убрать предложение") {edit(p.copy(removed=true))}
                }
                Spacer(Modifier.height(10.dp))
                BasicTextField(p.title,{edit(p.copy(title=it.take(500)))},Modifier.fillMaxWidth().heightIn(min=30.dp),
                    textStyle=TextStyle(fontFamily=Onest,fontSize=19.sp,lineHeight=25.sp,fontWeight=FontWeight(650),color=c.ink),cursorBrush=SolidColor(c.primary),
                    decorationBox={inner->Box {if(p.title.isBlank())Copy("Что хочется сохранить?",19f,c.muted);inner()}})
                all.find {it.id==p.projectRef&&it.kind=="projects"&&!it.removed}?.let {project->
                    Spacer(Modifier.height(8.dp));ProjectStepPill(project.title.ifBlank {"Новый проект"})
                }
                if(p.kind=="projects") {
                    val count=all.count {it.projectRef==p.id&&!it.removed}
                    Spacer(Modifier.height(8.dp));Copy("Шагов проекта · $count",11f,c.muted2)
                }
                ReferenceSelect(p.kind,proposalKinds.toList(),"Куда сохранить","proposal-kind-${p.id}") {kind->
                    val taskKind=kind in listOf("next","waiting","someday")
                    edit(p.copy(kind=kind,date=if(kind=="cal")p.date.ifBlank {p.dueDate} else "",time=if(kind=="cal")p.time else "",
                        dueDate=if(taskKind)p.dueDate.ifBlank {if(p.kind=="cal")p.date else ""} else "",projectRef=if(taskKind)p.projectRef else null))
                }
                if(p.kind in listOf("next","waiting","someday")) {
                    val projects=all.filter {it.kind=="projects"&&!it.removed}
                    if(projects.isNotEmpty())ReferenceSelect(p.projectRef?:"",listOf("" to "Самостоятельное дело")+projects.map {it.id to it.title.ifBlank {"Новый проект"}},"Проект","proposal-project-${p.id}") {edit(p.copy(projectRef=it.takeIf {it.isNotBlank()}))}
                    DateField(p.dueDate,{edit(p.copy(dueDate=it))},"Срок",placeholder="Без срока")
                    if(p.dueDate.isNotBlank())QuietAction("Убрать срок",R.drawable.ic_left,c.muted2) {edit(p.copy(dueDate=""))}
                }
                if(p.kind=="cal") {
                    DateField(p.date,{edit(p.copy(date=it))})
                    ReferenceField(p.time,{edit(p.copy(time=it.take(5)))},"Время · ЧЧ:ММ",placeholder="Весь день")
                }
                if(!details&&p.body.isNotBlank()){Spacer(Modifier.height(12.dp));Copy(p.body,12f,c.muted2,line=19f,maxLines=3)}
                Spacer(Modifier.height(6.dp));QuietAction(if(details)"Свернуть детали" else "Текст и детали",R.drawable.ic_edit,c.muted2) {details=!details}
                if(details) {
                    ReferenceField(p.body,{edit(p.copy(body=it.take(12000)))},"Текст записи",multiline=true)
                    if(p.kind in listOf("next","waiting","someday","projects"))ReferenceField(p.area,{edit(p.copy(area=it.take(80)))},"Сфера")
                    if(p.kind in listOf("next","waiting","someday")) {
                        ReferenceSelect(p.energy.toString(),listOf("0" to "Просто","1" to "Средне","2" to "Сложно"),"Сложность","proposal-energy-${p.id}") {edit(p.copy(energy=it.toInt()))}
                    }
                    if(p.excerpt.isNotBlank()){Spacer(Modifier.height(14.dp));Eyebrow("ИЗ ОРИГИНАЛА",c.muted,9f);Spacer(Modifier.height(5.dp));Copy(p.excerpt,11f,c.muted2,line=18f)}
                }
            }
        }
    }
}
