package com.korimbekef.reading.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.korimbekef.reading.AppContainer
import com.korimbekef.reading.audio.Sfx
import com.korimbekef.reading.core.DualText
import com.korimbekef.reading.core.GameGenerator
import com.korimbekef.reading.core.GameType
import com.korimbekef.reading.core.HebrewText
import com.korimbekef.reading.core.MemoryCard
import com.korimbekef.reading.core.QuizQuestion
import com.korimbekef.reading.core.VocabWord
import com.korimbekef.reading.data.db.ChildEntity
import com.korimbekef.reading.ui.T
import com.korimbekef.reading.ui.appViewModel
import com.korimbekef.reading.ui.components.BigButton
import com.korimbekef.reading.ui.components.ConfettiBurst
import com.korimbekef.reading.ui.components.EmojiTile
import com.korimbekef.reading.ui.components.KidTopBar
import com.korimbekef.reading.ui.components.Owl
import com.korimbekef.reading.ui.components.OwlMood
import com.korimbekef.reading.ui.components.RoundIconButton
import com.korimbekef.reading.ui.components.SpeechBubble
import com.korimbekef.reading.ui.components.bouncyClick
import com.korimbekef.reading.ui.components.readingTextStyle
import com.korimbekef.reading.ui.theme.KColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class GameInfo(val type: GameType, val emoji: String, val title: String, val color: Color)

private val GAMES = listOf(
    GameInfo(GameType.WORD_TO_PICTURE, "🖼️", T.GAME_WORD_PICTURE, KColors.BlueLight),
    GameInfo(GameType.PICTURE_TO_WORD, "📝", T.GAME_PICTURE_WORD, KColors.PinkLight),
    GameInfo(GameType.FIRST_LETTER, "אָ", T.GAME_FIRST_LETTER, KColors.YellowLight),
    GameInfo(GameType.LISTEN_AND_CHOOSE, "👂", T.GAME_LISTEN, KColors.GreenLight),
    GameInfo(GameType.MISSING_WORD, "🧩", T.GAME_MISSING, KColors.OrangeLight),
    GameInfo(GameType.MEMORY, "🃏", T.GAME_MEMORY, KColors.VioletLight),
)

/** Builds the word pool for games: the picture dictionary, or the child's own saved words. */
private suspend fun gamePool(c: AppContainer, child: ChildEntity, onlyMine: Boolean): List<VocabWord> {
    val vocab = c.content.vocabulary()
    if (!onlyMine) return vocab
    val dict = c.content.dictionary()
    val mine = c.progress.observeWords(child.id).first()
    return mine.map { w ->
        dict.find(w.word, w.plain)
            ?: VocabWord(
                word = if (HebrewText.hasNikud(w.word)) DualText(w.word, w.plain) else DualText.ofUserText(w.plain),
                emoji = "⭐",
                category = "mine",
                description = null,
            )
    }.distinctBy { it.plainLetters }
}

@Composable
fun GamesScreen(onBack: () -> Unit, onPlay: (GameType, Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().background(KColors.Cream).navigationBarsPadding()) {
        KidTopBar(T.MY_GAMES, onBack)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(GAMES) { _, g ->
                EmojiTile(g.emoji, g.title, g.color, { onPlay(g.type, false) })
            }
        }
    }
}

// ---------------------------------------------------------------- quiz games

data class QuizUi(
    val loading: Boolean = true,
    val girl: Boolean = false,
    val withNikud: Boolean = true,
    val questions: List<QuizQuestion> = emptyList(),
    val index: Int = 0,
    val wrong: Set<Int> = emptySet(),
    val solved: Boolean = false,
    val feedback: String? = null,
    val finished: Boolean = false,
    val starsAdded: Int = 0,
    val notEnough: Boolean = false,
) {
    val current: QuizQuestion? get() = questions.getOrNull(index)
}

class QuizViewModel(private val c: AppContainer, private val type: GameType, private val onlyMine: Boolean) : ViewModel() {
    private val _ui = MutableStateFlow(QuizUi())
    val ui: StateFlow<QuizUi> = _ui.asStateFlow()
    private var child: ChildEntity? = null

