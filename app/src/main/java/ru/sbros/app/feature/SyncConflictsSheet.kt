package ru.sbros.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.local.*

@Composable private fun RecordVersion(label: String,json: String) {
    val c=LocalSbrosColors.current;val record=JSONObject(json);val body=record.getJSONObject("payload")
    Paper(Modifier.fillMaxWidth(),17.dp){SelectionContainer {Column(Modifier.padding(14.dp)) {
        Copy(label,10f,c.muted,750);Spacer(Modifier.height(7.dp))
        Copy(body.optString("title",if(record.optString("kind")=="capture")"Исходный сброс" else "Запись"),14f,weight=750)
        if(record.optString("kind")=="project") {
            Spacer(Modifier.height(7.dp));Copy(if(body.optional("completedAt")!=null)"Проект завершён" else "Проект в работе",11f,c.primary)
        }
        val text=body.optString("text",body.optString("body",body.optString("outcome",body.optString("hint"))))
        if(text.isNotBlank()){Spacer(Modifier.height(7.dp));Copy(text,12f,c.muted2,line=20f)}
        if(!record.isNull("deleted_at")){Spacer(Modifier.height(7.dp));Copy("В этой версии запись удалена",11f,c.muted2)}
    }}}
}
@Composable fun SyncConflictsSheet(conflicts: List<SyncConflict>,resolve: suspend (String,Boolean)->Boolean,close: ()->Unit) {
    val scope=rememberCoroutineScope();val c=LocalSbrosColors.current;var busy by remember{mutableStateOf(false)};var failed by remember{mutableStateOf(false)}
    Copy("Выберем версию.",23f,weight=750);Spacer(Modifier.height(8.dp));Copy("Запись менялась на разных устройствах. Обе версии сохранятся в истории.",color=c.muted2)
    if(conflicts.isEmpty()){Spacer(Modifier.height(20.dp));Copy("Все изменения согласованы.",color=c.muted2)}
    conflicts.forEach {conflict->
        val analysisGroup=JSONObject(conflict.remoteJson).optBoolean("analysis_group_conflict")
        if(analysisGroup){Spacer(Modifier.height(16.dp));Copy("Этот сброс разобрали на двух устройствах. Выбор относится ко всему разбору. Свой вариант можно вернуть во входящие, не добавляя повторные дела.",12f,c.muted2,line=20f)}
        Spacer(Modifier.height(18.dp));RecordVersion("НА ЭТОМ УСТРОЙСТВЕ",conflict.localJson)
        Spacer(Modifier.height(8.dp));RecordVersion("В ОБЛАКЕ",conflict.remoteJson)
        Spacer(Modifier.height(10.dp));ActionButton(if(analysisGroup)"Вернуть мой разбор во входящие" else "Оставить версию устройства",{if(!busy){busy=true;scope.launch {failed=!resolve(conflict.recordId,true);busy=false}}},Modifier.fillMaxWidth(),enabled=!busy)
        Spacer(Modifier.height(7.dp));ActionButton(if(analysisGroup)"Оставить разбор из облака" else "Использовать версию из облака",{if(!busy){busy=true;scope.launch {failed=!resolve(conflict.recordId,false);busy=false}}},Modifier.fillMaxWidth(),primary=false,enabled=!busy)
    }
    if(failed)Copy("Не удалось сохранить выбор. Повтори попытку.",color=c.muted2)
    Spacer(Modifier.height(16.dp));ActionButton("Закрыть",close,Modifier.fillMaxWidth(),primary=false)
}
@Composable fun ConflictHistorySheet(records: List<LocalRecord>,close: ()->Unit) {
    val c=LocalSbrosColors.current
    Copy("Сохранённые версии",23f,weight=750);Spacer(Modifier.height(8.dp));Copy("Копии записей до согласования изменений.",color=c.muted2)
    if(records.isEmpty()){Spacer(Modifier.height(20.dp));Copy("Здесь пока пусто.",color=c.muted2)}
    records.forEach {record->val p=JSONObject(record.payload);Spacer(Modifier.height(20.dp));RecordVersion("УСТРОЙСТВО",p.getJSONObject("local").toString());Spacer(Modifier.height(8.dp));RecordVersion("ОБЛАКО",p.getJSONObject("cloud").toString())
        p.optJSONArray("analysis_results")?.let {results->for(i in 0 until results.length()){Spacer(Modifier.height(8.dp));RecordVersion("ЗАПИСЬ ИЗ РАЗБОРА УСТРОЙСТВА",results.getJSONObject(i).toString())}}
    }
    Spacer(Modifier.height(16.dp));ActionButton("Закрыть",close,Modifier.fillMaxWidth())
}
