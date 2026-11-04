package com.korimbekef.reading.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.korimbekef.reading.AppContainer
import com.korimbekef.reading.core.MascotStage
import com.korimbekef.reading.core.Passage
import com.korimbekef.reading.data.ChildSummary
import com.korimbekef.reading.data.db.ChildEntity
import com.korimbekef.reading.data.db.PassageProgressEntity
import com.korimbekef.reading.data.db.SessionEntity
import com.korimbekef.reading.ui.LocalGirl
import com.korimbekef.reading.ui.T
import com.korimbekef.reading.ui.appViewModel
import com.korimbekef.reading.ui.components.BigButton
import com.korimbekef.reading.ui.components.EmojiTile
import com.korimbekef.reading.ui.components.GoalRing
import com.korimbekef.reading.ui.components.Owl
import com.korimbekef.reading.ui.components.OwlMood
import com.korimbekef.reading.ui.components.RoundIconButton
import com.korimbekef.reading.ui.components.SoftCard
import com.korimbekef.reading.ui.components.SpeechBubble
import com.korimbekef.reading.ui.components.StatPill
import com.korimbekef.reading.ui.theme.KColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.korimbekef.reading.speech.EngineStatus
import kotlinx.coroutines.launch
import java.time.LocalTime

data class HomeUi(
    val loading: Boolean = true,
    val child: ChildEntity? = null,
    val summary: ChildSummary = ChildSummary(),
    val continueSession: SessionEntity? = null,
    val continuePassage: Passage? = null,
    val recommended: Passage? = null,
) {
    val owlStage: MascotStage get() = MascotStage.forStars(summary.totalStars)
}

