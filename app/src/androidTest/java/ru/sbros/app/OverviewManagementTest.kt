package ru.sbros.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverviewManagementTest {
    @get:Rule(order=0) val fixtures=DemoTestRule()
    @get:Rule(order=1) val rule=createAndroidComposeRule<MainActivity>()
    private fun tab(name: String){rule.onNodeWithContentDescription("Вкладка $name").performClick()}
    private fun enter() {
        rule.waitUntil(10_000){rule.onAllNodesWithText("Начать без аккаунта").fetchSemanticsNodes().isNotEmpty()||rule.onAllNodesWithContentDescription("Вкладка Сброс").fetchSemanticsNodes().isNotEmpty()}
        if(rule.onAllNodesWithText("Начать без аккаунта").fetchSemanticsNodes().isNotEmpty())rule.onNodeWithText("Начать без аккаунта").performClick()
        rule.waitUntil(10_000){rule.onAllNodesWithContentDescription("Вкладка Обзор").fetchSemanticsNodes().isNotEmpty()}
        tab("Обзор")
    }
    private fun hideKeyboard(){rule.activityRule.scenario.onActivity {WindowCompat.getInsetsController(it.window,it.window.decorView).hide(WindowInsetsCompat.Type.ime())};rule.waitForIdle()}
    private fun save(){
        hideKeyboard();rule.onNodeWithTag("editor-save").performScrollTo().performClick()
        rule.waitUntil(5_000){rule.onAllNodesWithTag("editor-save").fetchSemanticsNodes().isEmpty()}
    }
    private fun shot(name: String) {
        hideKeyboard();rule.onAllNodes(isRoot()).onLast().captureToImage();android.os.SystemClock.sleep(400)
        val dir=File(rule.activity.getExternalFilesDir(null),"detail-screenshots").apply {mkdirs()}
        File(dir,"$name.png").outputStream().use {InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun projectNavigationEditingAndActionsThenTaskCardAndArrowEditors() {
        enter()
        rule.onNodeWithTag("overview-projects").performClick()
        rule.onNodeWithTag("collection-screen-projects").assertExists()
        rule.onNodeWithContentDescription("Вкладка Обзор").assertIsSelected()
        shot("projects-screen")
        rule.onNodeWithTag("project-row-1").performClick()
        rule.onNodeWithTag("project-screen").assertExists();shot("project-detail")
        rule.onNodeWithContentDescription("Изменить проект").performClick();shot("project-editor")
        rule.onNodeWithTag("project-title-input").performTextReplacement("Курсовая — готовый результат")
        rule.onNodeWithTag("project-outcome-input").performTextReplacement("Работа одобрена")
        save()
        rule.onNodeWithText("Курсовая — готовый результат").assertExists()
        rule.onNodeWithContentDescription("Добавить").performClick()
        shot("new-entry")
        rule.onNodeWithTag("new-title-input").performTextInput("Добавленное дело проекта")
        save()
        rule.onNodeWithTag("project-actions-list").performScrollToNode(hasTestTag("management-edit-task-7"))
        rule.onNodeWithTag("management-edit-task-7").performClick()
        rule.onNodeWithTag("task-title-input").assertTextContains("Добавленное дело проекта")
        rule.onNodeWithTag("task-status-input").performClick()
        rule.onNodeWithContentDescription("Выбрать Жду ответа").performClick()
        save()
        rule.onNodeWithTag("management-edit-task-7").performClick()
        rule.onNodeWithTag("task-status-input").assertTextContains("Жду ответа")
        rule.onNodeWithTag("editor-cancel").performScrollTo().performClick()
        rule.activityRule.scenario.onActivity {it.onBackPressedDispatcher.onBackPressed()}
        rule.onNodeWithTag("collection-screen-projects").assertExists()
        rule.onNodeWithTag("project-row-1").assertTextContains("4 ",substring=true)
        tab("Дела")
        rule.onNodeWithTag("focused-task-card").performClick();shot("task-editor-from-card")
        rule.onNodeWithTag("task-title-input").performTextReplacement("Исправленное дело")
        save()
        rule.onNodeWithTag("focused-task-title",useUnmergedTree=true).assertTextEquals("Исправленное дело")
        rule.onNodeWithTag("tasks-list").performScrollToNode(hasTestTag("edit-task-2"))
        rule.onNodeWithTag("edit-task-2").performClick();shot("task-editor-from-arrow")
        rule.onNodeWithTag("task-title-input").assertTextContains("Купить корм коту по дороге домой")
        rule.onNodeWithTag("editor-cancel").performScrollTo().performClick()
        rule.onNodeWithTag("tasks-list").performScrollToIndex(1)
        rule.onNodeWithTag("focused-task-title",useUnmergedTree=true).assertTextEquals("Исправленное дело")
        rule.onNodeWithText("Другое").performClick()
        rule.onNodeWithTag("task-title-input").assertDoesNotExist()
    }
    @Test fun overviewCollectionsArePagesAndMaterialsAreEditable() {
        enter()
        for((key,label) in listOf("waiting" to "Ожидаю","someday" to "Когда-нибудь","next" to "Все действия","done" to "Сделано")) {
            rule.onNodeWithText(label).performScrollTo().performClick()
            rule.onNodeWithTag("collection-screen-$key").assertExists()
            rule.onNodeWithContentDescription("Вкладка Обзор").assertIsSelected()
            shot("collection-$key")
            rule.activityRule.scenario.onActivity {it.onBackPressedDispatcher.onBackPressed()}
        }
        rule.onNodeWithTag("overview-notes").performScrollTo().performClick()
        rule.onNodeWithTag("collection-screen-notes").assertExists();shot("materials-screen")
        rule.onNodeWithTag("material-row-1").performClick();shot("material-editor")
        rule.onNodeWithTag("material-title-input").performTextReplacement("Исправленный материал")
        rule.onNodeWithTag("material-body-input").performTextReplacement("Полный текст\nсохранён")
        save()
        rule.onNodeWithText("Исправленный материал").assertExists()
        rule.onNodeWithTag("material-row-1").performClick()
        rule.onNodeWithTag("material-body-input").assertTextContains("Полный текст\nсохранён")
        rule.onNodeWithTag("editor-cancel").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Добавить").performClick()
        rule.onNodeWithTag("new-title-input").performTextInput("Новый материал")
        save()
        rule.onNodeWithTag("material-row-2").performClick()
        rule.onNodeWithText("Удалить материал").performScrollTo().performClick()
        rule.waitUntil(5_000){rule.onAllNodesWithTag("material-row-2").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithTag("material-row-2").assertDoesNotExist()
        rule.waitUntil(5_000){rule.onAllNodesWithText("Отменить").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Отменить").performClick()
        rule.waitUntil(5_000){rule.onAllNodesWithTag("material-row-2").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("material-row-2").assertExists()
        tab("Обзор")
        rule.onNodeWithTag("overview-notes").assertTextContains("2")
    }
    @Test fun projectDeletionKeepsTasksAndCanBeUndone() {
        enter();rule.onNodeWithTag("overview-projects").performClick();rule.onNodeWithTag("project-row-1").performClick()
        rule.onNodeWithContentDescription("Изменить проект").performClick()
        rule.onNodeWithText("Удалить проект").performScrollTo().performClick()
        rule.onNodeWithText("Удалить").performClick()
        rule.onNodeWithTag("collection-screen-projects").assertExists()
        rule.waitUntil(5_000){rule.onAllNodesWithTag("project-row-1").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithTag("project-row-1").assertDoesNotExist()
        rule.waitUntil(5_000){rule.onAllNodesWithText("Отменить").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Отменить").performClick()
        rule.waitUntil(5_000){rule.onAllNodesWithTag("project-row-1").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("project-row-1").assertExists()
    }
}
