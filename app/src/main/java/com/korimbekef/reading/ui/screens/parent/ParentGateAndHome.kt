package com.korimbekef.reading.ui.screens.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.korimbekef.reading.AppContainer
import com.korimbekef.reading.core.LevelAdvisor
import com.korimbekef.reading.core.Levels
import com.korimbekef.reading.core.Passage
import com.korimbekef.reading.data.ChildSummary
import com.korimbekef.reading.data.db.ChildEntity
import com.korimbekef.reading.ui.appViewModel
import com.korimbekef.reading.ui.components.Owl
import com.korimbekef.reading.ui.components.OwlMood
import com.korimbekef.reading.ui.components.PinPad
import com.korimbekef.reading.ui.theme.KColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

// ---------------------------------------------------------------- PIN gate

class ParentGateViewModel(c: AppContainer) : ParentBaseViewModel(c)

private enum class GateStep { ENTER, CHALLENGE, NEW_PIN, CONFIRM }

@Composable
fun ParentGateScreen(onBack: () -> Unit, onUnlocked: () -> Unit) {
    val vm = appViewModel { ParentGateViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(GateStep.ENTER) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorKey by remember { mutableIntStateOf(0) }
    var newPin by rememberSaveable { mutableStateOf("") }
    val a = rememberSaveable { Random.nextInt(12, 20) }
    val b = rememberSaveable { Random.nextInt(3, 10) }
    var answer by rememberSaveable { mutableStateOf("") }

    val effectiveStep = if (!settings.hasPin && step == GateStep.ENTER) GateStep.NEW_PIN else step

    Column(
        Modifier
            .fillMaxSize()
            .background(KColors.Cream)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "חזרה") }
        }
        Owl(mood = OwlMood.IDLE, size = 100.dp)
        Spacer(Modifier.height(8.dp))
        when (effectiveStep) {
            GateStep.ENTER -> {
                PinPad(
                    title = "אזור ההורים",
                    subtitle = "הקישו את קוד ההורים",
                    error = error,
                    errorKey = errorKey,
                    onComplete = { pin ->
                        scope.launch(Dispatchers.Main.immediate) {
                            if (vm.pinMatches(pin)) {
                                onUnlocked()
                            } else {
                                error = "קוד שגוי, נסו שוב"
                                errorKey++
                            }
                        }
                    },
                )
                TextButton(onClick = { step = GateStep.CHALLENGE; error = null }) { Text("שכחתי את הקוד") }
            }
            GateStep.CHALLENGE -> {
                Text("איפוס קוד ההורים", style = MaterialTheme.typography.headlineSmall, color = KColors.Ink)
                Spacer(Modifier.height(8.dp))
                Text("כדי לוודא שמבוגר כאן, פתרו את התרגיל:", color = KColors.InkSoft, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text("$a × $b = ?", fontSize = 36.sp, color = KColors.Ink)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { v -> answer = v.filter { it.isDigit() }.take(4) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    label = { Text("התשובה") },
                )
                Spacer(Modifier.height(8.dp))
                Text(error ?: " ", color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    if (answer.toIntOrNull() == a * b) {
                        step = GateStep.NEW_PIN
                        error = null
                    } else {
                        error = "התשובה לא נכונה"
                    }
                }) { Text("אישור") }
                TextButton(onClick = { step = GateStep.ENTER; error = null }) { Text("ביטול") }
            }
            GateStep.NEW_PIN -> PinPad(
                title = "בחרו קוד הורים חדש",
                subtitle = "4 ספרות",
                error = error,
                errorKey = errorKey,
                resetKey = "new",
                onComplete = { pin ->
                    newPin = pin
                    step = GateStep.CONFIRM
                    error = null
                },
            )
            GateStep.CONFIRM -> PinPad(
                title = "הקישו שוב את הקוד החדש",
                error = error,
                errorKey = errorKey,
                resetKey = "confirm",
                onComplete = { pin ->
                    if (pin == newPin) {
                        scope.launch(Dispatchers.Main.immediate) {
                            vm.savePin(pin)
                            onUnlocked()
                        }
                    } else {
                        error = "הקודים לא תואמים, נסו שוב"
                        errorKey++
                        step = GateStep.NEW_PIN
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------- parent home

data class ParentHomeUi(
    val child: ChildEntity? = null,
    val summary: ChildSummary = ChildSummary(),
    val recommendation: Int? = null,
    val completedInLevel: Int = 0,
    val totalInLevel: Int = 0,
)

class ParentHomeViewModel(c: AppContainer) : ParentBaseViewModel(c) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val home: StateFlow<ParentHomeUi> = c.children.activeChild.flatMapLatest { child ->
        if (child == null) {
            flowOf(ParentHomeUi())
        } else {
            combine(c.progress.observeSummary(child.id), c.content.visiblePassages) { summary, passages ->
                val inLevel = passages.filter { it.level == child.level && !it.isCustom }
                val done = inLevel.count { (summary.progress[it.id]?.timesCompleted ?: 0) > 0 }
                ParentHomeUi(child, summary, LevelAdvisor.recommendedLevel(child.level, done, inLevel.size), done, inLevel.size)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ParentHomeUi())

    fun select(id: Long) {
        viewModelScope.launch { c.children.setActive(id) }
    }

    fun approveLevel(level: Int) {
        val child = home.value.child ?: return
        viewModelScope.launch { c.children.update(child.copy(level = level, maxLevel = maxOf(child.maxLevel, level))) }
    }
}

@Composable
fun ParentHomeScreen(
    onExit: () -> Unit,
    onReport: () -> Unit,
    onChild: (Long) -> Unit,
    onVoice: () -> Unit,
    onContent: () -> Unit,
    onWords: () -> Unit,
    onPin: () -> Unit,
    onAbout: () -> Unit,
    onErase: () -> Unit,
) {
    val vm = appViewModel { ParentHomeViewModel(it) }
    val children by vm.children.collectAsStateWithLifecycle()
    val ui by vm.home.collectAsStateWithLifecycle()
    val child = ui.child

    ParentScaffold(
        title = "אזור ההורים",
        onBack = onExit,
        actions = {
            IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = "יציאה") }
        },
    ) {
        // child selector
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            children.forEach { ch ->
                val selected = ch.id == child?.id
                Row(
                    Modifier
                        .background(if (selected) KColors.Violet else Color.White, RoundedCornerShape(50))
                        .border(2.dp, KColors.Violet, RoundedCornerShape(50))
                        .clickable { vm.select(ch.id) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ch.avatar, fontSize = 20.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(ch.name, color = if (selected) Color.White else KColors.Ink, style = MaterialTheme.typography.titleSmall)
                }
            }
            Row(
                Modifier
                    .background(Color.White, RoundedCornerShape(50))
                    .border(2.dp, KColors.Line, RoundedCornerShape(50))
                    .clickable { onChild(-1L) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = KColors.Ink)
                Text("הוספת ילד/ה", color = KColors.Ink, style = MaterialTheme.typography.titleSmall)
            }
        }

        if (child != null) {
            SectionCard(title = "${child.avatar} ${child.name} · רמה ${child.level} (${Levels.info(child.level).name.plain})") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MiniStat("היום", "${ui.summary.todaySeconds / 60}/${child.dailyGoalMinutes} דק׳")
                    MiniStat("רצף ימים", "${ui.summary.currentStreak}")
                    MiniStat("סיפורים", "${ui.summary.passagesCompleted}")
                    MiniStat("סה״כ זמן", formatMinutes(ui.summary.totalSeconds))
                }
                InfoText("ברמה הנוכחית הושלמו ${ui.completedInLevel} מתוך ${ui.totalInLevel} קטעים.")
                val rec = ui.recommendation
                if (rec != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(KColors.GreenLight, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                    ) {
                        Text("🌟 המלצה: לעבור לרמה $rec (${Levels.info(rec).name.plain})", style = MaterialTheme.typography.titleSmall, color = KColors.Ink)
                        InfoText("הילד/ה השלים/ה את רוב הקטעים ברמה הנוכחית. המעבר לא קורה לבד – אתם מחליטים.")
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { vm.approveLevel(rec) }) { Text("אישור מעבר לרמה $rec") }
                    }
                }
            }
        }

        SectionCard {
            MenuRow(Icons.Rounded.BarChart, "דוח התקדמות", "זמן תרגול, קטעים שהושלמו, היסטוריה", onReport)
            MenuRow(Icons.Rounded.Tune, "הגדרות הילד/ה", "רמה, ניקוד, גודל גופן, יעד יומי, פרסים", { child?.let { onChild(it.id) } })
            MenuRow(Icons.Rounded.RecordVoiceOver, "קול והקראה", "בחירת קול, מהירות, גובה, סנכרון הדגשה", onVoice)
            MenuRow(Icons.Rounded.LibraryBooks, "קטעי קריאה", "הוספת טקסט משלכם, הסתרת קטעים", onContent)
            MenuRow(Icons.Rounded.Spellcheck, "מילים לתרגול", "סימון מילים שכדאי לתרגל במשחקים", onWords)
            MenuRow(Icons.Rounded.Password, "קוד הורים", "שינוי הקוד", onPin)
            MenuRow(Icons.Rounded.Info, "פרטיות ומידע", "איך האפליקציה עובדת ומה היא לא אוספת", onAbout)
        }
        SectionCard {
            MenuRow(Icons.Rounded.DeleteForever, "מחיקת כל הנתונים", "כל הפרופילים, ההתקדמות וההגדרות – התחלה מחדש", onErase, danger = true)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = KColors.Ink)
        Text(label, style = MaterialTheme.typography.labelSmall, color = KColors.InkSoft)
    }
}

@Composable
fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, danger: Boolean = false) {
    val accent = if (danger) MaterialTheme.colorScheme.error else KColors.Violet
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).background(if (danger) accent.copy(alpha = 0.12f) else KColors.VioletLight, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (danger) accent else KColors.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KColors.InkSoft)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = KColors.InkSoft)
    }
}

/** Helper for screens that list passages by level. */
fun groupByLevel(passages: List<Passage>): List<Pair<String, List<Passage>>> {
    val builtIn = (1..5).map { level -> "רמה $level · ${Levels.info(level).name.plain}" to passages.filter { !it.isCustom && it.level == level } }
    val custom = passages.filter { it.isCustom }
    return builtIn.filter { it.second.isNotEmpty() } + if (custom.isNotEmpty()) listOf("הקטעים שלכם" to custom) else emptyList()
}