/** Picks the next passage to suggest: the first unfinished story of the child's level. */
fun recommendPassage(child: ChildEntity, passages: List<Passage>, progress: Map<String, PassageProgressEntity>, excludeId: String? = null): Passage? {
    val open = passages.filter { it.level <= maxOf(child.maxLevel, child.level) && it.id != excludeId }
    val atLevel = open.filter { it.level == child.level }
    return atLevel.firstOrNull { (progress[it.id]?.timesCompleted ?: 0) == 0 }
        ?: open.filter { it.level < child.level }.firstOrNull { (progress[it.id]?.timesCompleted ?: 0) == 0 }
        ?: atLevel.minByOrNull { progress[it.id]?.timesCompleted ?: 0 }
        ?: open.firstOrNull()
}

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<HomeUi> = c.children.activeChild.flatMapLatest { child ->
        if (child == null) {
            flowOf(HomeUi(loading = false))
        } else {
            combine(
                c.progress.observeSummary(child.id),
                c.progress.observeLatestUnfinished(child.id),
                c.content.visiblePassages,
            ) { summary, unfinished, passages ->
                val continuePassage = unfinished?.let { s -> passages.firstOrNull { it.id == s.passageId } }
                HomeUi(
                    loading = false,
                    child = child,
                    summary = summary,
                    continueSession = unfinished.takeIf { continuePassage != null },
                    continuePassage = continuePassage,
                    recommended = recommendPassage(child, passages, summary.progress),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUi())

    /** The owl says hello once per app launch (only if spoken prompts are on). */
    fun greet(child: ChildEntity) {
        if (greeted) return
        greeted = true
        viewModelScope.launch {
            if (!c.settings.current().voicePrompts) return@launch
            // The speech engine may still be starting right after launch.
            val ready = withTimeoutOrNull(5000) { c.speech.state.first { it.status != EngineStatus.INITIALIZING } }
            if (ready?.status != EngineStatus.READY) return@launch
            delay(400)
            c.speech.speakMessage(T.HOME_GREETING, 0.95f)
        }
    }

    companion object {
        private var greeted = false
    }
}

@Composable
fun HomeScreen(
    onRead: (String) -> Unit,
    onLibrary: () -> Unit,
    onGames: () -> Unit,
    onWords: () -> Unit,
    onTreasure: () -> Unit,
    onParents: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val girl = LocalGirl.current
    val child = ui.child

    LaunchedEffect(child?.id) { child?.let { vm.greet(it) } }

    Box(
        Modifier
            .fillMaxSize()
            .background(KColors.Cream)
            .drawBehind {
                drawCircle(KColors.VioletLight, radius = size.width * 0.55f, center = Offset(size.width * 1.05f, -size.width * 0.15f))
                drawCircle(KColors.YellowLight, radius = size.width * 0.35f, center = Offset(-size.width * 0.1f, size.height * 0.62f))
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // greeting row
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(54.dp).background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(child?.avatar ?: "🦉", fontSize = 30.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = KColors.InkSoft)
                    Text(
                        T.hello(child?.name.orEmpty()),
                        style = MaterialTheme.typography.headlineSmall,
                        color = KColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RoundIconButton(Icons.Rounded.Lock, T.PARENTS, onParents, size = 46.dp, color = Color.White.copy(alpha = 0.9f))
            }
            if (child?.gamification != false) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatPill("⭐", ui.summary.totalStars.toString(), label = T.STARS)
                    StatPill("🔥", ui.summary.currentStreak.toString(), label = T.DAYS_IN_ROW)
                    StatPill("🏅", ui.summary.medals.size.toString(), label = T.MEDALS)
                }
            }
            Spacer(Modifier.height(8.dp))
            val goalMinutes = child?.dailyGoalMinutes ?: 10
            val todayMinutes = ui.summary.todaySeconds / 60
            val goalReached = todayMinutes >= goalMinutes
            Row(
                Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Owl(stage = ui.owlStage, mood = if (goalReached) OwlMood.CHEER else OwlMood.HAPPY, size = 150.dp)
                SpeechBubble(
                    if (goalReached) T.GOAL_REACHED else T.WHAT_TODAY,
                    modifier = Modifier.weight(1f, fill = false),
                    textStyle = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.height(10.dp))
            val recommended = ui.recommended
            BigButton(
                text = T.START_STORY.of(girl),
                onClick = { recommended?.let { onRead(it.id) } ?: onLibrary() },
                emoji = "📖",
                color = KColors.Orange,
                height = 84.dp,
                textStyle = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            )
            if (recommended != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${T.NEXT_STORY}: ${recommended.emoji} ${recommended.title.get(child?.withNikud != false)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = KColors.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val cont = ui.continuePassage
            if (cont != null) {
                Spacer(Modifier.height(12.dp))
                BigButton(
                    text = T.CONTINUE_LAST,
                    onClick = { onRead(cont.id) },
                    emoji = cont.emoji,
                    color = KColors.Teal,
                    height = 66.dp,
                    textStyle = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    cont.title.get(child?.withNikud != false),
                    style = MaterialTheme.typography.titleSmall,
                    color = KColors.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    EmojiTile("📚", T.MY_LIBRARY, KColors.BlueLight, onLibrary, Modifier.weight(1f))
                    EmojiTile("🎮", T.MY_GAMES, KColors.PinkLight, onGames, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    EmojiTile("📝", T.MY_WORDS, KColors.GreenLight, onWords, Modifier.weight(1f))
                    EmojiTile(
                        if (child?.gamification != false) "🏆" else "🦉",
                        T.MY_TREASURE,
                        KColors.YellowLight,
                        onTreasure,
                        Modifier.weight(1f),
                    )
                }
            }
            if (child != null) {
                Spacer(Modifier.height(16.dp))
                SoftCard(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GoalRing(todayMinutes.toFloat() / goalMinutes.coerceAtLeast(1)) {
                            Text(if (goalReached) "🎯" else "⏱️", fontSize = 24.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(T.TODAY, style = MaterialTheme.typography.titleMedium, color = KColors.Ink)
                            Text(T.minutesOf(minOf(todayMinutes, 999), goalMinutes), style = MaterialTheme.typography.bodyLarge, color = KColors.InkSoft)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> T.GREET_MORNING
    in 12..16 -> T.GREET_NOON
    in 17..21 -> T.GREET_EVENING
    else -> T.GREET_NIGHT
}
