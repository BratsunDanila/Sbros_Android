package ru.sbros.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.sbros.app.R
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.ai.AiState

@Composable fun AiSettingsSheet(state: AiState,save: suspend (String)->Boolean,remove: suspend ()->Boolean,check: suspend ()->Boolean,close: ()->Unit) {
    val c=LocalSbrosColors.current;val scope=rememberCoroutineScope()
    var key by remember {mutableStateOf("")};var deleting by remember{mutableStateOf(false)}
    Copy("Искусственный интеллект",28f,weight=750,line=32f,tracking=-1.2f)
    Spacer(Modifier.height(10.dp));Copy("Твой личный ключ Token Harbor. Аккаунт Сброса не нужен.",color=c.muted2,line=20f)
    Spacer(Modifier.height(18.dp));Paper(Modifier.fillMaxWidth(),18.dp){Column(Modifier.padding(15.dp)){
        Copy(if(state.verified)"Подключение проверено" else if(state.hasKey)"Ключ сохранён" else "Ключ не добавлен",15f,weight=750)
        Spacer(Modifier.height(6.dp));Copy(if(state.busy)"Подождём немного…" else state.message,11f,c.muted2,modifier=Modifier.testTag("ai-status"),line=18f)
    }}
    PrivateField(key,{key=it.take(4096)},if(state.hasKey)"Новый ключ" else "API-ключ Token Harbor","ai-key-input",showToggle=false)
    Spacer(Modifier.height(12.dp));Copy("Ключ остаётся зашифрованным на устройстве. Он не попадает в облако, резервные копии и журнал приложения.",11f,c.muted2,line=18f)
    Spacer(Modifier.height(15.dp));ActionButton(if(state.hasKey)"Заменить ключ" else "Сохранить ключ",{scope.launch {if(save(key))key=""}},Modifier.fillMaxWidth().testTag("ai-save-key"),enabled=key.isNotBlank()&&!state.busy)
    Spacer(Modifier.height(9.dp));ActionButton("Проверить подключение",{scope.launch {check()}},Modifier.fillMaxWidth().testTag("ai-check"),primary=false,enabled=state.hasKey&&!state.busy)
    if(state.hasKey){Spacer(Modifier.height(9.dp));QuietAction("Удалить ключ",R.drawable.ic_trash){if(!state.busy)deleting=true}}
    if(state.models.isNotEmpty()){Spacer(Modifier.height(10.dp));Copy("Из каталога: "+state.models.take(3).joinToString(", "),10f,c.muted,line=17f)}
    Spacer(Modifier.height(18.dp));Copy("Проверка подключения запрашивает только каталог моделей. При разборе текущий сброс отправляется в Token Harbor; запрос оплачивается с твоего баланса. Результаты сохраняются после твоего подтверждения.",11f,c.muted2,line=18f)
    Spacer(Modifier.height(18.dp));ActionButton("Готово",close,Modifier.fillMaxWidth(),primary=false)
    if(deleting)androidx.compose.material3.AlertDialog(onDismissRequest={deleting=false},title={Copy("Удалить ключ?",22f,weight=750)},text={Copy("Ручной ввод, дела и мысли продолжат работать.")},
        confirmButton={androidx.compose.material3.TextButton(onClick={scope.launch {if(remove()){key="";deleting=false}}}){androidx.compose.material3.Text("Удалить")}},dismissButton={androidx.compose.material3.TextButton(onClick={deleting=false}){androidx.compose.material3.Text("Отмена")}})
}
