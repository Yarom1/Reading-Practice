package com.korimbekef.reading.ui.screens.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korimbekef.reading.AppContainer
import com.korimbekef.reading.audio.Sfx
import com.korimbekef.reading.core.HebrewText
import com.korimbekef.reading.core.MascotStage
import com.korimbekef.reading.core.MedalDef
import com.korimbekef.reading.core.Passage
import com.korimbekef.reading.core.Rewards
import com.korimbekef.reading.core.VocabWord
import com.korimbekef.reading.data.db.ChildEntity
import com.korimbekef.reading.data.db.SessionEntity
import com.korimbekef.reading.speech.EngineStatus
import com.korimbekef.reading.speech.PlayStatus
import com.korimbekef.reading.speech.Playback
import com.korimbekef.reading.speech.SpeakRequest
import com.korimbekef.reading.ui.T
import com.korimbekef.reading.ui.screens.recommendPassage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReadingMode {
    /** Listen-and-repeat: the app reads a line, then invites the child to read it. */
    TOGETHER,

    /** The child reads first and asks for help with a single word or line when needed. */
    ALONE,
}

object FontSteps {
    val sizes = listOf(24, 30, 38, 46)
}

object Speeds {
    val rates = listOf(0.6f, 0.8f, 1.0f, 1.2f)
    fun nearestIndex(rate: Float): Int = rates.indices.minByOrNull { kotlin.math.abs(rates[it] - rate) } ?: 1
}

data class WordSheet(
    val line: Int,
    val word: Int,
    val nikud: String,
    val plain: String,
    val vocab: VocabWord?,
    val saved: Boolean,
)

data class CompletionUi(
    val starsAdded: Int,
    val passageStars: Int,
    val medals: List<MedalDef>,
    val newOwlStage: MascotStage?,
    val nextPassageId: String?,
)

data class ReadingUi(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val passage: Passage? = null,
    val girl: Boolean = false,
    val gamification: Boolean = true,
    val withNikud: Boolean = true,
    val fontStep: Int = 1,
    val speedIndex: Int = 1,
    val mode: ReadingMode = ReadingMode.TOGETHER,
    val focusLine: Int = 0,
    val playingLine: Int = -1,
    val playingWord: Int = -1,
    val playStatus: PlayStatus = PlayStatus.IDLE,
    val playTag: String = "",
    /** Line for which the "your turn" panel is shown, or -1. */
    val turnLine: Int = -1,
    val turnText: String = "",
    val praise: String? = null,
    val praiseKey: Int = 0,
    val starKey: Int = 0,
    val linesRead: Set<Int> = emptySet(),
    val linesHeard: Set<Int> = emptySet(),
    val wholeStoryPrompt: Boolean = false,
    val completion: CompletionUi? = null,
    val speechStatus: EngineStatus = EngineStatus.INITIALIZING,
    val volumeHintKey: Int = 0,
    val wordSheet: WordSheet? = null,
    val sessionStars: Int = 0,
) {
    val lineCount: Int get() = passage?.lineCount ?: 0
    val speechAvailable: Boolean get() = speechStatus == EngineStatus.READY || speechStatus == EngineStatus.INITIALIZING
}

/**
 * Drives the reading screen: line playback with highlighting, the "your turn" flow, stars,
 * completion, and active-time tracking. The child is never moved to the next line automatically
 * after a line was read aloud — the app waits for "I finished reading".
 */
