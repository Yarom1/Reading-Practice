package com.korimbekef.reading.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.korimbekef.reading.AppContainer
import com.korimbekef.reading.core.Levels
import com.korimbekef.reading.core.PinHasher
import com.korimbekef.reading.data.db.ChildEntity
import com.korimbekef.reading.speech.EngineStatus
import com.korimbekef.reading.ui.SystemIntents
import com.korimbekef.reading.ui.appViewModel
import com.korimbekef.reading.ui.components.BigButton
import com.korimbekef.reading.ui.components.Owl
import com.korimbekef.reading.ui.components.OwlMood
import com.korimbekef.reading.ui.components.PinPad
import com.korimbekef.reading.ui.components.SelectableChip
import com.korimbekef.reading.ui.components.SoftCard
import com.korimbekef.reading.ui.components.bouncyClick
import com.korimbekef.reading.ui.theme.KColors
import kotlinx.coroutines.launch

val AVATARS = listOf("🦁", "🐯", "🐼", "🐨", "🦊", "🐰", "🐸", "🐵", "🦄", "🐬", "🐢", "🦋")

class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    val speech = c.speech.state

    fun sample(name: String, girl: Boolean) {
        val call = if (girl) "בּוֹאִי" else "בּוֹא"
        c.speech.speakMessage("שָׁלוֹם $name! $call נִקְרָא יַחַד.", 0.9f)
    }

    fun finish(name: String, girl: Boolean, avatar: String, level: Int, nikud: Boolean, pin: String, onDone: () -> Unit) {
        viewModelScope.launch {
            c.children.create(
                ChildEntity(
                    name = name.trim(),
                    gender = if (girl) ChildEntity.GENDER_GIRL else ChildEntity.GENDER_BOY,
                    avatar = avatar,
                    level = level,
                    maxLevel = level,
                    withNikud = nikud,
                    fontStep = if (level <= 2) 2 else 1,
                    speechRate = if (level <= 2) 0.8f else 1.0f,
                ),
            )
            val salt = PinHasher.newSalt()
            c.settings.setPin(PinHasher.hash(pin, salt), salt)
            c.settings.setOnboardingDone(true)
            onDone()
        }
    }
}

