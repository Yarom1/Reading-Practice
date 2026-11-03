package com.korimbekef.reading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.korimbekef.reading.AppContainer
import com.korimbekef.reading.core.Levels
import com.korimbekef.reading.core.Passage
import com.korimbekef.reading.core.Rewards
import com.korimbekef.reading.data.db.ChildEntity
import com.korimbekef.reading.data.db.PassageProgressEntity
import com.korimbekef.reading.ui.T
import com.korimbekef.reading.ui.appViewModel
import com.korimbekef.reading.ui.components.KidTopBar
import com.korimbekef.reading.ui.components.Owl
import com.korimbekef.reading.ui.components.OwlMood
import com.korimbekef.reading.ui.components.SpeechBubble
import com.korimbekef.reading.ui.components.StarsRow
import com.korimbekef.reading.ui.components.bouncyClick
import com.korimbekef.reading.ui.theme.KColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Shelf index 0 = parent's custom passages; 1..5 = levels. */
data class LibraryUi(
    val loading: Boolean = true,
    val child: ChildEntity? = null,
    val passages: List<Passage> = emptyList(),
    val progress: Map<String, PassageProgressEntity> = emptyMap(),
) {
    val hasCustom: Boolean get() = passages.any { it.isCustom }
    fun shelf(index: Int): List<Passage> =
        if (index == 0) passages.filter { it.isCustom } else passages.filter { !it.isCustom && it.level == index }
    fun isLocked(level: Int): Boolean = child != null && level > maxOf(child.maxLevel, child.level)
}

class LibraryViewModel(c: AppContainer) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<LibraryUi> = c.children.activeChild.flatMapLatest { child ->
        if (child == null) {
            flowOf(LibraryUi(loading = false))
        } else {
            combine(c.content.visiblePassages, c.progress.observeProgress(child.id)) { passages, progress ->
                LibraryUi(false, child, passages, progress.associateBy { it.passageId })
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUi())
}

@Composable
fun LibraryScreen(initialLevel: Int, onBack: () -> Unit, onRead: (String) -> Unit) {
    val vm = appViewModel { LibraryViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var shelf by rememberSaveable { mutableIntStateOf(if (initialLevel in 1..5) initialLevel else -1) }
    LaunchedEffect(ui.child?.id) {
        val child = ui.child
        if (shelf == -1 && child != null) shelf = child.level
    }
    val withNikud = ui.child?.withNikud != false

    Column(Modifier.fillMaxSize().background(KColors.Cream).navigationBarsPadding()) {
        KidTopBar(T.LIBRARY, onBack)
        // shelves: levels (and the parents' shelf if there is one)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Levels.all.forEach { info ->
                val selected = shelf == info.number
                val locked = ui.isLocked(info.number)
                Column(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (selected) KColors.level(info.number) else Color.White)
                        .border(2.dp, KColors.level(info.number), RoundedCornerShape(22.dp))
                        .bouncyClick { shelf = info.number }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (locked) "🔒" else info.emoji, fontSize = 26.sp)
                    Text(
                        "${T.LEVEL} ${info.number}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) Color.White else KColors.Ink,
                    )
                }
            }
            if (ui.hasCustom) {
                val selected = shelf == 0
                Column(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (selected) KColors.Teal else Color.White)
                        .border(2.dp, KColors.Teal, RoundedCornerShape(22.dp))
                        .bouncyClick { shelf = 0 }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("💌", fontSize = 26.sp)
                    Text(T.FROM_PARENTS, style = MaterialTheme.typography.titleSmall, color = if (selected) Color.White else KColors.Ink)
                }
            }
        }
        if (shelf in 1..5) {
            Text(
                Levels.info(shelf).name.get(true),
                style = MaterialTheme.typography.titleLarge,
                color = KColors.Ink,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        val locked = shelf in 1..5 && ui.isLocked(shelf)
        val items = ui.shelf(shelf)
        if (ui.loading || shelf == -1) {
            Spacer(Modifier.weight(1f))
        } else if (locked || items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Owl(mood = OwlMood.IDLE, size = 140.dp)
                Spacer(Modifier.height(12.dp))
                SpeechBubble(if (locked) "${T.LOCKED}\n${T.LOCKED_SUB}" else T.NO_STORIES)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { passage ->
                    PassageCard(
                        passage = passage,
                        progress = ui.progress[passage.id],
                        withNikud = withNikud,
                        showStars = ui.child?.gamification != false,
                        onClick = { onRead(passage.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun PassageCard(passage: Passage, progress: PassageProgressEntity?, withNikud: Boolean, showStars: Boolean, onClick: () -> Unit) {
    val times = progress?.timesCompleted ?: 0
    val isNew = progress == null || progress.lastOpenedAt == 0L
    Column(
        Modifier
            .shadow(5.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .bouncyClick(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .background(KColors.cardBrush(passage.colorIndex)),
            contentAlignment = Alignment.Center,
        ) {
            Text(passage.emoji, fontSize = 52.sp, textAlign = TextAlign.Center, maxLines = 1)
            if (isNew) {
                Text(
                    T.NEW,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(KColors.Pink, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (times > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(30.dp)
                        .background(KColors.Green, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", color = Color.White, style = MaterialTheme.typography.titleMedium) }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                passage.title.get(withNikud),
                style = MaterialTheme.typography.titleMedium,
                color = KColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(T.linesCount(passage.lineCount), style = MaterialTheme.typography.labelMedium, color = KColors.InkSoft, modifier = Modifier.weight(1f))
                if (showStars) StarsRow(Rewards.passageStars(times), size = 14.dp)
            }
        }
    }
}
