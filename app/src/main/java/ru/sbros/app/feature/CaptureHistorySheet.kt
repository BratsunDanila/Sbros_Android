package ru.sbros.app.feature
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.WorkspaceState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun CaptureHistorySheet(state: WorkspaceState,delete: suspend (String)->Boolean,close: ()->Unit) {
    val c=LocalSbrosColors.current
    Copy("История сбросов",23f,weight=750)
    Spacer(Modifier.height(8.dp));Copy("Исходные записи сохраняются целиком.",color=c.muted2)
    if(state.captures.isEmpty()){Spacer(Modifier.height(24.dp));Copy("Здесь появятся твои сохранённые сбросы.",color=c.muted2)}
    state.captures.forEach {capture->key(capture.id) {
        Spacer(Modifier.height(12.dp));Paper(Modifier.fillMaxWidth(),20.dp) {Column(Modifier.padding(16.dp)) {
            Copy(Instant.parse(capture.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm")),10f,c.muted)
            Spacer(Modifier.height(8.dp));Copy(capture.text,13f,line=21f)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Copy(if(capture.status=="committed")"Разобрано" else "Во входящих",10f,c.primary,700,Modifier.weight(1f))
                DeleteRecordAction("Удалить сброс","Исходная запись исчезнет из истории и входящих. Созданные из неё дела, проекты, мысли и события останутся.",iconOnly=true){delete(capture.id)}
            }
            val results=state.captureResults[capture.id]?: (state.thoughts.filter{it.captureId==capture.id}.map{it.title}+state.tasks.filter{it.captureId==capture.id}.map{it.title})
            results.forEach {title->Spacer(Modifier.height(6.dp));Copy("→ $title",11f,c.muted2)}
        }}
    }}
    Spacer(Modifier.height(18.dp));ActionButton("Закрыть",close,Modifier.fillMaxWidth())
}
