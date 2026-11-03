package com.korimbekef.reading.ui.screens.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.korimbekef.reading.AppContainer
import com.korimbekef.reading.BuildConfig
import com.korimbekef.reading.core.DualText
import com.korimbekef.reading.core.HebrewText
import com.korimbekef.reading.core.Passage
import com.korimbekef.reading.data.db.PracticeWordEntity
import com.korimbekef.reading.ui.appViewModel
import com.korimbekef.reading.ui.components.PinPad
import com.korimbekef.reading.ui.components.readingTextStyle
import com.korimbekef.reading.ui.theme.KColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- passages

class ContentViewModel(c: AppContainer) : ParentBaseViewModel(c) {
    val passages: StateFlow<List<Passage>> = c.content.allPassages.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setHidden(id: String, hidden: Boolean) = viewModelScope.launch { c.settings.setPassageHidden(id, hidden) }

    fun delete(id: String) = viewModelScope.launch { c.content.deleteCustom(id) }

    fun add(title: String, level: Int, emoji: String, text: String, onDone: () -> Unit) {
        viewModelScope.launch {
            c.content.addCustom(title, level, emoji, text)
            onDone()
        }
    }
}

@Composable
fun ContentScreen(onBack: () -> Unit, onAdd: () -> Unit) {
    val vm = appViewModel { ContentViewModel(it) }
    val passages by vm.passages.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Passage?>(null) }

    ParentScaffold(title = "קטעי קריאה", onBack = onBack) {
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("הוספת קטע קריאה משלכם")
        }
        InfoText("כבו את המתג כדי להסתיר קטע מהילד. קטעים שהוספתם אפשר גם למחוק.")
        groupByLevel(passages).forEach { (title, list) ->
            SectionCard(title = title) {
                list.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.emoji, fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(p.title.plain, style = MaterialTheme.typography.bodyLarge, color = KColors.Ink)
                            InfoText("${p.lineCount} שורות · ${p.wordCount} מילים")
                        }
                        if (p.isCustom) {
                            IconButton(onClick = { toDelete = p }) { Icon(Icons.Rounded.Delete, contentDescription = "מחיקה", tint = KColors.InkSoft) }
                        }
                        Switch(checked = p.id !in settings.hiddenPassages, onCheckedChange = { visible -> vm.setHidden(p.id, !visible) })
                    }
                }
            }
        }
    }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("למחוק את \"${p.title.plain}\"?") },
            confirmButton = { TextButton(onClick = { vm.delete(p.id); toDelete = null }) { Text("מחיקה", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("ביטול") } },
        )
    }
}

private val PASSAGE_EMOJIS = listOf("📖", "🌳", "🐶", "🐱", "🚀", "🌈", "🏖️", "🎈", "🦄", "🚗", "🍎", "🌙", "⚽", "🎂", "🐠", "🏰")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddPassageScreen(onBack: () -> Unit) {
    val vm = appViewModel { ContentViewModel(it) }
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableIntStateOf(1) }
    var emoji by rememberSaveable { mutableStateOf(PASSAGE_EMOJIS[0]) }
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    ParentScaffold(title = "קטע קריאה חדש", onBack = onBack) {
        SectionCard {
            OutlinedTextField(value = title, onValueChange = { title = it.take(40) }, label = { Text("כותרת") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(3000) },
                label = { Text("הטקסט – כל שורה תהיה שורה לחיצה") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                textStyle = readingTextStyle(20.sp),
            )
            InfoText("אפשר להדביק טקסט מנוקד (למשל מ\"נקדן\" של דיקטה) – כך הילד יוכל לעבור בין מצב עם ניקוד ובלי ניקוד. ${lines.size} שורות.")
        }
        SectionCard(title = "רמה") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { l -> FilterChip(selected = level == l, onClick = { level = l }, label = { Text("רמה $l") }) }
            }
        }
        SectionCard(title = "איור") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PASSAGE_EMOJIS.forEach { e ->
                    Box(
                        Modifier
                            .size(46.dp)
                            .background(if (e == emoji) KColors.VioletLight else Color.White, CircleShape)
                            .border(2.dp, if (e == emoji) KColors.Violet else KColors.Line, CircleShape)
                            .clickable { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 24.sp) }
                }
            }
        }
        if (lines.isNotEmpty()) {
            SectionCard(title = "תצוגה מקדימה") {
                lines.take(4).forEach { Text(DualText.ofUserText(it).nikud, style = readingTextStyle(22.sp)) }
                if (lines.size > 4) InfoText("ועוד ${lines.size - 4} שורות…")
                if (!HebrewText.hasNikud(text)) InfoText("הטקסט ללא ניקוד – מתג הניקוד לא ישנה את התצוגה.")
            }
        }
        Button(
            onClick = { vm.add(title.ifBlank { "הקטע שלי" }, level, emoji, text, onBack) },
            enabled = lines.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("שמירה") }
    }
}

// ---------------------------------------------------------------- practice words