    init {
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        val ch = c.children.active() ?: return
        child = ch
        val pool = gamePool(c, ch, onlyMine)
        val passages = c.content.visiblePassages.first().filter { it.level <= ch.level.coerceAtLeast(1) && !it.isCustom }
        val questions = GameGenerator(c.content.vocabulary().let { if (onlyMine) pool + it else it })
            .questions(type, 8, pool = if (pool.size >= 3) pool else c.content.vocabulary(), passages = passages)
        _ui.value = QuizUi(
            loading = false,
            girl = ch.isGirl,
            withNikud = ch.withNikud || ch.level <= 2,
            questions = questions,
            notEnough = questions.isEmpty(),
        )
        autoSpeak()
    }

    private fun autoSpeak() {
        val q = _ui.value.current ?: return
        if (q.type == GameType.LISTEN_AND_CHOOSE || q.type == GameType.FIRST_LETTER) {
            viewModelScope.launch {
                delay(400)
                speak()
            }
        }
    }

    fun speak() {
        val q = _ui.value.current ?: return
        q.speakText?.let { c.speech.speakMessage(it, 0.85f) }
    }

    fun answer(option: Int) {
        val st = _ui.value
        val q = st.current ?: return
        if (st.solved || option in st.wrong) return
        if (option == q.correctIndex) {
            c.sounds.play(Sfx.CORRECT)
            _ui.update { it.copy(solved = true, feedback = T.pick(T.CORRECT)) }
            q.speakText?.let { c.speech.speakMessage(it, 0.9f) }
            viewModelScope.launch {
                delay(if (q.type == GameType.MISSING_WORD) 2600 else 1500)
                next()
            }
        } else {
            c.sounds.play(Sfx.SOFT)
            _ui.update { it.copy(wrong = it.wrong + option, feedback = T.TRY_AGAIN) }
        }
    }

    private fun next() {
        val st = _ui.value
        if (st.index + 1 >= st.questions.size) {
            finish()
        } else {
            _ui.update { it.copy(index = it.index + 1, wrong = emptySet(), solved = false, feedback = null) }
            autoSpeak()
        }
    }

    private fun finish() {
        val ch = child ?: return
        viewModelScope.launch {
            val (stars, _) = c.progress.recordGame(ch)
            c.sounds.play(Sfx.FANFARE)
            _ui.update { it.copy(finished = true, starsAdded = stars) }
            if (c.settings.current().voicePrompts) c.speech.speakMessage(T.GAME_DONE, 0.95f)
        }
    }

    fun restart() {
        _ui.value = QuizUi()
        viewModelScope.launch { start() }
    }

    override fun onCleared() {
        c.speech.stop()
        super.onCleared()
    }
}

@Composable
fun GamePlayScreen(type: GameType, onlyMyWords: Boolean, onBack: () -> Unit) {
    val vm = appViewModel(key = "quiz-$type-$onlyMyWords") { QuizViewModel(it, type, onlyMyWords) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val title = GAMES.firstOrNull { it.type == type }?.title ?: T.MY_GAMES

    Box(Modifier.fillMaxSize().background(KColors.Cream)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            KidTopBar(if (onlyMyWords) T.MY_WORDS else title, onBack)
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Owl() }
                ui.notEnough -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Owl()
                    SpeechBubble(T.NEED_MORE_WORDS)
                }
                ui.finished -> GameFinished(ui.starsAdded, onAgain = vm::restart, onBack = onBack)
                else -> ui.current?.let { q ->
                    LinearProgressIndicator(
                        progress = { (ui.index + 1f) / ui.questions.size },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = KColors.Teal,
                        trackColor = KColors.TealLight,
                    )
                    AnimatedContent(targetState = ui.index, label = "question") { _ ->
                        QuestionView(q, ui, onSpeak = vm::speak, onAnswer = vm::answer)
                    }
                }
            }
        }
        if (ui.finished) ConfettiBurst(key = ui.questions.hashCode())
    }
}

private fun questionTitle(type: GameType): String = when (type) {
    GameType.WORD_TO_PICTURE -> T.Q_WORD_PICTURE
    GameType.PICTURE_TO_WORD -> T.Q_PICTURE_WORD
    GameType.FIRST_LETTER -> T.Q_FIRST_LETTER
    GameType.LISTEN_AND_CHOOSE -> T.Q_LISTEN
    GameType.MISSING_WORD -> T.Q_MISSING
    GameType.MEMORY -> T.Q_MEMORY
}

