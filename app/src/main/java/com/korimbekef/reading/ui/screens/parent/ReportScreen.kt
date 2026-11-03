package com.korimbekef.reading.ui.screens.parent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.korimbekef.reading.AppContainer
import com.korimbekef.reading.core.Levels
import com.korimbekef.reading.core.Passage
import com.korimbekef.reading.data.ChildSummary
import com.korimbekef.reading.data.db.ChildEntity
import com.korimbekef.reading.data.db.SessionEntity
import com.korimbekef.reading.ui.appViewModel
import com.korimbekef.reading.ui.theme.KColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ReportUi(
    val child: ChildEntity? = null,
    val summary: ChildSummary = ChildSummary(),
    val sessions: List<SessionEntity> = emptyList(),
    val passages: List<Passage> = emptyList(),
)

class ReportViewModel(c: AppContainer) : ParentBaseViewModel(c) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val report: StateFlow<ReportUi> = c.children.activeChild.flatMapLatest { child ->
        if (child == null) {
            flowOf(ReportUi())
        } else {
            combine(
                c.progress.observeSummary(child.id),
                c.progress.observeRecentSessions(child.id, 40),
                c.content.allPassages,
            ) { summary, sessions, passages -> ReportUi(child, summary, sessions, passages) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReportUi())
}

@Composable
fun ReportScreen(onBack: () -> Unit) {
    val vm = appViewModel { ReportViewModel(it) }
    val ui by vm.report.collectAsStateWithLifecycle()
    val s = ui.summary
    val child = ui.child
    ParentScaffold(title = "דוח התקדמות${child?.let { " · ${it.name}" } ?: ""}", onBack = onBack) {
        SectionCard(title = "סיכום") {
            StatLine("זמן תרגול מצטבר", formatMinutes(s.totalSeconds))
            StatLine("זמן תרגול היום", formatMinutes(s.todaySeconds))
            StatLine("רצף ימי תרגול (נוכחי / שיא)", "${s.currentStreak} / ${s.bestStreak}")
            StatLine("קטעים שהושלמו", "${s.passagesCompleted}")
            StatLine("משחקים ששוחקו", "${s.gamesPlayed}")
            HorizontalDivider()
            StatLine("שורות שהאפליקציה הקריאה", "${s.linesHeard}")
            StatLine("שורות שהילד/ה סימן/ה שקרא/ה בעצמו/ה", "${s.linesRead}")
            StatLine("מילים שהאפליקציה הקריאה (חשיפה)", "${s.wordsHeard}")
            StatLine("מילים בשורות שהילד/ה קרא/ה בעצמו/ה", "${s.wordsRead}")
            InfoText(
                "\"קרא/ה בעצמו/ה\" מבוסס על דיווח עצמי – לחיצה על \"סיימתי לקרוא\". האפליקציה אינה מקשיבה לילד ואינה מודדת דיוק קריאה, ולכן אין כאן ציון דיוק.",
            )
        }
        SectionCard(title = "דקות תרגול – 14 הימים האחרונים") {
            MinutesChart(s)
        }
        SectionCard(title = "התקדמות לפי רמות") {
            val builtIn = ui.passages.filter { !it.isCustom }
            Levels.all.forEach { info ->
                val inLevel = builtIn.filter { it.level == info.number }
                val done = inLevel.count { (s.progress[it.id]?.timesCompleted ?: 0) > 0 }
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Text("${info.emoji} רמה ${info.number} · ${info.name.plain}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = KColors.Ink)
                        Text("$done/${inLevel.size}", style = MaterialTheme.typography.bodyMedium, color = KColors.InkSoft)
                    }
                    LinearProgressIndicator(
                        progress = { if (inLevel.isEmpty()) 0f else done.toFloat() / inLevel.size },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = KColors.level(info.number),
                        trackColor = KColors.levelSoft(info.number),
                    )
                }
            }
        }
        SectionCard(title = "היסטוריית תרגול") {
            if (ui.sessions.isEmpty()) InfoText("עדיין אין תרגולים.")
            val fmt = DateTimeFormatter.ofPattern("EEE d.M HH:mm", Locale.forLanguageTag("he-IL"))
            ui.sessions.forEach { session ->
                val passage = ui.passages.firstOrNull { it.id == session.passageId }
                val date = Instant.ofEpochMilli(session.updatedAt).atZone(ZoneId.systemDefault()).format(fmt)
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(passage?.emoji ?: "📄")
                        Spacer(Modifier.width(6.dp))
                        Text(passage?.title?.plain ?: session.passageId, style = MaterialTheme.typography.bodyLarge, color = KColors.Ink, modifier = Modifier.weight(1f))
                        if (session.completedAt != null) Text("✓ הושלם", color = KColors.Green, style = MaterialTheme.typography.labelMedium)
                    }
                    InfoText("$date · ${formatMinutes(session.durationSeconds)} · הוקראו ${session.heardSet.size} שורות · נקראו לבד ${session.readSet.size} שורות")
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = KColors.Ink, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = KColors.Violet)
    }
}

@Composable
private fun MinutesChart(summary: ChildSummary) {
    val today = LocalDate.now().toEpochDay()
    val days = (13 downTo 0).map { today - it }
    val byDay = summary.days.associateBy { it.day }
    val minutes = days.map { (byDay[it]?.seconds ?: 0) / 60f }
    val max = (minutes.maxOrNull() ?: 0f).coerceAtLeast(10f)
    val fmt = DateTimeFormatter.ofPattern("d.M")
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp).background(KColors.Cream, RoundedCornerShape(12.dp)).padding(8.dp)) {
            val slot = size.width / days.size
            val barWidth = slot * 0.6f
            minutes.forEachIndexed { i, m ->
                val h = (m / max) * size.height
                // index 0 = oldest; draw newest on the right side visually in LTR canvas space,
                // mirrored for RTL by placing the newest day at the left.
                val x = size.width - (i + 1) * slot + (slot - barWidth) / 2
                drawRoundRect(
                    color = if (m >= 1f) KColors.Teal else KColors.Line,
                    topLeft = Offset(x, size.height - h.coerceAtLeast(3f)),
                    size = Size(barWidth, h.coerceAtLeast(3f)),
                    cornerRadius = CornerRadius(6f, 6f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(LocalDate.ofEpochDay(days.first()).format(fmt), style = MaterialTheme.typography.labelSmall, color = KColors.InkSoft)
            Text("היום", style = MaterialTheme.typography.labelSmall, color = KColors.InkSoft)
        }
        InfoText("סה״כ ב-14 הימים האחרונים: ${minutes.sum().toInt()} דקות")
    }
}
