package ru.sbros.app.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import ru.sbros.app.R
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.auth.AuthState

@Composable fun PrivateField(value: String,change: (String)->Unit,label: String,tag: String,showToggle: Boolean=true) {
    val c=LocalSbrosColors.current;var visible by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().padding(top=16.dp)) {
        Copy(label.uppercase(),11f,c.muted2,750);Spacer(Modifier.height(7.dp))
        BasicTextField(value,change,Modifier.fillMaxWidth().testTag(tag).semantics {contentDescription=label;password()}
            .background(c.background,RoundedCornerShape(15.dp)).border(1.dp,c.border,RoundedCornerShape(15.dp)).padding(13.dp),
            textStyle=TextStyle(fontFamily=Onest,fontSize=13.sp,color=c.ink),cursorBrush=SolidColor(c.primary),singleLine=true,
            visualTransformation=if(visible&&showToggle)VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false))
        if(showToggle)QuietAction(if(visible)"Скрыть пароль" else "Показать пароль",R.drawable.ic_more){visible=!visible}
    }
}

@Composable fun AuthScreen(register: Boolean,state: AuthState,configured: Boolean,back: ()->Unit,switch: ()->Unit,local: ()->Unit,submit: suspend (String,String,String)->Boolean) {
    val c=LocalSbrosColors.current;val scope=rememberCoroutineScope()
    // Passwords are intentionally not rememberSaveable / SavedStateHandle values.
    var name by remember(register){mutableStateOf("")};var email by remember(register){mutableStateOf("")};var password by remember(register){mutableStateOf("")}
    var submitted by remember(register){mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=18.dp).testTag("auth-screen")) {
        QuietAction("Назад",R.drawable.ic_left,c.muted2, onClick={if(!state.busy&&!submitted)back()})
        Spacer(Modifier.height(35.dp));Copy(if(register)"Твоё пространство." else "С возвращением.",39f,weight=760,line=43f,tracking=-2.4f)
        Spacer(Modifier.height(12.dp));Copy("Аккаунт нужен для облачного хранения. Без него приложение тоже работает.",13f,c.muted2,line=21f)
        Spacer(Modifier.height(20.dp))
        if(!configured){Copy("Облако пока не настроено. Можно начать без аккаунта.",13f,c.muted2,line=21f)}
        if(register)ReferenceField(name,{name=it.take(120)},"Имя","auth-name")
        ReferenceField(email,{email=it.take(254)},"Email","auth-email")
        PrivateField(password,{password=it.take(1024)},"Пароль","auth-password")
        val message=error?:state.error
        if(message!=null){Spacer(Modifier.height(10.dp));Copy(message,12f,c.muted2,line=19f)}
        Spacer(Modifier.height(20.dp));ActionButton(if(submitted||state.busy)"Подождём немного…" else if(register)"Создать аккаунт" else "Войти",{
            if(!submitted){
                if(email.isBlank()||password.length<8||(register&&name.isBlank()))error="Заполни поля. Пароль — от 8 символов."
                else {submitted=true;error=null;scope.launch {try {val success=submit(name,email,password);if(success)password=""}catch(e: Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error="Не удалось завершить вход. Проверь интернет и свободное место, затем повтори."}finally{submitted=false}}}
            }
        },Modifier.fillMaxWidth().testTag("auth-submit"),enabled=configured&&!submitted&&!state.busy)
        Spacer(Modifier.height(12.dp));ActionButton(if(register)"У меня уже есть аккаунт" else "Создать аккаунт",switch,Modifier.fillMaxWidth(),primary=false,enabled=!submitted&&!state.busy)
        Spacer(Modifier.height(12.dp));QuietAction("Продолжить без аккаунта",R.drawable.ic_arrowright,c.muted2,onClick={if(!state.busy&&!submitted)local()})
        Spacer(Modifier.height(24.dp));Copy("В учебном прототипе подтверждение почты и восстановление пароля по email не используются.",10f,c.muted,line=16f)
    }
}
