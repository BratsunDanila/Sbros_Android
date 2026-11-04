package ru.sbros.app.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.*
import ru.sbros.app.data.ai.*

@Composable fun TaskAdviceSheet(context: TaskAdviceContext?,hasKey: Boolean,allowed: Boolean,
    suggest: suspend (Boolean)->String,openAi: ()->Unit,close: ()->Unit) {
    val c=LocalSbrosColors.current
    val scope=rememberCoroutineScope()
    var text by rememberSaveable {mutableStateOf("")}
    var consent by rememberSaveable {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var failure by remember {mutableStateOf<String?>(null)}
    Copy("Совет по делу",23f,weight=750,tracking=-1f)
    Spacer(Modifier.height(8.dp))
    if(context==null) {
        Copy("Это дело больше недоступно. Выбери другое на главной карточке.",color=c.muted2)
        Spacer(Modifier.height(18.dp));ActionButton("Понятно",close,Modifier.fillMaxWidth())
        return
    }
    context.project?.let {project->
        ProjectStepPill(project.title,compact=true)
        Spacer(Modifier.height(10.dp))
    }
    Copy(context.task.title,16f,weight=700,line=23f)
    Spacer(Modifier.height(8.dp))
    Copy(if(context.project!=null)"Подсказка с учётом цели проекта и связанных шагов." else "Подсказка, как подойти к этому делу.",12f,c.muted2,line=19f)
    if(!allowed&&hasKey)Row(verticalAlignment=Alignment.CenterVertically) {
        Checkbox(consent,{if(!busy)consent=it})
        Copy("Разрешаю передать это дело, его пояснение и контекст проекта в Token Harbor для совета.",11f,c.muted2,modifier=Modifier.weight(1f),line=17f)
    }
    if(text.isNotBlank()) {
        Spacer(Modifier.height(18.dp))
        Paper(Modifier.fillMaxWidth(),20.dp) {
            Column(Modifier.padding(17.dp)) {
                Eyebrow("Совет",size=9f)
                Spacer(Modifier.height(9.dp))
                SelectionContainer {Copy(text,13f,line=21f)}
            }
        }
    }
    failure?.let {Spacer(Modifier.height(12.dp));Copy(it,12f,c.muted2,line=19f)}
    Spacer(Modifier.height(18.dp))
    ActionButton(when {busy->"Готовим совет…";!hasKey->"Настроить ИИ";failure!=null->"Попробовать ещё раз";text.isBlank()->"Получить совет";else->"Другой совет"},{
        if(!busy) {
            if(!hasKey)openAi()
            else if(allowed||consent) {busy=true;failure=null;scope.launch {
                try {text=suggest(consent)}
                catch(e: CancellationException){throw e}
                catch(e: Exception){failure=(e as? AiFailure)?.publicMessage?:"Не удалось получить совет. Попробуй ещё раз."}
                finally {busy=false}
            }}
        }
    },Modifier.fillMaxWidth(),enabled=!busy&&(!hasKey||allowed||consent))
    Spacer(Modifier.height(8.dp));ActionButton("Закрыть",close,Modifier.fillMaxWidth(),primary=false)
}

private val commonWords=setOf("быть","было","будет","были","есть","этот","эта","это","эти","того","тоже","также","чтобы","потому","когда","если","только","очень","просто","можно","нужно","надо","меня","мне","себя","свои","своей","который","которая","которые","хочу","сегодня","завтра","потом","всего","всегда","сейчас","вроде","один","одна","одного","about","this","that","with","have","from","the","and")
private fun thoughtWords(text: String)=Regex("[\\p{L}]{3,}").findAll(text.lowercase(java.util.Locale.ROOT).replace('ё','е'))
    .map {it.value}.filter {it !in commonWords}.toSet()

@Composable fun RelatedThoughtsSheet(thought: Thought?,all: List<Thought>,open: (Int)->Unit,close: ()->Unit) {
    val c=LocalSbrosColors.current
    val related=remember(thought,all) {
        val words=thought?.let {thoughtWords(it.title+" "+it.body)}?:emptySet()
        all.filter {it.id!=thought?.id}.map {item->
            val shared=thoughtWords(item.title+" "+item.body).intersect(words)
            Triple(item,shared.size,shared.sorted().take(3).joinToString(", "))
        }.filter {it.second>0}.sortedByDescending {it.second}.take(8)
    }
    Copy("Похожие мысли",23f,weight=750,tracking=-1f)
    Spacer(Modifier.height(7.dp));Copy("Записи по общим словам. Поиск выполняется на устройстве.",12f,c.muted2,line=19f)
    Spacer(Modifier.height(18.dp))
    if(related.isEmpty())Copy("Пока нет записей с общими словами. Новые мысли появятся здесь, когда найдутся совпадения.",color=c.muted2,line=20f)
    related.forEach {(item,_,shared)->
        Paper(Modifier.fillMaxWidth().clickable {open(item.id)},18.dp) {
            Column(Modifier.padding(15.dp)) {
                Copy(item.title,15f,weight=700,line=20f)
                Spacer(Modifier.height(6.dp));Copy(item.body,12f,c.muted2,maxLines=2,line=18f)
                Spacer(Modifier.height(8.dp));Copy("Общее: $shared",10f,c.primary,line=16f)
            }
        }
        Spacer(Modifier.height(9.dp))
    }
    Spacer(Modifier.height(15.dp));ActionButton("Закрыть",close,Modifier.fillMaxWidth(),primary=false)
}
