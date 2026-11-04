package ru.sbros.app.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

@Composable fun FirstStepSheet(task: ActionTask?,hasKey: Boolean,allowed: Boolean,
    suggest: suspend (Boolean)->String,save: suspend (String)->Boolean,openAi: ()->Unit,close: ()->Unit) {
    val c=LocalSbrosColors.current
    val scope=rememberCoroutineScope()
    var text by rememberSaveable(task?.id) {mutableStateOf("")}
    var consent by rememberSaveable {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var failure by remember {mutableStateOf<String?>(null)}
    Copy("Маленький первый шаг",23f,weight=750,tracking=-1f)
    Spacer(Modifier.height(8.dp))
    if(task==null) {
        Copy("Сначала выбери дело на главной карточке.",color=c.muted2)
        Spacer(Modifier.height(18.dp));ActionButton("Понятно",close,Modifier.fillMaxWidth())
        return
    }
    Copy(task.title,15f,line=22f)
    Spacer(Modifier.height(8.dp))
    Copy("Начни с одного простого действия. Оно добавится отдельным делом; исходное дело останется.",12f,c.muted2,line=19f)
    if(!allowed)Row(verticalAlignment=Alignment.CenterVertically) {
        Checkbox(consent,{if(!busy)consent=it})
        Copy("Разрешаю передать название этого дела в Token Harbor для подсказки.",11f,c.muted2,modifier=Modifier.weight(1f),line=17f)
    }
    Spacer(Modifier.height(14.dp))
    ActionButton(if(busy)"Подбираем шаг…" else if(text.isBlank())"Предложить с ИИ" else "Другая подсказка",{
        if(!busy) {
            if(!hasKey)openAi()
            else if(!allowed&&!consent)failure="Разреши отправку названия или напиши шаг самостоятельно."
            else {busy=true;failure=null;scope.launch {
                try {text=suggest(consent)}
                catch(e: CancellationException){throw e}
                catch(e: Exception){failure=(e as? AiFailure)?.publicMessage?:"Не удалось получить подсказку. Попробуй ещё раз."}
                finally {busy=false}
            }}
        }
    },Modifier.fillMaxWidth(),primary=false,enabled=!busy)
    ReferenceField(text,{if(!busy)text=it.take(500)},"Первое действие",placeholder="Например: открыть документ и написать заголовок")
    failure?.let {Spacer(Modifier.height(10.dp));Copy(it,12f,c.muted2,line=19f)}
    Spacer(Modifier.height(18.dp))
    ActionButton(if(busy)"Подожди…" else "Добавить шаг",{
        if(!busy&&text.isNotBlank()){busy=true;scope.launch {try {if(save(text.trim()))close()}finally{busy=false}}}
    },Modifier.fillMaxWidth(),enabled=!busy&&text.isNotBlank())
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