@Composable
private fun QuestionView(q: QuizQuestion, ui: QuizUi, onSpeak: () -> Unit, onAnswer: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(questionTitle(q.type), style = MaterialTheme.typography.titleLarge, color = KColors.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        // prompt
        Box(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .shadow(4.dp, RoundedCornerShape(28.dp))
                .background(Color.White, RoundedCornerShape(28.dp))
                .padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (q.type) {
                    GameType.WORD_TO_PICTURE -> Text(q.promptText?.get(ui.withNikud).orEmpty(), style = readingTextStyle(54.sp))
                    GameType.PICTURE_TO_WORD -> Text(q.promptEmoji.orEmpty(), fontSize = 96.sp)
                    GameType.FIRST_LETTER -> Text(q.promptEmoji.orEmpty(), fontSize = 96.sp)
                    GameType.LISTEN_AND_CHOOSE -> Text("👂", fontSize = 80.sp)
                    GameType.MISSING_WORD -> {
                        val line = q.promptText
                        if (line != null) {
                            val tokens = line.tokens(ui.withNikud)
                            val parts = (0 until tokens.wordCount).joinToString(" ") { i ->
                                if (i == q.blankWordIndex) {
                                    if (ui.solved) q.options[q.correctIndex].text?.get(ui.withNikud).orEmpty() else "______"
                                } else {
                                    tokens.word(i)
                                }
                            }
                            Text(parts, style = readingTextStyle(32.sp).copy(textAlign = TextAlign.Center))
                        }
                    }
                    GameType.MEMORY -> Unit
                }
                if (q.speakText != null && (q.type == GameType.LISTEN_AND_CHOOSE || q.type == GameType.FIRST_LETTER || ui.solved)) {
                    Spacer(Modifier.height(8.dp))
                    RoundIconButton(Icons.AutoMirrored.Rounded.VolumeUp, T.HEAR_WORD, onSpeak, size = 64.dp, color = KColors.TealLight)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        // options
        val isPicture = q.options.firstOrNull()?.emoji != null
        Row(
            Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            q.options.forEachIndexed { i, opt ->
                val wrong = i in ui.wrong
                val reveal = ui.solved && i == q.correctIndex || (ui.wrong.size >= 2 && i == q.correctIndex)
                val bg by animateColorAsState(
                    when {
                        reveal -> KColors.GreenLight
                        wrong -> Color(0xFFF1EEF7)
                        else -> Color.White
                    },
                    label = "opt",
                )
                val shake = remember(ui.index, i) { Animatable(0f) }
                LaunchedEffect(wrong) {
                    if (wrong) {
                        repeat(2) {
                            shake.animateTo(10f, tween(50))
                            shake.animateTo(-10f, tween(100))
                        }
                        shake.animateTo(0f, tween(50))
                    }
                }
                val alpha by animateFloatAsState(if (wrong) 0.45f else 1f, label = "alpha")
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(if (isPicture || opt.letter != null) 1f else 1.25f)
                        .offset { IntOffset(shake.value.roundToInt(), 0) }
                        .graphicsLayer { this.alpha = alpha }
                        .shadow(4.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(bg)
                        .border(3.dp, if (reveal) KColors.Green else KColors.Line, RoundedCornerShape(24.dp))
                        .bouncyClick { onAnswer(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        opt.emoji != null -> Text(opt.emoji, fontSize = 56.sp)
                        opt.letter != null -> Text(opt.letter.toString(), style = readingTextStyle(64.sp))
                        opt.text != null -> Text(
                            opt.text.get(ui.withNikud),
                            style = readingTextStyle(if (q.type == GameType.MISSING_WORD) 26.sp else 30.sp).copy(textAlign = TextAlign.Center),
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ui.feedback?.let { SpeechBubble(it, color = if (ui.solved) KColors.GreenLight else KColors.OrangeLight, textStyle = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun GameFinished(stars: Int, onAgain: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Owl(mood = OwlMood.CHEER, size = 170.dp)
        Text(T.GAME_DONE, style = MaterialTheme.typography.headlineMedium, color = KColors.Ink, textAlign = TextAlign.Center)
        if (stars > 0) Text("\u2066+$stars\u2069 ⭐", style = MaterialTheme.typography.headlineMedium, color = KColors.Orange)
        Spacer(Modifier.height(20.dp))
        BigButton(T.PLAY_AGAIN, onAgain, emoji = "🔁", color = KColors.Teal, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        BigButton(T.BACK, onBack, color = KColors.Violet, height = 58.dp, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth())
    }
}

// ---------------------------------------------------------------- memory game

data class MemoryUi(
    val loading: Boolean = true,
    val withNikud: Boolean = true,
    val cards: List<MemoryCard> = emptyList(),
    val faceUp: Set<Int> = emptySet(),
    val matched: Set<Int> = emptySet(),
    val finished: Boolean = false,
    val starsAdded: Int = 0,
    val round: Int = 0,
)

class MemoryViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(MemoryUi())
    val ui: StateFlow<MemoryUi> = _ui.asStateFlow()
    private var child: ChildEntity? = null
    private var busy = false

    init {
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        val ch = c.children.active() ?: return
        child = ch
        val pairs = if (ch.level <= 1) 4 else 6
        val cards = GameGenerator(c.content.vocabulary()).memoryCards(pairs)
        _ui.value = MemoryUi(loading = false, withNikud = ch.withNikud || ch.level <= 2, cards = cards, round = _ui.value.round + 1)
    }

    fun flip(index: Int) {
        val st = _ui.value
        val card = st.cards.getOrNull(index) ?: return
        if (busy || index in st.faceUp || card.pairId in st.matched) return
        c.sounds.play(Sfx.POP, 0.5f)
        val up = st.faceUp + index
        _ui.update { it.copy(faceUp = up) }
        card.word?.let { c.speech.speakMessage(it.nikud, 0.9f) }
        if (up.size == 2) {
            val (a, b) = up.map { st.cards[it] }
            if (a.pairId == b.pairId) {
                c.sounds.play(Sfx.CORRECT)
                val matched = st.matched + a.pairId
                _ui.update { it.copy(matched = matched, faceUp = emptySet()) }
                if (matched.size == st.cards.size / 2) finish()
            } else {
                busy = true
                viewModelScope.launch {
                    delay(1100)
                    _ui.update { it.copy(faceUp = emptySet()) }
                    busy = false
                }
            }
        }
    }

    private fun finish() {
        val ch = child ?: return
        viewModelScope.launch {
            delay(600)
            val (stars, _) = c.progress.recordGame(ch)
            c.sounds.play(Sfx.FANFARE)
            _ui.update { it.copy(finished = true, starsAdded = stars) }
        }
    }

    fun restart() {
        viewModelScope.launch { start() }
    }

    override fun onCleared() {
        c.speech.stop()
        super.onCleared()
    }
}

@Composable
fun MemoryGameScreen(onBack: () -> Unit) {
    val vm = appViewModel(key = "memory") { MemoryViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(KColors.Cream)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            KidTopBar(T.GAME_MEMORY, onBack)
            if (ui.finished) {
                GameFinished(ui.starsAdded, onAgain = vm::restart, onBack = onBack)
            } else {
                Text(T.Q_MEMORY, style = MaterialTheme.typography.titleMedium, color = KColors.InkSoft, modifier = Modifier.padding(horizontal = 18.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                ) {
                    itemsIndexed(ui.cards, key = { _, card -> "${ui.round}-${card.id}" }) { i, card ->
                        val open = i in ui.faceUp || card.pairId in ui.matched
                        val rotation by animateFloatAsState(if (open) 180f else 0f, tween(320), label = "flip")
                        Box(
                            Modifier
                                .aspectRatio(0.8f)
                                .graphicsLayer {
                                    rotationY = rotation
                                    cameraDistance = 12f * density
                                }
                                .shadow(4.dp, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    when {
                                        card.pairId in ui.matched -> KColors.GreenLight
                                        rotation > 90f -> Color.White
                                        else -> KColors.Violet
                                    },
                                )
                                .bouncyClick { vm.flip(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (rotation > 90f) {
                                Box(Modifier.graphicsLayer { rotationY = 180f }, contentAlignment = Alignment.Center) {
                                    if (card.emoji != null) {
                                        Text(card.emoji, fontSize = 44.sp)
                                    } else {
                                        Text(card.word?.get(ui.withNikud).orEmpty(), style = readingTextStyle(26.sp).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(4.dp))
                                    }
                                }
                            } else {
                                Text("🦉", fontSize = 36.sp)
                            }
                        }
                    }
                }
            }
        }
        if (ui.finished) ConfettiBurst(key = ui.round)
    }
}
