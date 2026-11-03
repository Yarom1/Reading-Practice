package com.korimbekef.reading.ui.screens.reading

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.korimbekef.reading.core.DualText
import com.korimbekef.reading.core.Levels
import com.korimbekef.reading.core.MascotStage
import com.korimbekef.reading.speech.EngineStatus
import com.korimbekef.reading.speech.PlayStatus
import com.korimbekef.reading.ui.T
import com.korimbekef.reading.ui.appViewModel
import com.korimbekef.reading.ui.components.BigButton
import com.korimbekef.reading.ui.components.ConfettiBurst
import com.korimbekef.reading.ui.components.KidTopBar
import com.korimbekef.reading.ui.components.LabeledIconButton
import com.korimbekef.reading.ui.components.Owl
import com.korimbekef.reading.ui.components.OwlMood
import com.korimbekef.reading.ui.components.ReadingLineText
import com.korimbekef.reading.ui.components.RoundIconButton
import com.korimbekef.reading.ui.components.SelectableChip
import com.korimbekef.reading.ui.components.SoftCard
import com.korimbekef.reading.ui.components.SpeechBubble
import com.korimbekef.reading.ui.components.StarsRow
import com.korimbekef.reading.ui.components.bouncyClick
import com.korimbekef.reading.ui.components.readingTextStyle
import com.korimbekef.reading.ui.theme.KColors
import kotlinx.coroutines.delay

@Composable
fun ReadingScreen(
    passageId: String,
    restart: Boolean,
    onBack: () -> Unit,
    onOpenPassage: (id: String, restart: Boolean) -> Unit,
    onLibrary: () -> Unit,
) {
    val vm = appViewModel(key = "read-$passageId-$restart") { ReadingViewModel(it, passageId, restart) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(vm) {
        vm.onVisible(true)
        onPauseOrDispose { vm.onVisible(false) }
    }

    val passage = ui.passage
    Box(Modifier.fillMaxSize().background(KColors.Cream)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            KidTopBar(
                title = passage?.title?.get(ui.withNikud) ?: "",
                onBack = onBack,
            ) {
                NikudToggle(ui.withNikud, vm::toggleNikud)
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Rounded.Tune, T.READ_SETTINGS, { showSettings = true }, size = 46.dp)
            }
            if (passage == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (ui.notFound) SpeechBubble(T.NO_STORIES) else Owl()
                }
                return@Column
            }
            ProgressHeader(read = ui.linesRead.size, total = ui.lineCount, focus = ui.focusLine)
            if (!ui.speechAvailable) {
                Text(
                    T.NO_VOICE_CHILD,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(KColors.YellowLight, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KColors.Ink,
                )
            }
            val listState = rememberLazyListState()
            LaunchedEffect(ui.focusLine, ui.playingLine) {
                val target = ui.focusLine + 1
                val visible = listState.layoutInfo.visibleItemsInfo
                val item = visible.firstOrNull { it.index == target }
                val viewportEnd = listState.layoutInfo.viewportEndOffset
                if (item == null || item.offset < 0 || item.offset + item.size > viewportEnd) {
                    listState.animateScrollToItem(target, scrollOffset = -140)
                }
            }
            val textStyle = readingTextStyle(FontSteps.sizes[ui.fontStep].sp)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "header") { StoryHeader(passage.emoji, passage.title, passage.level, passage.colorIndex, ui.withNikud, passage.isCustom) }
                itemsIndexed(passage.lines, key = { i, _ -> "line-$i" }) { index, line ->
                    LineCard(
                        index = index,
                        line = line,
                        withNikud = ui.withNikud,
                        style = textStyle,
                        focused = index == ui.focusLine,
                        playing = index == ui.playingLine && ui.playStatus != PlayStatus.IDLE,
                        currentWord = if (index == ui.playingLine) ui.playingWord else -1,
                        read = index in ui.linesRead,
                        heard = index in ui.linesHeard,
                        alone = ui.mode == ReadingMode.ALONE,
                        onTap = { vm.onLineTap(index) },
                        onWordTap = { w -> vm.onWordTap(index, w) },
                        onWordLongPress = { w -> vm.openWord(index, w) },
                        onListen = { vm.playLine(index) },
                        onMarkRead = { vm.markRead(index) },
                    )
                }
                item(key = "hint") {
                    Text(
                        if (ui.mode == ReadingMode.ALONE) T.ALONE_HINT else T.TAP_LINE_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = KColors.InkSoft,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            BottomArea(ui, vm)
        }

        // floating feedback
        PraiseToast(ui.praise, ui.praiseKey, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 70.dp))
        StarPop(ui.starKey, Modifier.align(Alignment.Center))
        VolumeHint(ui.volumeHintKey, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 70.dp))
    }

    ui.completion?.let { completion ->
        CompletionDialog(
            completion = completion,
            girl = ui.girl,
            gamification = ui.gamification,
            onNext = completion.nextPassageId?.let { id -> { onOpenPassage(id, false) } },
            onAgain = { vm.restartStory() },
            onLibrary = onLibrary,
        )
    }
    ui.wordSheet?.let { sheet ->
        WordSheetView(sheet, ui.withNikud, ui.speechAvailable, onHear = vm::hearWord, onToggleSave = vm::toggleSaveWord, onDismiss = vm::closeWord)
    }
    if (showSettings) {
        ReadingSettingsSheet(ui, vm, onDismiss = { showSettings = false })
    }
}

