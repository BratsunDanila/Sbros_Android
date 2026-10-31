package ru.sbros.app

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import ru.sbros.app.data.Appearance
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocalFirstUiTest {
    private val graph get()=(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SbrosApplication).graph
    @get:Rule(order=0) val localSetup=object: ExternalResource(){override fun before(){
        Assume.assumeTrue("Requires sbros_isolated=true on an isolated emulator",InstrumentationRegistry.getArguments().getString("sbros_isolated")=="true"&&(Build.HARDWARE in listOf("ranchu","goldfish")||Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("Emulator",ignoreCase=true)||Build.MODEL.contains("sdk",ignoreCase=true)))
        runBlocking{
            graph.settings.mode("local");graph.settings.update(Appearance(welcomed=false))
            graph.data.change("local"){s->s.copy(tasks=s.tasks.filterNot{it.title=="Локальное дело этапа 2"},thoughts=s.thoughts.filterNot{it.title=="Моя дневниковая запись"},captures=s.captures.filterNot{it.text=="Моя дневниковая запись\nПолный исходный текст"})}
        }
    }}
    @get:Rule(order=1) val rule=createAndroidComposeRule<MainActivity>()
    private fun tab(name: String){rule.onNodeWithContentDescription("Вкладка $name").performClick()}
    private fun hideKeyboard(){rule.activityRule.scenario.onActivity {WindowCompat.getInsetsController(it.window,it.window.decorView).hide(WindowInsetsCompat.Type.ime())};rule.waitForIdle()}
    private fun shot(name: String){hideKeyboard();rule.onAllNodes(isRoot()).onLast().captureToImage();android.os.SystemClock.sleep(400);val folder=File(rule.activity.getExternalFilesDir(null),"stage2-screenshots").apply{mkdirs()};File(folder,"$name.png").outputStream().use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun guestCreationPersistentOriginalManualThoughtAndByokRemoval() {
        rule.waitUntil(10_000){rule.onAllNodesWithText("Начать без аккаунта").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Создать аккаунт").assertExists();rule.onNodeWithText("У меня уже есть аккаунт").assertExists();shot("00-welcome")
        rule.onNodeWithText("Начать без аккаунта").performScrollTo().performClick()
        rule.waitUntil(10_000){rule.onAllNodesWithContentDescription("Вкладка Обзор").fetchSemanticsNodes().isNotEmpty()}
        tab("Обзор");rule.onNodeWithText("Все действия").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Добавить").performClick()
        rule.onNodeWithTag("new-title-input").performTextInput("Локальное дело этапа 2")
        hideKeyboard();rule.onNodeWithTag("editor-save").performScrollTo().performClick()
        rule.waitUntil(5_000){rule.onAllNodesWithTag("editor-save").fetchSemanticsNodes().isEmpty()}
        tab("Сброс");rule.onNodeWithContentDescription("Текст сброса").performTextReplacement("Моя дневниковая запись\nПолный исходный текст")
        hideKeyboard();rule.onNodeWithText("На потом").performScrollTo().performClick()
        rule.waitUntil(5_000){runBlocking{graph.data.read("local").data.captures.any{it.text=="Моя дневниковая запись\nПолный исходный текст"}}}
        tab("Обзор");rule.onNodeWithText("Посмотреть входящие").performScrollTo().performClick()
        rule.onNodeWithText("В мысли").performClick()
        rule.waitUntil(5_000){runBlocking{graph.data.read("local").data.thoughts.any{it.title=="Моя дневниковая запись"}}}
        rule.onNodeWithText("Закрыть").performScrollTo().performClick()
        tab("Мысли");rule.onNodeWithText("Моя дневниковая запись").assertExists();shot("01-local-thoughts")
        tab("Сброс");rule.onNodeWithContentDescription("Настройки").performClick();shot("02-settings-account")
        rule.onNodeWithText("Настроить ИИ").performScrollTo().performClick();shot("03-ai-no-key")
        rule.onNodeWithTag("ai-key-input").performTextInput("thk_ui_fixture_1234567890")
        hideKeyboard();rule.onNodeWithTag("ai-save-key").performScrollTo().performClick()
        rule.waitUntil(5_000){graph.ai.state.value.hasKey};rule.onNodeWithTag("ai-key-input").assertTextEquals("")
        shot("04-ai-key-masked")
        rule.onNodeWithText("Удалить ключ").performScrollTo().performClick();rule.onNodeWithText("Удалить").performClick()
        rule.waitUntil(5_000){!graph.ai.state.value.hasKey}
        rule.onNodeWithText("Готово").performScrollTo().performClick();rule.onNodeWithText("Готово").performScrollTo().performClick()
        val stored=runBlocking{graph.data.read("local").data}
        Assert.assertTrue(stored.tasks.any{it.title=="Локальное дело этапа 2"})
        val thought=stored.thoughts.first{it.title=="Моя дневниковая запись"}
        Assert.assertEquals(thought.body,thought.sourceText);Assert.assertNotNull(thought.captureId)
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10_000){rule.onAllNodesWithContentDescription("Вкладка Дела").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Начать без аккаунта").assertDoesNotExist()
        tab("Дела");shot("05-local-tasks")
        tab("Обзор");rule.onNodeWithText("История сбросов").performScrollTo().performClick();shot("06-history")
        rule.onNodeWithText("Закрыть").performScrollTo().performClick()
        rule.onNodeWithTag("screen-title-Обзор").performScrollTo()
        rule.onNodeWithContentDescription("Настройки").performClick();rule.onNodeWithText("Войти").performScrollTo().performClick();shot("07-login-unconfigured")
        rule.onNodeWithText("Создать аккаунт").performScrollTo().performClick();shot("08-registration")
        rule.onNodeWithText("Продолжить без аккаунта").performScrollTo().performClick()
    }
}