class ReadingViewModel(
    private val c: AppContainer,
    private val passageId: String,
    private val restart: Boolean,
) : ViewModel() {

    private val _ui = MutableStateFlow(ReadingUi())
    val ui: StateFlow<ReadingUi> = _ui.asStateFlow()

    private var child: ChildEntity? = null
    private var passage: Passage? = null
    private var session: SessionEntity? = null
    private var requestId = -1L
    private var lastAllLine = -1
    private var savedPlain: Set<String> = emptySet()
    private var starsAtStart = 0
    private val sessionMedals = mutableListOf<MedalDef>()

    // active time tracking
    private var visible = false
    private var lastInteraction = System.currentTimeMillis()
    private var unsavedSeconds = 0

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch { c.speech.playback.collect { onPlayback(it) } }
        viewModelScope.launch { c.speech.state.collect { s -> _ui.update { it.copy(speechStatus = s.status) } } }
        viewModelScope.launch { ticker() }
    }

    private suspend fun load() {
        val ch = c.children.active()
        val p = c.content.passage(passageId)
        if (ch == null || p == null || p.lines.isEmpty()) {
            _ui.update { it.copy(loading = false, notFound = true) }
            return
        }
        child = ch
        passage = p
        starsAtStart = c.progress.observeSummary(ch.id).first().totalStars
        val s = c.progress.openSession(ch.id, p.id, forceNew = restart)
        session = s
        val read = s.readSet.filter { it in p.lines.indices }.toSet()
        val focus = p.lines.indices.firstOrNull { it !in read } ?: 0
        _ui.update {
            it.copy(
                loading = false,
                passage = p,
                girl = ch.isGirl,
                gamification = ch.gamification,
                withNikud = ch.withNikud,
                fontStep = ch.fontStep.coerceIn(0, FontSteps.sizes.lastIndex),
                speedIndex = Speeds.nearestIndex(ch.speechRate),
                linesRead = read,
                linesHeard = s.heardSet.filter { i -> i in p.lines.indices }.toSet(),
                focusLine = focus,
                sessionStars = s.starsEarned,
            )
        }
        viewModelScope.launch {
            c.progress.observeWords(ch.id).collect { list -> savedPlain = list.map { it.plain }.toSet() }
        }
    }

    // ------------------------------------------------------------------ playback

    private fun onPlayback(pb: Playback) {
        if (pb.requestId != requestId) return
        when (pb.status) {
            PlayStatus.SPEAKING -> {
                lastInteraction = System.currentTimeMillis()
                if (pb.tag == SpeakRequest.TAG_ALL && pb.line >= 0 && pb.line != lastAllLine) {
                    lastAllLine = pb.line
                    recordListen(pb.line)
                }
                _ui.update {
                    it.copy(
                        playStatus = PlayStatus.SPEAKING,
                        playingLine = pb.line,
                        playingWord = pb.word,
                        playTag = pb.tag,
                        focusLine = if (pb.tag == SpeakRequest.TAG_ALL && pb.line >= 0) pb.line else it.focusLine,
                    )
                }
            }
            PlayStatus.PAUSED -> _ui.update { it.copy(playStatus = PlayStatus.PAUSED, playingLine = pb.line, playingWord = pb.word) }
            PlayStatus.DONE -> {
                _ui.update { it.copy(playStatus = PlayStatus.DONE, playingLine = -1, playingWord = -1) }
                onFinished(pb.tag, pb.line)
            }
            PlayStatus.IDLE -> _ui.update { it.copy(playStatus = PlayStatus.IDLE, playingLine = -1, playingWord = -1) }
            PlayStatus.ERROR -> {
                _ui.update { it.copy(playStatus = PlayStatus.ERROR, playingLine = -1, playingWord = -1) }
                // Without a working voice the child can still read: show the invitation anyway.
                if (pb.tag == SpeakRequest.TAG_LINE || pb.tag == SpeakRequest.TAG_TOGETHER) showTurn(pb.line.coerceAtLeast(0), speak = false)
            }
        }
    }

    private fun onFinished(tag: String, line: Int) {
        lastInteraction = System.currentTimeMillis()
        when (tag) {
            SpeakRequest.TAG_LINE, SpeakRequest.TAG_TOGETHER -> if (_ui.value.mode == ReadingMode.TOGETHER) showTurn(line, speak = true)
            SpeakRequest.TAG_ALL -> {
                lastAllLine = -1
                _ui.update { it.copy(wholeStoryPrompt = true, focusLine = 0) }
                speakPrompt(T.READ_ALL_DONE.of(_ui.value.girl), requestId)
            }
        }
    }

    private fun showTurn(line: Int, speak: Boolean) {
        val text = T.pick(T.TURN_PROMPTS).of(_ui.value.girl)
        _ui.update { it.copy(turnLine = line, turnText = text, focusLine = line) }
        if (speak) speakPrompt(text, requestId)
    }

    /** Speaks an encouragement shortly after, unless the child already moved on. */
    private fun speakPrompt(text: String, forRequest: Long) {
        viewModelScope.launch {
            if (!c.settings.current().voicePrompts) return@launch
            delay(300)
            if (requestId == forRequest && _ui.value.playStatus != PlayStatus.SPEAKING) {
                c.speech.speakMessage(text, Speeds.rates[_ui.value.speedIndex].coerceAtLeast(0.85f))
            }
        }
    }

    private fun rate(slow: Boolean = false): Float = Speeds.rates[_ui.value.speedIndex] * if (slow) 0.8f else 1f

    fun playLine(line: Int, together: Boolean = false) {
        val p = passage ?: return
        if (line !in p.lines.indices) return
        touch()
        checkVolume()
        if (!_ui.value.speechAvailable) {
            c.speech.stop()
            showTurn(line, speak = false)
            return
        }
        val tag = if (together) SpeakRequest.TAG_TOGETHER else SpeakRequest.TAG_LINE
        requestId = c.speech.speak(
            SpeakRequest(
                lines = p.lines,
                startLine = line,
                endLine = line,
                rate = rate(slow = together),
                tag = tag,
                forceWordByWord = together,
            ),
        )
        _ui.update {
            it.copy(
                focusLine = line,
                turnLine = -1,
                wholeStoryPrompt = false,
                playingLine = line,
                playingWord = -1,
                playStatus = PlayStatus.SPEAKING,
                playTag = tag,
            )
        }
        recordListen(line)
    }

    fun readAll() {
        val p = passage ?: return
        touch()
        checkVolume()
        if (!_ui.value.speechAvailable) return
        lastAllLine = -1
        requestId = c.speech.speak(
            SpeakRequest(lines = p.lines, startLine = 0, endLine = p.lines.lastIndex, rate = rate(), tag = SpeakRequest.TAG_ALL),
        )
        _ui.update { it.copy(turnLine = -1, wholeStoryPrompt = false, focusLine = 0, playStatus = PlayStatus.SPEAKING, playTag = SpeakRequest.TAG_ALL, playingLine = 0, playingWord = -1) }
    }

    fun speakWord(line: Int, word: Int) {
        val p = passage ?: return
        if (line !in p.lines.indices || word < 0) return
        touch()
        if (!_ui.value.speechAvailable) return
        requestId = c.speech.speak(
            SpeakRequest(lines = p.lines, startLine = line, startWord = word, rate = rate(), tag = SpeakRequest.TAG_WORD, singleWord = true),
        )
        _ui.update { it.copy(focusLine = line, playingLine = line, playingWord = -1, playStatus = PlayStatus.SPEAKING, playTag = SpeakRequest.TAG_WORD) }
    }

    fun onLineTap(line: Int) {
        touch()
        if (_ui.value.mode == ReadingMode.TOGETHER) {
            playLine(line)
        } else {
            c.speech.stop()
            _ui.update { it.copy(focusLine = line, turnLine = -1) }
        }
    }

    fun onWordTap(line: Int, word: Int) {
        if (_ui.value.mode == ReadingMode.TOGETHER || word < 0) onLineTap(line) else speakWord(line, word)
    }

    fun onPlayPause() {
        touch()
        val st = _ui.value
        when (st.playStatus) {
            PlayStatus.SPEAKING -> c.speech.pause()
            PlayStatus.PAUSED -> c.speech.resume()
            else -> playLine(st.focusLine)
        }
    }

    fun onReplay() = playLine(_ui.value.focusLine)

    fun onNextLine() = moveFocus(+1)

    fun onPrevLine() = moveFocus(-1)

    private fun moveFocus(delta: Int) {
        val st = _ui.value
        val target = (st.focusLine + delta).coerceIn(0, (st.lineCount - 1).coerceAtLeast(0))
        if (st.mode == ReadingMode.TOGETHER) {
            playLine(target)
        } else {
            c.speech.stop()
            _ui.update { it.copy(focusLine = target, turnLine = -1) }
        }
    }

    fun onListenAgain() {
        val st = _ui.value
        playLine(if (st.turnLine >= 0) st.turnLine else st.focusLine)
    }

    fun onReadTogether() {
        val st = _ui.value
        val line = if (st.turnLine >= 0) st.turnLine else st.focusLine
        viewModelScope.launch {
            if (c.settings.current().voicePrompts && st.speechAvailable) {
                // short spoken intro, then the slow word-by-word reading
                val prompt = T.TOGETHER_PROMPT.of(st.girl)
                c.speech.speakMessage(prompt, 0.9f)
                delay((c.speech.estimatedDurationMs(prompt, 0.9f) ?: 2400L) + 250L)
            }
            playLine(line, together = true)
        }
    }

    // ------------------------------------------------------------------ child reports reading

    fun onDoneReading() {
        val st = _ui.value
        markRead(if (st.turnLine >= 0) st.turnLine else st.focusLine)
    }

    fun markRead(line: Int) {
        val p = passage ?: return
        val ch = child ?: return
        val s = session ?: return
        if (line !in p.lines.indices) return
        touch()
        c.speech.stop()
        // Update the screen right away; the database write follows. Nothing below may undo a
        // newer action (e.g. the child already tapped the next line).
        val before = _ui.value.linesRead
        val read = before + line
        val isNew = line !in before
        val next = (line + 1 until p.lineCount).firstOrNull { it !in read } ?: p.lines.indices.firstOrNull { it !in read }
        val praise = T.pick(T.PRAISE).of(ch.isGirl)
        if (isNew) c.sounds.play(if (ch.gamification) Sfx.STAR else Sfx.CORRECT)
        _ui.update {
            it.copy(
                linesRead = read,
                turnLine = if (it.turnLine == line) -1 else it.turnLine,
                praise = praise,
                praiseKey = it.praiseKey + 1,
                starKey = if (isNew && ch.gamification) it.starKey + 1 else it.starKey,
                focusLine = next ?: line,
            )
        }
        val requestAtTap = requestId
        viewModelScope.launch {
            val (updated, _) = c.progress.recordLineRead(s, line, p.lines[line].wordCount, ch.gamification)
            session = updated
            _ui.update { it.copy(sessionStars = updated.starsEarned) }
            sessionMedals += c.progress.awardMedals(ch)
            if (read.size >= p.lineCount) {
                complete()
            } else if (
                c.settings.current().voicePrompts && _ui.value.speechAvailable &&
                requestId == requestAtTap && _ui.value.playStatus != PlayStatus.SPEAKING
            ) {
                c.speech.speakMessage(praise, 0.95f)
            }
        }
    }

    fun onWholeStoryRead() {
        val p = passage ?: return
        val ch = child ?: return
        val s = session ?: return
        touch()
        c.speech.stop()
        viewModelScope.launch {
            val (updated, _) = c.progress.recordAllLinesRead(s, p, ch.gamification)
            session = updated
            _ui.update { it.copy(linesRead = p.lines.indices.toSet(), wholeStoryPrompt = false, sessionStars = updated.starsEarned) }
            complete()
        }
    }

    private suspend fun complete() {
        val ch = child ?: return
        val s = session ?: return
        val p = passage ?: return
        val result = c.progress.completeSession(s, ch)
        sessionMedals += result.newMedals
        val summary = c.progress.observeSummary(ch.id).first()
        val before = MascotStage.forStars(starsAtStart)
        val after = MascotStage.forStars(summary.totalStars)
        val passages = c.content.visiblePassages.first()
        val next = recommendPassage(ch, passages, summary.progress, excludeId = p.id)
        c.sounds.play(Sfx.FANFARE)
        _ui.update {
            it.copy(
                completion = CompletionUi(
                    starsAdded = (session?.starsEarned ?: 0) + result.starsAdded,
                    passageStars = Rewards.passageStars(result.timesCompleted),
                    medals = sessionMedals.distinctBy { m -> m.code },
                    newOwlStage = if (after != before && ch.gamification) after else null,
                    nextPassageId = next?.id,
                ),
                turnLine = -1,
                wholeStoryPrompt = false,
            )
        }
        if (c.settings.current().voicePrompts && _ui.value.speechAvailable) {
            c.speech.speakMessage(T.COMPLETED.of(ch.isGirl), 0.95f)
        }
    }

    // ------------------------------------------------------------------ settings in the screen

    fun toggleNikud() = _ui.update { it.copy(withNikud = !it.withNikud) }

    fun setFontStep(step: Int) {
        _ui.update { it.copy(fontStep = step) }
        updateChild { it.copy(fontStep = step) }
    }

    fun setSpeed(index: Int) {
        _ui.update { it.copy(speedIndex = index) }
        updateChild { it.copy(speechRate = Speeds.rates[index]) }
    }

    fun setMode(mode: ReadingMode) {
        c.speech.stop()
        _ui.update { it.copy(mode = mode, turnLine = -1, wholeStoryPrompt = false) }
    }

    private fun updateChild(change: (ChildEntity) -> ChildEntity) {
        val ch = child ?: return
        val updated = change(ch)
        child = updated
        viewModelScope.launch { c.children.update(updated) }
    }

    // ------------------------------------------------------------------ word card

    fun openWord(line: Int, word: Int) {
        val p = passage ?: return
        if (line !in p.lines.indices || word < 0) return
        val l = p.lines[line]
        if (word >= l.wordCount) return
        val nikud = l.nikudTokens.speechWord(word)
        val plain = l.plainTokens.speechWord(word)
        touch()
        viewModelScope.launch {
            val vocab = c.content.dictionary().find(nikud, plain)
            _ui.update {
                it.copy(wordSheet = WordSheet(line, word, nikud, plain, vocab, HebrewText.lettersOnly(plain) in savedPlain.map { s -> HebrewText.lettersOnly(s) }.toSet()))
            }
        }
    }

    fun closeWord() = _ui.update { it.copy(wordSheet = null) }

    fun hearWord() {
        val w = _ui.value.wordSheet ?: return
        speakWord(w.line, w.word)
    }

    fun toggleSaveWord() {
        val w = _ui.value.wordSheet ?: return
        val ch = child ?: return
        viewModelScope.launch {
            if (w.saved) {
                c.progress.removeWordByPlain(ch.id, w.plain)
            } else {
                c.progress.addWord(ch.id, w.nikud, w.plain, byParent = false)
                c.sounds.play(Sfx.POP)
                sessionMedals += c.progress.awardMedals(ch)
            }
            _ui.update { it.copy(wordSheet = w.copy(saved = !w.saved)) }
        }
    }

    // ------------------------------------------------------------------ restart

    fun restartStory() {
        val ch = child ?: return
        val p = passage ?: return
        c.speech.stop()
        viewModelScope.launch {
            sessionMedals.clear()
            starsAtStart = c.progress.observeSummary(ch.id).first().totalStars
            val s = c.progress.openSession(ch.id, p.id, forceNew = true)
            session = s
            _ui.update {
                it.copy(
                    completion = null,
                    linesRead = emptySet(),
                    linesHeard = emptySet(),
                    focusLine = 0,
                    turnLine = -1,
                    wholeStoryPrompt = false,
                    sessionStars = 0,
                )
            }
        }
    }

    // ------------------------------------------------------------------ bookkeeping

    private fun recordListen(line: Int) {
        val p = passage ?: return
        val s = session ?: return
        viewModelScope.launch {
            val updated = c.progress.recordListen(s, line, p.lines[line].wordCount)
            session = updated
            _ui.update { it.copy(linesHeard = updated.heardSet.filter { i -> i in p.lines.indices }.toSet()) }
        }
    }

    private fun checkVolume() {
        if (c.sounds.isMediaMuted()) _ui.update { it.copy(volumeHintKey = it.volumeHintKey + 1) }
    }

    private fun touch() {
        lastInteraction = System.currentTimeMillis()
    }

    fun onVisible(isVisible: Boolean) {
        visible = isVisible
        if (isVisible) {
            touch()
        } else {
            if (_ui.value.playStatus == PlayStatus.SPEAKING) c.speech.pause()
            flushTime()
        }
    }

    private suspend fun ticker() {
        while (true) {
            delay(1000)
            if (visible && child != null && System.currentTimeMillis() - lastInteraction < IDLE_LIMIT_MS) {
                unsavedSeconds++
                if (unsavedSeconds >= 15) flushTime()
            }
        }
    }

    private fun flushTime() {
        val seconds = unsavedSeconds
        val ch = child ?: return
        if (seconds <= 0) return
        unsavedSeconds = 0
        val sessionId = session?.id
        // App scope: must survive the screen closing.
        c.scope.launch { c.progress.addTime(ch.id, sessionId, seconds) }
    }

    override fun onCleared() {
        c.speech.stop()
        flushTime()
        super.onCleared()
    }

    private companion object {
        /** Time without any interaction or speech after which practice time stops counting. */
        const val IDLE_LIMIT_MS = 2 * 60 * 1000L
    }
}
