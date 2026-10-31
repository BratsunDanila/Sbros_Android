package ru.sbros.app.feature

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.*
import ru.sbros.app.R
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.*

internal data class TaskDisplayGroup(val project: DemoProject?, val tasks: List<DemoTask>) {
    val key: String get() = project?.let { "project-${it.id}" } ?: "task-${tasks.first().id}"
}

internal fun projectTaskGroups(tasks: List<DemoTask>, projects: List<DemoProject>): List<TaskDisplayGroup> {
    val byProject = tasks.filter { it.projectId != null }.groupBy { it.projectId }
    val projectsById = projects.associateBy { it.id }
    val seen = mutableSetOf<Int>()
    return buildList {
        tasks.forEach { task ->
            val project = projectsById[task.projectId]
            val siblings = byProject[task.projectId].orEmpty()
            if (project != null && siblings.size > 1) {
                if (seen.add(project.id)) add(TaskDisplayGroup(project, siblings))
            } else add(TaskDisplayGroup(null, listOf(task)))
        }
    }
}

@Composable internal fun ProjectTaskGroup(group: TaskDisplayGroup, content: @Composable (DemoTask) -> Unit) {
    val project = requireNotNull(group.project)
    val c = LocalSbrosColors.current
    var expanded by rememberSaveable(project.id) { mutableStateOf(false) }
    Paper(Modifier.fillMaxWidth(), 20.dp) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 15.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Glyph(R.drawable.ic_folder, 19.dp)
                Column(Modifier.weight(1f)) {
                    Copy(project.title, 13f, weight = 750, line = 18f)
                    Spacer(Modifier.height(3.dp))
                    Copy("Шагов · ${group.tasks.size}", 10.5f, c.muted2)
                }
                Box(Modifier.rotate(if (expanded) 90f else 0f)) { Glyph(R.drawable.ic_chevron, 16.dp, c.muted) }
            }
            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.fillMaxWidth().background(c.soft).padding(start=12.dp,end=12.dp,top=6.dp,bottom=10.dp),
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    group.tasks.forEach { task ->
                        key(task.id) {
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(c.card)) {
                                content(task)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun TaskRowSurface(modifier: Modifier, radius: Dp, embedded: Boolean,
    content: @Composable BoxScope.() -> Unit) {
    if (embedded) Box(modifier, content = content) else Paper(modifier, radius, content = content)
}