@Composable
private fun NikudToggle(on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) KColors.Violet else Color.White)
            .border(2.dp, KColors.Violet, RoundedCornerShape(50))
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (on) "בָּ" else "ב", fontSize = 20.sp, color = if (on) Color.White else KColors.Violet, style = readingTextStyle(20.sp))
        Spacer(Modifier.width(6.dp))
        Text(T.NIKUD, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else KColors.Violet)
    }
}

@Composable
private fun ProgressHeader(read: Int, total: Int, focus: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp)) {
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else read.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = KColors.Green,
            trackColor = KColors.GreenLight,
        )
        Spacer(Modifier.height(4.dp))
        Text(T.lineOf(focus + 1, total), style = MaterialTheme.typography.labelMedium, color = KColors.InkSoft)
    }
}

@Composable
private fun StoryHeader(emoji: String, title: DualText, level: Int, colorIndex: Int, withNikud: Boolean, custom: Boolean) {
    Column(
        Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(KColors.cardBrush(colorIndex))
            .padding(vertical = 16.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 56.sp, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(title.get(withNikud), style = readingTextStyle(28.sp).copy(textAlign = TextAlign.Center), textAlign = TextAlign.Center)
        if (!custom) {
            Text(
                "${T.LEVEL} $level · ${Levels.info(level).name.get(true)}",
                style = MaterialTheme.typography.labelLarge,
                color = KColors.Ink.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun LineCard(
    index: Int,
    line: DualText,
    withNikud: Boolean,
    style: androidx.compose.ui.text.TextStyle,
    focused: Boolean,
    playing: Boolean,
    currentWord: Int,
    read: Boolean,
    heard: Boolean,
    alone: Boolean,
    onTap: () -> Unit,
    onWordTap: (Int) -> Unit,
    onWordLongPress: (Int) -> Unit,
    onListen: () -> Unit,
    onMarkRead: () -> Unit,
) {
    val border = when {
        playing -> BorderStroke(3.dp, KColors.WordNowEdge)
        focused -> BorderStroke(3.dp, KColors.Violet)
        else -> BorderStroke(1.dp, KColors.Line)
    }
    Card(
        onClick = onTap,
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (playing) Color(0xFFFFFCEB) else Color.White),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = if (focused) 4.dp else 1.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(
                        when {
                            read -> KColors.Green
                            heard -> KColors.VioletLight
                            else -> KColors.Cream
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (read) {
                    Text("⭐", fontSize = 16.sp)
                } else {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = KColors.InkSoft)
                }
            }
            Spacer(Modifier.width(10.dp))
            ReadingLineText(
                text = line.get(withNikud),
                tokens = line.tokens(withNikud),
                currentWord = currentWord,
                spokenBefore = if (currentWord > 0) currentWord else 0,
                style = style,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                onTap = { w -> onWordTap(w) },
                onLongPress = { w -> if (w >= 0) onWordLongPress(w) },
            )
            if (alone) {
                Spacer(Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoundIconButton(Icons.AutoMirrored.Rounded.VolumeUp, T.LISTEN, onListen, size = 42.dp)
                    RoundIconButton(
                        Icons.Rounded.Check,
                        T.MARK_READ,
                        onMarkRead,
                        size = 42.dp,
                        color = if (read) KColors.Green else Color.White,
                        tint = if (read) Color.White else KColors.Green,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomArea(ui: ReadingUi, vm: ReadingViewModel) {
    val state = when {
        ui.wholeStoryPrompt -> 2
        ui.turnLine >= 0 && ui.mode == ReadingMode.TOGETHER -> 1
        else -> 0
    }
    AnimatedContent(
        targetState = state,
        transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { it / 2 } + fadeOut()) },
        label = "bottom",
    ) { s ->
        when (s) {
            1 -> TurnPanel(ui.turnText, onDone = vm::onDoneReading, onListenAgain = vm::onListenAgain, onTogether = vm::onReadTogether, speechAvailable = ui.speechAvailable)
            2 -> WholeStoryPanel(ui.girl, onDone = vm::onWholeStoryRead, onListenAgain = vm::readAll)
            else -> Controls(ui, vm)
        }
    }
}

@Composable
private fun Controls(ui: ReadingUi, vm: ReadingViewModel) {
    val speaking = ui.playStatus == PlayStatus.SPEAKING
    val paused = ui.playStatus == PlayStatus.PAUSED
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledIconButton(Icons.Rounded.KeyboardArrowUp, T.PREV_LINE, vm::onPrevLine, enabled = ui.focusLine > 0, modifier = Modifier.weight(1f))
            LabeledIconButton(Icons.Rounded.Replay, T.AGAIN, vm::onReplay, enabled = ui.speechAvailable, modifier = Modifier.weight(1f))
            BigButton(
                text = when {
                    speaking -> T.PAUSE
                    paused -> T.RESUME
                    else -> T.LISTEN
                },
                onClick = vm::onPlayPause,
                icon = if (speaking) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                color = if (speaking) KColors.Orange else KColors.Violet,
                height = 64.dp,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1.9f).padding(horizontal = 4.dp),
                enabled = ui.speechAvailable || ui.mode == ReadingMode.TOGETHER,
            )
            LabeledIconButton(Icons.Rounded.AutoStories, T.READ_ALL, vm::readAll, enabled = ui.speechAvailable, modifier = Modifier.weight(1f))
            LabeledIconButton(Icons.Rounded.KeyboardArrowDown, T.NEXT_LINE, vm::onNextLine, enabled = ui.focusLine < ui.lineCount - 1, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TurnPanel(text: String, onDone: () -> Unit, onListenAgain: () -> Unit, onTogether: () -> Unit, speechAvailable: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(KColors.VioletLight, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.widthIn(max = 640.dp), verticalAlignment = Alignment.CenterVertically) {
            Owl(mood = OwlMood.HAPPY, size = 78.dp)
            Spacer(Modifier.width(8.dp))
            SpeechBubble(text, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        BigButton(
            text = T.DONE_READING,
            onClick = onDone,
            emoji = "✅",
            color = KColors.Green,
            height = 66.dp,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        )
        if (speechAvailable) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(
                    T.LISTEN_AGAIN,
                    onListenAgain,
                    emoji = "🔊",
                    color = Color.White,
                    contentColor = KColors.Ink,
                    height = 54.dp,
                    textStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                BigButton(
                    T.READ_TOGETHER,
                    onTogether,
                    emoji = "🐢",
                    color = Color.White,
                    contentColor = KColors.Ink,
                    height = 54.dp,
                    textStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WholeStoryPanel(girl: Boolean, onDone: () -> Unit, onListenAgain: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(KColors.TealLight, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.widthIn(max = 640.dp), verticalAlignment = Alignment.CenterVertically) {
            Owl(mood = OwlMood.HAPPY, size = 78.dp)
            Spacer(Modifier.width(8.dp))
            SpeechBubble(T.READ_ALL_DONE.of(girl), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        BigButton(T.READ_ALL_DONE_BUTTON, onDone, emoji = "🏁", color = KColors.Green, height = 64.dp, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        BigButton(
            T.LISTEN_AGAIN,
            onListenAgain,
            emoji = "🔊",
            color = Color.White,
            contentColor = KColors.Ink,
            height = 52.dp,
            textStyle = MaterialTheme.typography.titleMedium,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun PraiseToast(text: String?, key: Int, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (key > 0) {
            visible = true
            delay(2600)
            visible = false
        }
    }
    AnimatedVisibility(visible = visible && text != null, modifier = modifier, enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
        SpeechBubble(text.orEmpty(), color = KColors.YellowLight, textStyle = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun StarPop(key: Int, modifier: Modifier) {
    if (key == 0) return
    val anim = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { anim.animateTo(1f, tween(1100)) }
    val t = anim.value
    if (t >= 1f) return
    val scale = if (t < 0.3f) 0.4f + t * 3f else 1.3f
    Text(
        "⭐",
        fontSize = 90.sp,
        modifier = modifier
            .padding(bottom = (t * 220).dp)
            .scale(scale)
            .alpha(if (t > 0.6f) (1f - t) / 0.4f else 1f),
    )
}

@Composable
private fun VolumeHint(key: Int, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (key > 0) {
            visible = true
            delay(4000)
            visible = false
        }
    }
    AnimatedVisibility(visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        SpeechBubble(T.VOLUME_OFF, color = KColors.OrangeLight)
    }
}

@Composable
private fun CompletionDialog(
    completion: CompletionUi,
    girl: Boolean,
    gamification: Boolean,
    onNext: (() -> Unit)?,
    onAgain: () -> Unit,
    onLibrary: () -> Unit,
) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            SoftCard(Modifier.padding(20.dp).widthIn(max = 520.dp), padding = PaddingValues(20.dp)) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Owl(stage = completion.newOwlStage ?: MascotStage.BABY, mood = OwlMood.CHEER, size = 150.dp)
                    Text(T.COMPLETED.of(girl), style = MaterialTheme.typography.headlineSmall, color = KColors.Ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(T.COMPLETED_SUB.of(girl), style = MaterialTheme.typography.bodyLarge, color = KColors.InkSoft, textAlign = TextAlign.Center)
                    if (gamification) {
                        Spacer(Modifier.height(12.dp))
                        StarsRow(completion.passageStars, size = 34.dp)
                        if (completion.starsAdded > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text("+${completion.starsAdded} ⭐", style = MaterialTheme.typography.headlineMedium, color = KColors.Orange)
                        }
                        completion.newOwlStage?.let { stage ->
                            Spacer(Modifier.height(10.dp))
                            Text("🎁 ${T.OWL_GIFT} ${stage.emoji}", style = MaterialTheme.typography.titleMedium, color = KColors.Violet, textAlign = TextAlign.Center)
                        }
                        if (completion.medals.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(T.NEW_MEDAL, style = MaterialTheme.typography.titleMedium, color = KColors.Ink)
                            completion.medals.forEach { m ->
                                Text("${m.emoji} ${m.title}", style = MaterialTheme.typography.titleMedium, color = KColors.Ink)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (onNext != null) {
                        BigButton(T.TO_NEXT_STORY, onNext, emoji = "➡️", color = KColors.Orange, height = 62.dp, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton(T.READ_AGAIN, onAgain, emoji = "🔁", color = KColors.Teal, height = 54.dp, textStyle = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        BigButton(T.TO_LIBRARY, onLibrary, emoji = "📚", color = KColors.Violet, height = 54.dp, textStyle = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
            ConfettiBurst(key = completion)
        }
    }
}

@Composable
private fun WordSheetView(sheet: WordSheet, withNikud: Boolean, speechAvailable: Boolean, onHear: () -> Unit, onToggleSave: () -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Color.White) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            sheet.vocab?.let { Text(it.emoji, fontSize = 72.sp) }
            Text(if (withNikud) sheet.nikud else sheet.plain, style = readingTextStyle(52.sp).copy(textAlign = TextAlign.Center))
            sheet.vocab?.description?.let { d ->
                Spacer(Modifier.height(6.dp))
                Text(d.get(withNikud), style = readingTextStyle(22.sp).copy(textAlign = TextAlign.Center, color = KColors.InkSoft))
            }
            Spacer(Modifier.height(16.dp))
            if (speechAvailable) {
                BigButton(T.HEAR_WORD, onHear, emoji = "🔊", color = KColors.Teal, height = 60.dp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            }
            BigButton(
                if (sheet.saved) T.REMOVE_FROM_LIST else T.ADD_TO_MY_WORDS,
                onToggleSave,
                emoji = if (sheet.saved) "✖️" else "⭐",
                color = if (sheet.saved) Color.White else KColors.Yellow,
                contentColor = KColors.Ink,
                height = 58.dp,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            if (sheet.saved) {
                Spacer(Modifier.height(6.dp))
                Text("✓ ${T.IN_MY_WORDS}", style = MaterialTheme.typography.titleSmall, color = KColors.Green)
            }
        }
    }
}

@Composable
private fun ReadingSettingsSheet(ui: ReadingUi, vm: ReadingViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(T.READ_SETTINGS, style = MaterialTheme.typography.headlineSmall, color = KColors.Ink)
            Spacer(Modifier.height(14.dp))
            Text(T.FONT_SIZE, style = MaterialTheme.typography.titleMedium, color = KColors.Ink)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                FontSteps.sizes.forEachIndexed { i, size ->
                    val selected = i == ui.fontStep
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) KColors.Violet else KColors.Cream)
                            .bouncyClick { vm.setFontStep(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("אָ", style = readingTextStyle((size * 0.8f).sp).copy(color = if (selected) Color.White else KColors.Ink))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(T.SPEED, style = MaterialTheme.typography.titleMedium, color = KColors.Ink)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val icons = listOf("🐌", "🐢", "🚶", "🐇")
                Speeds.rates.indices.forEach { i ->
                    SelectableChip("${icons[i]} ${T.SPEED_NAMES[i]}", i == ui.speedIndex, { vm.setSpeed(i) }, color = KColors.Teal)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectableChip("👂 ${T.MODE_TOGETHER}", ui.mode == ReadingMode.TOGETHER, { vm.setMode(ReadingMode.TOGETHER) })
                SelectableChip("📖 ${T.MODE_ALONE}", ui.mode == ReadingMode.ALONE, { vm.setMode(ReadingMode.ALONE) })
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectableChip("בָּ ${T.NIKUD}", ui.withNikud, { if (!ui.withNikud) vm.toggleNikud() }, color = KColors.Orange)
                SelectableChip("ב", !ui.withNikud, { if (ui.withNikud) vm.toggleNikud() }, color = KColors.Orange)
            }
            if (ui.speechStatus == EngineStatus.NO_HEBREW || ui.speechStatus == EngineStatus.NO_ENGINE) {
                Spacer(Modifier.height(12.dp))
                Text(T.NO_VOICE_CHILD, style = MaterialTheme.typography.bodyMedium, color = KColors.InkSoft)
            }
            Spacer(Modifier.height(18.dp))
            BigButton(T.OK, onDismiss, emoji = "✅", color = KColors.Violet, height = 56.dp, modifier = Modifier.fillMaxWidth())
        }
    }
}
