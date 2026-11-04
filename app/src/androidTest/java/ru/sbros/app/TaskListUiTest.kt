package ru.sbros.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.*
import ru.sbros.app.feature.*

@RunWith(AndroidJUnit4::class)
class TaskListUiTest {
    @get:Rule val rule=createComposeRule()
    @Test fun fiftyRowsSelectionCheckboxCompletionAndSwipes() {
        var state by mutableStateOf(fixtures().copy(tasks=(1..50).map {DemoTask(it,"Дело $it","Категория",it%3,"Небольшой первый шаг")}
            +DemoTask(100,"Ожидание","",0,"","waiting")).reconcileTaskFocus())
        var token=0L
        rule.setContent {SbrosTheme("lavender",false) {Surface {
            TasksScreen(state,{}, {id->state=state.completeTask(id,++token)}, {}, {id->state=state.focusTask(id)}, {state=state.nextTask()})
        }}}
        rule.onNodeWithTag("focused-task-title",useUnmergedTree=true).assertTextEquals("Дело 1")
        rule.onNodeWithTag("tasks-list").performScrollToNode(hasTestTag("select-task-50"))
        rule.onNodeWithTag("select-task-50").performClick()
        rule.onNodeWithTag("focused-task-title",useUnmergedTree=true).assertTextEquals("Дело 50")
        rule.runOnIdle {assertEquals(49,state.remainingActions.size);assertFalse(state.remainingActions.any {it.id==50})}
        rule.onNodeWithTag("tasks-list").performScrollToNode(hasTestTag("complete-task-2"))
        rule.onNodeWithTag("complete-task-2").performClick()
        rule.runOnIdle {assertEquals(50,state.focusedAction?.id);assertEquals("done",state.tasks.first {it.id==2}.status)}
        rule.onNodeWithTag("tasks-list").performScrollToIndex(1)
        rule.onNodeWithTag("focused-task-card").performTouchInput {swipeLeft()}
        rule.onNodeWithTag("focused-task-title",useUnmergedTree=true).assertTextEquals("Дело 1")
        rule.onNodeWithTag("focused-task-card").performTouchInput {swipeRight()}
        rule.runOnIdle {assertEquals("done",state.tasks.first {it.id==1}.status);assertEquals(48,state.availableActions.size)}
        rule.runOnIdle {state=state.undoTaskCompletion(token)}
        rule.onNodeWithTag("focused-task-title",useUnmergedTree=true).assertTextEquals("Дело 1")
        rule.runOnIdle {state=state.copy(tasks=state.tasks.map {if(it.id==1)it.copy(title="Обновлённое дело")else it}+DemoTask(51,"Новое дело","",0,"")).reconcileTaskFocus()}
        rule.onNodeWithTag("focused-task-title",useUnmergedTree=true).assertTextEquals("Обновлённое дело")
        rule.onNodeWithTag("tasks-list").performScrollToNode(hasTestTag("select-task-51"))
        rule.onNodeWithTag("select-task-51").assertExists()
    }
    @Test fun emptyAndSingleTaskStates() {
        var state by mutableStateOf(fixtures().copy(tasks=emptyList()).reconcileTaskFocus())
        rule.setContent {SbrosTheme("lavender",false) {Surface {TasksScreen(state,{},{},{},{},{})}}}
        rule.onNodeWithText("На сегодня спокойно.").assertExists()
        rule.onNodeWithText("Новых дел пока нет.").assertExists()
        rule.runOnIdle {state=state.copy(tasks=listOf(DemoTask(1,"Единственное дело","",0,""))).reconcileTaskFocus()}
        rule.onNodeWithText("Осталось всего одно действие.").assertExists()
    }
    @Test fun sourceLinkIsOnlyShownWhenOriginalIsAvailable() {
        var thought by mutableStateOf(fixtures().thoughts.first())
        var opened=false
        rule.setContent {SbrosTheme("lavender",false) {Surface {ThoughtDetail(thought,{},{},{opened=it=="thought-source"},{true})}}}
        rule.onNodeWithText("Посмотреть исходный сброс").assertDoesNotExist()
        rule.runOnIdle {thought=thought.copy(sourceText="Полный исходный текст сброса")}
        rule.onNodeWithText("Посмотреть исходный сброс").performScrollTo().performClick()
        rule.runOnIdle {assertTrue(opened);assertEquals("Полный исходный текст сброса",thought.sourceText)}
    }
}