/** First-run setup, done together with a parent: child profile, starting level, voice check, parent PIN. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val vm = appViewModel { OnboardingViewModel(it) }
    val speech by vm.speech.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var girl by rememberSaveable { mutableStateOf(false) }
    var avatar by rememberSaveable { mutableStateOf(AVATARS[0]) }
    var level by rememberSaveable { mutableIntStateOf(1) }
    var nikud by rememberSaveable { mutableStateOf(true) }
    var firstPin by rememberSaveable { mutableStateOf<String?>(null) }
    var pinError by rememberSaveable { mutableStateOf<String?>(null) }
    var pinErrorKey by rememberSaveable { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(KColors.Cream)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // progress dots
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(5) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 5.dp)
                        .size(if (i == step) 26.dp else 12.dp, 12.dp)
                        .background(if (i <= step) KColors.Violet else KColors.Line, RoundedCornerShape(6.dp)),
                )
            }
        }
        AnimatedContent(targetState = step, label = "onboarding", modifier = Modifier.weight(1f)) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (current) {
                    0 -> {
                        Spacer(Modifier.height(24.dp))
                        Owl(mood = OwlMood.HAPPY, size = 180.dp)
                        Spacer(Modifier.height(16.dp))
                        Text("ברוכים הבאים ל״קוראים בכיף״!", style = MaterialTheme.typography.headlineMedium, color = KColors.Ink, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "יַנְשׁוּפִי ילווה את הילד או הילדה בדרך לקריאה עצמאית: הוא מקריא כל שורה, מדגיש כל מילה בזמן ההקראה, ומזמין לקרוא לבד – בלי לחץ ובלי ציונים.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = KColors.InkSoft,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "את ההגדרה הראשונה כדאי לעשות יחד עם הורה (בערך דקה).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KColors.InkSoft,
                            textAlign = TextAlign.Center,
                        )
                    }
                    1 -> {
                        Text("מי הקורא או הקוראת שלנו?", style = MaterialTheme.typography.headlineSmall, color = KColors.Ink)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(20) },
                            label = { Text("שם הילד או הילדה") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(18.dp))
                        Text("איך לפנות?", style = MaterialTheme.typography.titleMedium, color = KColors.Ink)
                        Text("כדי שיַנְשׁוּפִי ידבר בלשון המתאימה (״בוא״ או ״בואי״).", style = MaterialTheme.typography.bodySmall, color = KColors.InkSoft, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SelectableChip("בן 👦", !girl, { girl = false })
                            SelectableChip("בת 👧", girl, { girl = true })
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("בחרו דמות", style = MaterialTheme.typography.titleMedium, color = KColors.Ink)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AVATARS.forEach { a ->
                                Box(
                                    Modifier
                                        .size(60.dp)
                                        .background(if (a == avatar) KColors.VioletLight else Color.White, CircleShape)
                                        .border(3.dp, if (a == avatar) KColors.Violet else KColors.Line, CircleShape)
                                        .bouncyClick { avatar = a },
                                    contentAlignment = Alignment.Center,
                                ) { Text(a, fontSize = 32.sp) }
                            }
                        }
                    }
                    2 -> {
                        Text("מאיזו רמה להתחיל?", style = MaterialTheme.typography.headlineSmall, color = KColors.Ink)
                        Text("לא בטוחים? התחילו ברמה 1 – תמיד אפשר לשנות באזור ההורים.", style = MaterialTheme.typography.bodyMedium, color = KColors.InkSoft, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Levels.all.forEach { info ->
                            val selected = info.number == level
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                                    .background(if (selected) KColors.levelSoft(info.number) else Color.White, RoundedCornerShape(20.dp))
                                    .border(3.dp, if (selected) KColors.level(info.number) else KColors.Line, RoundedCornerShape(20.dp))
                                    .bouncyClick { level = info.number }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(info.emoji, fontSize = 30.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("רמה ${info.number} · ${info.name.plain}", style = MaterialTheme.typography.titleMedium, color = KColors.Ink)
                                    Text(info.description, style = MaterialTheme.typography.bodySmall, color = KColors.InkSoft)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("להציג טקסט עם ניקוד", style = MaterialTheme.typography.titleMedium, color = KColors.Ink)
                                Text("מומלץ למתחילים. הילד יכול להחליף בכל רגע.", style = MaterialTheme.typography.bodySmall, color = KColors.InkSoft)
                            }
                            Switch(checked = nikud, onCheckedChange = { nikud = it })
                        }
                    }
                    3 -> {
                        Text("בדיקת קול", style = MaterialTheme.typography.headlineSmall, color = KColors.Ink)
                        Spacer(Modifier.height(16.dp))
                        SoftCard(Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                when (speech.status) {
                                    EngineStatus.INITIALIZING -> {
                                        CircularProgressIndicator()
                                        Spacer(Modifier.height(8.dp))
                                        Text("מחפשים קול עברי במכשיר…", color = KColors.InkSoft)
                                    }
                                    EngineStatus.READY -> {
                                        Text("✅ נמצא קול עברי", style = MaterialTheme.typography.titleLarge, color = KColors.Ink)
                                        Spacer(Modifier.height(6.dp))
                                        Text("לחצו כדי לשמוע דוגמה. אם לא שומעים – הגבירו את עוצמת הקול.", color = KColors.InkSoft, textAlign = TextAlign.Center)
                                        Spacer(Modifier.height(12.dp))
                                        BigButton("השמעת דוגמה", { vm.sample(name, girl) }, emoji = "🔊", color = KColors.Teal, height = 60.dp)
                                    }
                                    EngineStatus.NO_HEBREW, EngineStatus.NO_ENGINE -> {
                                        Text("⚠️ לא נמצא קול עברי", style = MaterialTheme.typography.titleLarge, color = KColors.Ink)
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "ההקראה משתמשת במנוע ההקראה של המכשיר. מומלץ להתקין את \"שירותי הדיבור של Google\" ולהוריד את הקול העברי. אפשר להמשיך גם בלי קול ולהשלים אחר כך.",
                                            color = KColors.InkSoft,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        BigButton("התקנת קול עברי", { SystemIntents.installVoiceData(context) }, color = KColors.Orange, height = 56.dp)
                                        TextButton(onClick = { SystemIntents.ttsSettings(context) }) { Text("הגדרות ההקראה של המכשיר") }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "בהגדרות ההורים אפשר לבחור קול (נשי/גברי, לפי הקולות במכשיר), מהירות וגובה קול.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KColors.InkSoft,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> {
                        PinPad(
                            title = if (firstPin == null) "בחרו קוד הורים" else "הקישו שוב את הקוד",
                            subtitle = "הקוד שומר על אזור ההורים (הגדרות ודוחות) מפני לחיצות של הילדים.",
                            error = pinError,
                            resetKey = firstPin ?: "first",
                            errorKey = pinErrorKey,
                            onComplete = { entered ->
                                val first = firstPin
                                if (first == null) {
                                    firstPin = entered
                                    pinError = null
                                } else if (first == entered) {
                                    vm.finish(name.ifBlank { "חבר" }, girl, avatar, level, nikud, entered, onDone)
                                } else {
                                    firstPin = null
                                    pinError = "הקודים לא תואמים, נסו שוב"
                                    pinErrorKey++
                                }
                            },
                        )
                    }
                }
            }
        }
        if (step < 4) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    TextButton(onClick = { step-- }) { Text("הקודם", style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.width(12.dp))
                }
                BigButton(
                    text = if (step == 0) "בואו נתחיל" else "המשך",
                    onClick = { step++ },
                    enabled = step != 1 || name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    height = 62.dp,
                )
            }
        } else {
            TextButton(onClick = { step = 3; firstPin = null }, modifier = Modifier.padding(16.dp)) {
                Text("הקודם", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
