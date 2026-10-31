package ru.sbros.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class VisualSmokeTest {
    @get:Rule(order=0) val fixtures=DemoTestRule()
    @get:Rule(order=1) val rule=createAndroidComposeRule<MainActivity>()
    private fun tab(name: String) { rule.onNodeWithContentDescription("Вкладка $name").performClick();rule.waitForIdle() }
    private fun shot(name: String) {
        rule.waitForIdle()
        rule.onAllNodes(isRoot()).onLast().captureToImage()
        android.os.SystemClock.sleep(400)
        val dir=File(rule.activity.getExternalFilesDir(null),"screenshots").apply {mkdirs()}
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        if(name=="06-dark-overview")org.junit.Assert.assertEquals("Actual screenshot must use the dark palette",android.graphics.Color.rgb(18,17,24),bitmap.getPixel(0,120))
    }
    private fun changeTheme(from: String,to: String) {
        rule.onNodeWithText(from).performScrollTo().performClick()
        rule.waitUntil(5_000) {rule.onAllNodesWithText(to).fetchSemanticsNodes().isNotEmpty()}
    }
    private fun enter() {
        rule.waitUntil(10_000) {rule.onAllNodesWithText("Начать без аккаунта").fetchSemanticsNodes().isNotEmpty()||rule.onAllNodesWithContentDescription("Вкладка Сброс").fetchSemanticsNodes().isNotEmpty()}
        if(rule.onAllNodesWithText("Начать без аккаунта").fetchSemanticsNodes().isNotEmpty()) {
            shot("00-welcome");rule.onNodeWithText("Начать без аккаунта").performClick()
        }
        rule.waitUntil(10_000) {rule.onAllNodesWithContentDescription("Вкладка Сброс").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("Вкладка Сброс").performClick()
        rule.onNodeWithText("Что у тебя").assertExists()
    }
    @Test fun a_visualScreens() {
        enter();shot("03-capture")
        val tops=mutableListOf<Float>()
        tab("Дела");tops+=rule.onNodeWithTag("screen-title-Дела").fetchSemanticsNode().boundsInRoot.top;shot("01-tasks")
        tab("Мысли");tops+=rule.onNodeWithTag("screen-title-Мысли").fetchSemanticsNode().boundsInRoot.top;rule.onNodeWithText("Новая мысль").assertDoesNotExist();shot("02-thoughts")
        rule.onNodeWithText("Про маленькие паузы").performClick();shot("thought-detail")
        rule.activityRule.scenario.onActivity {it.onBackPressedDispatcher.onBackPressed()}
        tab("Календарь");tops+=rule.onNodeWithTag("screen-title-Календарь").fetchSemanticsNode().boundsInRoot.top;shot("04-calendar")
        tab("Обзор");tops+=rule.onNodeWithTag("screen-title-Обзор").fetchSemanticsNode().boundsInRoot.top;shot("05-overview")
        org.junit.Assert.assertTrue("Header top positions must match: $tops",tops.max()-tops.min()<1f)
        rule.onNodeWithContentDescription("Настройки").performClick()
        changeTheme("Тёмная тема","Светлая тема");rule.onNodeWithText("Готово").performScrollTo().performClick()
        rule.waitUntil(5000) {rule.onAllNodesWithText("Твой Сброс").fetchSemanticsNodes().isEmpty()};shot("06-dark-overview")
        rule.onNodeWithContentDescription("Настройки").performClick();changeTheme("Светлая тема","Тёмная тема");rule.onNodeWithText("Готово").performScrollTo().performClick()
        listOf("sage","clay","ocean","lavender").forEach {name ->
            rule.onNodeWithContentDescription("Настройки").performClick();rule.onNodeWithContentDescription("Палитра $name").performScrollTo().performClick()
            rule.waitUntil(5_000) {rule.onNodeWithContentDescription("Палитра $name").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)==true}
            rule.onNodeWithText("Готово").performScrollTo().performClick()
            rule.waitUntil(5000) {rule.onAllNodesWithText("Твой Сброс").fetchSemanticsNodes().isEmpty()};shot("palette-$name")
        }
    }
    @Test fun b_localInteractions() {
        enter()
        rule.onNodeWithContentDescription("Текст сброса").performTextInput("Текст для проверки поворота")
        rule.activityRule.scenario.recreate()
        rule.onNodeWithContentDescription("Текст сброса").assertTextContains("Текст для проверки поворота")
        rule.onNodeWithText("Разобрать",useUnmergedTree=true).performScrollTo().performClick()
        rule.onNodeWithText("Не настроен ИИ").assertExists()
        rule.onNodeWithText("Закрыть").performClick()
        rule.onNodeWithContentDescription("Текст сброса").performTextClearance()
        rule.activityRule.scenario.onActivity {androidx.core.view.WindowCompat.getInsetsController(it.window,it.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())}
        tab("Дела")
        rule.onNodeWithText("Просто").assertDoesNotExist();rule.onNodeWithText("Средне").assertDoesNotExist()
        val navTop=rule.onNodeWithContentDescription("Вкладка Дела").fetchSemanticsNode().boundsInRoot.top
        rule.onNodeWithTag("tasks-list").performScrollToNode(hasTestTag("select-task-3"))
        org.junit.Assert.assertEquals(navTop,rule.onNodeWithContentDescription("Вкладка Дела").fetchSemanticsNode().boundsInRoot.top,.1f)
        rule.onNodeWithTag("select-task-3").performClick()
        rule.onNodeWithTag("focused-task-title",useUnmergedTree=true).assertTextEquals("Набросать план введения для курсовой")
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("focused-task-title",useUnmergedTree=true).assertTextEquals("Набросать план введения для курсовой")
        rule.onNodeWithText("Готово").performScrollTo().performClick()
        rule.waitUntil(5_000){rule.onAllNodesWithText("Отменить").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Отменить").performClick()
        rule.onNodeWithTag("focused-task-title",useUnmergedTree=true).assertTextEquals("Набросать план введения для курсовой")
        tab("Мысли")
        rule.onNodeWithContentDescription("Поиск по мыслям").performClick()
        rule.onNodeWithContentDescription("Поиск мыслей").performTextInput("паузы")
        rule.onNodeWithText("Про маленькие паузы").performScrollTo().performClick();rule.onNodeWithText("Продолжить мысль").assertExists()
        rule.activityRule.scenario.onActivity {it.onBackPressedDispatcher.onBackPressed()}
        tab("Календарь")
        rule.onNodeWithContentDescription("Следующий месяц").performClick();rule.onNodeWithContentDescription("Предыдущий месяц").performClick()
        tab("Обзор")
        rule.onNodeWithContentDescription("Настройки").performClick()
        changeTheme("Тёмная тема","Светлая тема");rule.onNodeWithText("Готово").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Настройки").performClick();changeTheme("Светлая тема","Тёмная тема");rule.onNodeWithText("Готово").performScrollTo().performClick()
    }
}

