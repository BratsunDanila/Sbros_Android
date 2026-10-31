package ru.sbros.app.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ru.sbros.app.core.designsystem.SbrosTheme
import ru.sbros.app.data.fixtures

@Preview(name="Сброс · 390 dp",widthDp=390,heightDp=720,showBackground=true)
@Composable private fun CapturePreview() = SbrosTheme("lavender",false) {CaptureScreen("",{},{},{},{},{})}
@Preview(name="Дела · 390 dp",widthDp=390,heightDp=720,showBackground=true)
@Composable private fun TasksPreview() = SbrosTheme("lavender",false) {TasksScreen(fixtures(),{},{},{},{},{})}
@Preview(name="Мысли · 390 dp",widthDp=390,heightDp=720,showBackground=true)
@Composable private fun ThoughtsPreview() = SbrosTheme("lavender",false) {ThoughtsScreen(fixtures(),{})}
@Preview(name="Календарь · 390 dp",widthDp=390,heightDp=720,showBackground=true)
@Composable private fun CalendarPreview() = SbrosTheme("lavender",false) {CalendarScreen(fixtures(),{},{_,_->})}
@Preview(name="Обзор · тёмный",widthDp=390,heightDp=720,showBackground=true)
@Composable private fun OverviewPreview() = SbrosTheme("lavender",true) {OverviewScreen(fixtures(),{},{})}
