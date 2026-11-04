package com.korimbekef.reading.ui.screens.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.korimbekef.reading.AppContainer
import com.korimbekef.reading.core.Levels
import com.korimbekef.reading.data.db.ChildEntity
import com.korimbekef.reading.ui.appViewModel
import com.korimbekef.reading.ui.components.readingTextStyle
import com.korimbekef.reading.ui.screens.AVATARS
import com.korimbekef.reading.ui.screens.reading.FontSteps
import com.korimbekef.reading.ui.screens.reading.Speeds
import com.korimbekef.reading.ui.T
import com.korimbekef.reading.ui.theme.KColors
import kotlinx.coroutines.launch

class ChildSettingsViewModel(c: AppContainer, private val childId: Long) : ParentBaseViewModel(c) {
    var loaded by mutableStateOf<ChildEntity?>(null)
        private set

    init {
        viewModelScope.launch {
            loaded = if (childId > 0) c.children.get(childId) else ChildEntity(name = "")
        }
    }

    fun save(child: ChildEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            if (child.id > 0) c.children.update(child) else c.children.create(child)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            if (childId > 0) c.children.delete(childId)
            onDone()
        }
    }

    fun resetProgress(onDone: () -> Unit) {
        viewModelScope.launch {
            if (childId > 0) c.progress.resetChild(childId)
            onDone()
        }
    }

    fun previewSpeed(rate: Float) {
        c.speech.speakMessage(T.SPEED_SAMPLE, rate)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChildSettingsScreen(childId: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "child-$childId") { ChildSettingsViewModel(it, childId) }
    val children by vm.children.collectAsStateWithLifecycle()
    val original = vm.loaded
    var draft by remember { mutableStateOf<ChildEntity?>(null) }
    LaunchedEffect(original) { if (draft == null && original != null) draft = original }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val d = draft

    ParentScaffold(title = if (childId > 0) "הגדרות הילד/ה" else "ילד/ה חדש/ה", onBack = onBack) {
        if (d == null) return@ParentScaffold
        SectionCard(title = "פרופיל") {
            OutlinedTextField(
                value = d.name,
                onValueChange = { draft = d.copy(name = it.take(20)) },
                label = { Text("שם") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !d.isGirl, onClick = { draft = d.copy(gender = ChildEntity.GENDER_BOY) }, label = { Text("בן 👦") })
                FilterChip(selected = d.isGirl, onClick = { draft = d.copy(gender = ChildEntity.GENDER_GIRL) }, label = { Text("בת 👧") })
            }
            InfoText("כך יַנְשׁוּפִי פונה לילד/ה בהודעות (״בוא״ או ״בואי״).")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AVATARS.forEach { a ->
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(if (a == d.avatar) KColors.VioletLight else Color.White, CircleShape)
                            .border(2.dp, if (a == d.avatar) KColors.Violet else KColors.Line, CircleShape)
                            .clickable { draft = d.copy(avatar = a) },
                        contentAlignment = Alignment.Center,
                    ) { Text(a, fontSize = 26.sp) }
                }
            }
        }
        SectionCard(title = "רמת קריאה") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Levels.all.forEach { info ->
                    FilterChip(
                        selected = d.level == info.number,
                        onClick = { draft = d.copy(level = info.number, maxLevel = maxOf(d.maxLevel, info.number)) },
                        label = { Text("${info.emoji} ${info.number} · ${info.name.plain}") },
                    )
                }
            }
            InfoText("הרמה הנוכחית קובעת אילו סיפורים יוצעו לילד. אפשר גם לפתוח רמות גבוהות יותר לעיון:")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { level ->
                    FilterChip(
                        selected = d.maxLevel == level,
                        enabled = level >= d.level,
                        onClick = { draft = d.copy(maxLevel = level) },
                        label = { Text(if (level == 5) "כל הרמות פתוחות" else "פתוח עד רמה $level") },
                    )
                }
            }
        }
        SectionCard(title = "תצוגה") {
            SwitchRow("ניקוד כברירת מחדל", "הילד יכול להחליף בכל רגע במסך הקריאה", d.withNikud) { draft = d.copy(withNikud = it) }
            Text("גודל גופן", style = MaterialTheme.typography.bodyLarge, color = KColors.Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                FontSteps.sizes.forEachIndexed { i, size ->
                    FilterChip(
                        selected = d.fontStep == i,
                        onClick = { draft = d.copy(fontStep = i) },
                        label = { Text("שָׁלוֹם", style = readingTextStyle((size * 0.6f).sp)) },
                    )
                }
            }
        }
        SectionCard(title = "מהירות הקראה") {
            val names = listOf("איטית מאוד", "איטית", "רגילה", "מהירה")
            val current = Speeds.nearestIndex(d.speechRate)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Speeds.rates.forEachIndexed { i, rate ->
                    FilterChip(selected = current == i, onClick = { draft = d.copy(speechRate = rate) }, label = { Text(names[i]) })
                }
            }
            OutlinedButton(onClick = { vm.previewSpeed(d.speechRate) }) { Text("🔊 השמעת דוגמה") }
        }
        SectionCard(title = "יעד יומי") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 20, 30).forEach { m ->
                    FilterChip(selected = d.dailyGoalMinutes == m, onClick = { draft = d.copy(dailyGoalMinutes = m) }, label = { Text("$m דקות") })
                }
            }
            InfoText("נספר רק זמן פעיל במסך הקריאה ובמשחקים (אחרי 2 דקות ללא פעילות הספירה נעצרת).")
        }
        SectionCard(title = "משחקיות") {
            SwitchRow(
                "כוכבים, מדליות ופרסים",
                "כיבוי מסתיר כוכבים ומדליות ומשאיר רק עידוד מילולי",
                d.gamification,
            ) { draft = d.copy(gamification = it) }
        }
        Button(
            onClick = { vm.save(d.copy(name = d.name.trim().ifEmpty { "חבר" }), onBack) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("שמירה") }
        if (childId > 0) {
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text("איפוס התקדמות") }
            if (children.size > 1) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("מחיקת הפרופיל", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("לאפס את ההתקדמות?") },
            text = { Text("כל הכוכבים, המדליות, ההיסטוריה ורשימת המילים של ${d?.name ?: ""} יימחקו. אי אפשר לבטל.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetProgress {} }) { Text("איפוס", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("ביטול") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("למחוק את הפרופיל?") },
            text = { Text("הפרופיל וכל הנתונים שלו יימחקו מהמכשיר. אי אפשר לבטל.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("מחיקה", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ביטול") } },
        )
    }
}
