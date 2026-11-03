package com.korimbekef.reading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.korimbekef.reading.AppContainer
import com.korimbekef.reading.core.DualText
import com.korimbekef.reading.core.HebrewText
import com.korimbekef.reading.core.MascotStage
import com.korimbekef.reading.core.Medals
import com.korimbekef.reading.data.ChildSummary
import com.korimbekef.reading.data.db.ChildEntity
import com.korimbekef.reading.data.db.PracticeWordEntity
import com.korimbekef.reading.ui.T
import com.korimbekef.reading.ui.appViewModel
import com.korimbekef.reading.ui.components.BigButton
import com.korimbekef.reading.ui.components.KidTopBar
import com.korimbekef.reading.ui.components.Owl
import com.korimbekef.reading.ui.components.OwlMood
import com.korimbekef.reading.ui.components.RoundIconButton
import com.korimbekef.reading.ui.components.SoftCard
import com.korimbekef.reading.ui.components.SpeechBubble
import com.korimbekef.reading.ui.components.readingTextStyle
import com.korimbekef.reading.ui.theme.KColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WordsUi(
    val child: ChildEntity? = null,
    val words: List<PracticeWordEntity> = emptyList(),
    val emojis: Map<String, String> = emptyMap(),
)

class MyWordsViewModel(private val c: AppContainer) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<WordsUi> = c.children.activeChild.flatMapLatest { child ->
        if (child == null) {
            flowOf(WordsUi())
        } else {
            combine(c.progress.observeWords(child.id), flowOf(Unit)) { words, _ ->
                val dict = c.content.dictionary()
                WordsUi(child, words, words.associate { w -> w.plain to (dict.find(w.word, w.plain)?.emoji ?: "⭐") })
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WordsUi())

    fun speak(word: PracticeWordEntity) {
        c.speech.speakMessage(word.word, 0.85f)
    }

    fun remove(word: PracticeWordEntity) {
        viewModelScope.launch { c.progress.removeWord(word.id) }
    }
}

@Composable
fun MyWordsScreen(onBack: () -> Unit, onPractice: () -> Unit) {
    val vm = appViewModel { MyWordsViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val withNikud = ui.child?.withNikud != false
    Column(Modifier.fillMaxSize().background(KColors.Cream).navigationBarsPadding()) {
        KidTopBar(T.MY_WORDS, onBack)
        if (ui.words.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Owl(size = 150.dp)
                Spacer(Modifier.height(10.dp))
                SpeechBubble("${T.MY_WORDS_EMPTY}\n${T.MY_WORDS_HOW}")
            }
        } else {
            if (ui.words.size >= 3) {
                BigButton(
                    T.PRACTICE_MY_WORDS,
                    onPractice,
                    emoji = "🎯",
                    color = KColors.Orange,
                    height = 62.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(ui.words, key = { it.id }) { w ->
                    val display = if (withNikud && HebrewText.hasNikud(w.word)) w.word else w.plain
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .shadow(3.dp, RoundedCornerShape(22.dp))
                            .background(Color.White, RoundedCornerShape(22.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ui.emojis[w.plain] ?: "⭐", fontSize = 34.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(display, style = readingTextStyle(34.sp))
                            if (w.addedByParent) Text(T.FROM_PARENTS, style = MaterialTheme.typography.labelSmall, color = KColors.InkSoft)
                        }
                        RoundIconButton(Icons.AutoMirrored.Rounded.VolumeUp, T.HEAR_WORD, { vm.speak(w) }, size = 48.dp, color = KColors.TealLight)
                        Spacer(Modifier.width(8.dp))
                        RoundIconButton(Icons.Rounded.Close, T.REMOVE_FROM_LIST, { vm.remove(w) }, size = 40.dp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- treasure (owl + medals)

data class TreasureUi(val child: ChildEntity? = null, val summary: ChildSummary = ChildSummary())

class TreasureViewModel(c: AppContainer) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<TreasureUi> = c.children.activeChild.flatMapLatest { child ->
        if (child == null) flowOf(TreasureUi()) else c.progress.observeSummary(child.id).let { flow ->
            combine(flow, flowOf(child)) { s, ch -> TreasureUi(ch, s) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TreasureUi())
}

@Composable
fun TreasureScreen(onBack: () -> Unit) {
    val vm = appViewModel { TreasureViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val stars = ui.summary.totalStars
    val stage = MascotStage.forStars(stars)
    val earned = ui.summary.medals.map { it.code }.toSet()
    val gamification = ui.child?.gamification != false

    Column(Modifier.fillMaxSize().background(KColors.Cream).navigationBarsPadding()) {
        KidTopBar(T.MY_TREASURE, onBack)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Owl(stage = stage, mood = OwlMood.HAPPY, size = 170.dp)
                        Text(stage.title, style = readingTextStyle(24.sp).copy(textAlign = TextAlign.Center))
                        if (gamification) {
                            Spacer(Modifier.height(6.dp))
                            Text("⭐ $stars", style = MaterialTheme.typography.headlineSmall, color = KColors.Orange)
                            val next = stage.next
                            if (next != null) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { ((stars - stage.minStars).toFloat() / (next.minStars - stage.minStars)).coerceIn(0f, 1f) },
                                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                                    color = KColors.Orange,
                                    trackColor = KColors.OrangeLight,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text("${T.starsToNextGift(next.minStars - stars)} ${next.emoji}", style = readingTextStyle(20.sp).copy(color = KColors.InkSoft, textAlign = TextAlign.Center))
                            } else {
                                Text(T.ALL_GIFTS, style = MaterialTheme.typography.titleMedium, color = KColors.Violet)
                            }
                        }
                    }
                }
            }
            if (gamification) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(T.MY_MEDALS, style = readingTextStyle(26.sp), modifier = Modifier.padding(top = 6.dp))
                }
                items(Medals.all, key = { it.code }) { m ->
                    val has = m.code in earned
                    Column(
                        Modifier
                            .shadow(if (has) 4.dp else 0.dp, RoundedCornerShape(22.dp))
                            .background(if (has) Color.White else Color.White.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                            .padding(12.dp)
                            .alpha(if (has) 1f else 0.55f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (has) m.emoji else "🔒", fontSize = 40.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(m.title, style = readingTextStyle(18.sp).copy(textAlign = TextAlign.Center))
                        Text(m.hint, style = readingTextStyle(14.sp).copy(color = KColors.InkSoft, textAlign = TextAlign.Center))
                    }
                }
            }
        }
    }
}

/** Display helper used by parent screens. */
fun DualText.display(withNikud: Boolean): String = get(withNikud)