class ParentWordsViewModel(c: AppContainer) : ParentBaseViewModel(c) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val words: StateFlow<List<PracticeWordEntity>> = c.children.activeChild.flatMapLatest { ch ->
        if (ch == null) flowOf(emptyList()) else c.progress.observeWords(ch.id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun add(raw: String) {
        val word = HebrewText.normalize(raw)
        if (word.isEmpty()) return
        viewModelScope.launch {
            val child = c.children.active() ?: return@launch
            c.progress.addWord(child.id, word, HebrewText.stripNikud(word), byParent = true)
            c.progress.awardMedals(child)
        }
    }

    fun remove(id: Long) = viewModelScope.launch { c.progress.removeWord(id) }
}

@Composable
fun ParentWordsScreen(onBack: () -> Unit) {
    val vm = appViewModel { ParentWordsViewModel(it) }
    val words by vm.words.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    ParentScaffold(title = "מילים לתרגול${active?.let { " · ${it.name}" } ?: ""}", onBack = onBack) {
        SectionCard {
            InfoText("מילים שתוסיפו יופיעו ברשימת \"המילים שלי\" של הילד ובמשחק התרגול. אפשר להקליד עם ניקוד או בלי.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = input, onValueChange = { input = it.take(30) }, label = { Text("מילה") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.add(input); input = "" }, enabled = input.isNotBlank()) { Text("הוספה") }
            }
        }
        SectionCard(title = "הרשימה (${words.size})") {
            if (words.isEmpty()) InfoText("הרשימה ריקה.")
            words.forEach { w ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(w.word, style = readingTextStyle(24.sp), modifier = Modifier.weight(1f))
                    InfoText(if (w.addedByParent) "נוספה ע״י הורה" else "נשמרה ע״י הילד")
                    IconButton(onClick = { vm.remove(w.id) }) { Icon(Icons.Rounded.Delete, contentDescription = "מחיקה", tint = KColors.InkSoft) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- PIN change

class ChangePinViewModel(c: AppContainer) : ParentBaseViewModel(c)

@Composable
fun ChangePinScreen(onBack: () -> Unit) {
    val vm = appViewModel { ChangePinViewModel(it) }
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var first by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var errorKey by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    ParentScaffold(title = "קוד הורים", onBack = onBack) {
        if (done) {
            SectionCard { Text("✅ הקוד עודכן", style = MaterialTheme.typography.titleMedium, color = KColors.Ink) }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("סיום") }
            return@ParentScaffold
        }
        when (step) {
            0 -> PinPad(title = "הקישו את הקוד הנוכחי", error = error, errorKey = errorKey, resetKey = "current", onComplete = { pin ->
                scope.launch {
                    if (vm.pinMatches(pin)) {
                        step = 1
                        error = null
                    } else {
                        error = "קוד שגוי"
                        errorKey++
                    }
                }
            })
            1 -> PinPad(title = "בחרו קוד חדש", error = error, errorKey = errorKey, resetKey = "new", onComplete = { pin ->
                first = pin
                step = 2
                error = null
            })
            else -> PinPad(title = "הקישו שוב את הקוד החדש", error = error, errorKey = errorKey, resetKey = "confirm", onComplete = { pin ->
                if (pin == first) {
                    scope.launch {
                        vm.savePin(pin)
                        done = true
                    }
                } else {
                    error = "הקודים לא תואמים"
                    errorKey++
                    step = 1
                }
            })
        }
    }
}

// ---------------------------------------------------------------- about & privacy

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }
    ParentScaffold(title = "פרטיות ומידע", onBack = onBack) {
        SectionCard(title = "פרטיות ובטיחות") {
            Bullet("האפליקציה עובדת בלי אינטרנט ואין לה הרשאת אינטרנט בכלל.")
            Bullet("אין פרסומות ואין קישורים חיצוניים בממשק הילד.")
            Bullet("אין שימוש במיקרופון ואין הקלטות של הילד.")
            Bullet("כל הנתונים (פרופיל, התקדמות, מילים) נשמרים רק במכשיר, ואפשר למחוק אותם בהגדרות הילד/ה.")
            Bullet("ההקראה נעשית על ידי מנוע ההקראה של המכשיר. קולות שמסומנים \"דורש אינטרנט\" עשויים לשלוח את הטקסט לספק הקול – מומלץ לבחור קול שעובד בלי אינטרנט.")
        }
        SectionCard(title = "איך זה עובד") {
            Bullet("כל שורה בקטע ניתנת ללחיצה: יַנְשׁוּפִי מקריא אותה ומדגיש כל מילה בזמן שהיא נשמעת.")
            Bullet("בסוף השורה הילד מוזמן לקרוא לבד. האפליקציה מחכה ולא עוברת לבד לשורה הבאה.")
            Bullet("כוכבים ניתנים על התמדה ומאמץ – לא על דיוק. אין ציונים שליליים ואין עונשים.")
            Bullet("מצב \"קריאה לבד\": הילד קורא קודם, ונעזר בהקראה של מילה או שורה רק כשצריך.")
            Bullet("לחיצה ארוכה על מילה פותחת כרטיס מילה עם השמעה ושמירה לרשימת \"המילים שלי\".")
        }
        SectionCard(title = "תוכן") {
            InfoText("40 קטעי קריאה מקוריים בחמש רמות, עם ניקוד מלא ובכתיב מלא לתצוגה ללא ניקוד. מומלץ שמורה או מגיה יעברו על הניקוד לפני שימוש רחב.")
        }
        SectionCard(title = "גרסה") {
            InfoText("קוראים בכיף ${BuildConfig.VERSION_NAME}")
            TextButton(onClick = { showLicenses = !showLicenses }) { Text(if (showLicenses) "הסתרת רישיונות" else "רישיונות גופנים") }
            if (showLicenses) {
                val license = remember {
                    runCatching { context.assets.open("licenses/fonts_ofl.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
                }
                Text(license, style = MaterialTheme.typography.bodySmall, color = KColors.InkSoft)
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("• ", color = KColors.Violet)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = KColors.Ink)
    }
}
