package com.korimbekef.reading.ui.screens.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.korimbekef.reading.AppContainer
import com.korimbekef.reading.data.prefs.SyncMode
import com.korimbekef.reading.speech.EffectiveSync
import com.korimbekef.reading.speech.EngineStatus
import com.korimbekef.reading.speech.RangeSupport
import com.korimbekef.reading.speech.SpeechState
import com.korimbekef.reading.ui.SystemIntents
import com.korimbekef.reading.ui.appViewModel
import com.korimbekef.reading.ui.theme.KColors
import kotlinx.coroutines.launch

class VoiceSettingsViewModel(c: AppContainer) : ParentBaseViewModel(c) {
    val speech = c.speech.state

    private val sample = "שָׁלוֹם! אֲנִי יַנְשׁוּפִי, וַאֲנִי אוֹהֵב לִקְרֹא סִפּוּרִים."

    fun preview(voice: String?) {
        viewModelScope.launch {
            val rate = c.children.active()?.speechRate ?: 0.9f
            c.speech.preview(voice, sample, rate)
        }
    }

    fun select(voice: String) = viewModelScope.launch { c.settings.setVoice(voice) }
    fun engine(pkg: String) = viewModelScope.launch { c.settings.setEngine(pkg) }
    fun pitch(p: Float) = viewModelScope.launch { c.settings.setPitch(p) }
    fun sync(mode: SyncMode) = viewModelScope.launch { c.settings.setSyncMode(mode) }
    fun speakNikud(on: Boolean) = viewModelScope.launch { c.settings.setSpeakNikud(on) }
    fun prompts(on: Boolean) = viewModelScope.launch { c.settings.setVoicePrompts(on) }
    fun sounds(on: Boolean) = viewModelScope.launch { c.settings.setSoundEffects(on) }
    fun gender(voice: String, g: String?) = viewModelScope.launch { c.settings.setVoiceGender(voice, g) }
    fun recheck() = c.speech.recheckSync()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceSettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { VoiceSettingsViewModel(it) }
    val speech by vm.speech.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ParentScaffold(title = "קול והקראה", onBack = onBack) {
        SectionCard(title = "מצב מנוע ההקראה") {
            when (speech.status) {
                EngineStatus.INITIALIZING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text("טוען את מנוע ההקראה…")
                }
                EngineStatus.READY -> {
                    Text("✅ נמצא קול עברי", style = MaterialTheme.typography.bodyLarge, color = KColors.Ink)
                    Text(syncDescription(speech), style = MaterialTheme.typography.bodyMedium, color = KColors.InkSoft)
                }
                EngineStatus.NO_HEBREW -> Text("⚠️ במנוע ההקראה הנוכחי אין קול עברי מותקן.", color = KColors.Ink)
                EngineStatus.NO_ENGINE -> Text("⚠️ לא נמצא מנוע הקראה במכשיר.", color = KColors.Ink)
            }
            speech.notice?.let { Text(it, color = KColors.Orange, style = MaterialTheme.typography.bodySmall) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { SystemIntents.installVoiceData(context) }) { Text("התקנת קולות") }
                OutlinedButton(onClick = { SystemIntents.ttsSettings(context) }) { Text("הגדרות הקראה במכשיר") }
            }
            InfoText("מומלץ \"שירותי הדיבור של Google\" עם הקול העברי המורד למכשיר – כך ההקראה עובדת גם בלי אינטרנט.")
        }

        if (speech.engines.size > 1) {
            SectionCard(title = "מנוע הקראה") {
                speech.engines.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.engine(e.packageName) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = e.packageName == speech.enginePackage, onClick = { vm.engine(e.packageName) })
                        Text(e.label, color = KColors.Ink)
                    }
                }
            }
        }

        SectionCard(title = "בחירת קול") {
            if (speech.voices.isEmpty()) {
                InfoText(
                    if (speech.status == EngineStatus.READY) "המנוע לא מציג רשימת קולות, ההקראה תשתמש בקול העברי ברירת המחדל." else "אין קולות עבריים זמינים כרגע.",
                )
            }
            InfoText("המכשיר לא תמיד מדווח אם קול הוא נשי או גברי – האזינו לדוגמה וסמנו, כדי שיהיה קל לבחור.")
            speech.voices.forEach { v ->
                val selected = v.name == speech.currentVoice
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) KColors.VioletLight else Color.White, RoundedCornerShape(16.dp))
                        .border(2.dp, if (selected) KColors.Violet else KColors.Line, RoundedCornerShape(16.dp))
                        .clickable { vm.select(v.name) }
                        .padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = { vm.select(v.name) })
                        Column(Modifier.weight(1f)) {
                            val genderText = when (v.gender) {
                                "f" -> " · קול נשי"
                                "m" -> " · קול גברי"
                                else -> ""
                            }
                            Text("${v.label}$genderText", style = MaterialTheme.typography.titleMedium, color = KColors.Ink)
                            Text(
                                (if (v.isNetwork) "דורש אינטרנט" else "עובד בלי אינטרנט") + " · איכות ${qualityName(v.quality)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = KColors.InkSoft,
                            )
                        }
                        FilledTonalIconButton(onClick = { vm.preview(v.name) }) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "השמעת דוגמה")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 48.dp)) {
                        FilterChip(selected = v.gender == "f", onClick = { vm.gender(v.name, if (v.gender == "f") null else "f") }, label = { Text("נשי") })
                        FilterChip(selected = v.gender == "m", onClick = { vm.gender(v.name, if (v.gender == "m") null else "m") }, label = { Text("גברי") })
                    }
                }
            }
            if (speech.voices.isEmpty() && speech.status == EngineStatus.READY) {
                OutlinedButton(onClick = { vm.preview(null) }) { Text("🔊 השמעת דוגמה") }
            }
        }

        SectionCard(title = "גובה הקול") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.85f to "נמוך", 1.0f to "רגיל", 1.15f to "גבוה").forEach { (p, name) ->
                    FilterChip(selected = kotlin.math.abs(settings.pitch - p) < 0.01f, onClick = { vm.pitch(p) }, label = { Text(name) })
                }
            }
            InfoText("מהירות ההקראה נקבעת לכל ילד בנפרד (הגדרות הילד/ה) ובמסך הקריאה.")
        }

        SectionCard(title = "סנכרון הדגשת מילים") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SyncOption("אוטומטי (מומלץ)", "משתמש בדיווח המנוע על המילה הנשמעת אם הוא אמין, ואחרת מקריא מילה אחר מילה.", settings.syncMode == SyncMode.AUTO) { vm.sync(SyncMode.AUTO) }
                SyncOption("לפי דיווחי המנוע", "שורה שלמה ברצף טבעי; מתאים רק למנועים שמדווחים על כל מילה.", settings.syncMode == SyncMode.ENGINE) { vm.sync(SyncMode.ENGINE) }
                SyncOption("מילה אחר מילה", "כל מילה מוקראת בנפרד ומודגשת כשהיא מתחילה להישמע. תמיד מסונכרן, פחות זורם.", settings.syncMode == SyncMode.WORD_BY_WORD) { vm.sync(SyncMode.WORD_BY_WORD) }
            }
            TextButton(onClick = vm::recheck) { Text("בדיקה חוזרת של יכולת המנוע") }
            InfoText("ההדגשה אף פעם לא מבוססת על טיימר: היא זזה רק לפי אירועים שמגיעים ממנוע ההקראה.")
        }

        SectionCard(title = "עוד") {
            SwitchRow("הקראה לפי הטקסט המנוקד", "הגייה מדויקת יותר. כבו אם המנוע מתקשה בניקוד.", settings.speakNikud, vm::speakNikud)
            SwitchRow("הודעות עידוד קוליות", "יַנְשׁוּפִי אומר ״עכשיו תורך!״ ו״כל הכבוד!״ בקול", settings.voicePrompts, vm::prompts)
            SwitchRow("צלילי משחק", "צלילים קצרים ועדינים להצלחות", settings.soundEffects, vm::sounds)
        }
        Button(onClick = { vm.preview(speech.currentVoice) }, modifier = Modifier.fillMaxWidth()) { Text("🔊 השמעת הקול הנבחר") }
    }
}

@Composable
private fun SyncOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.Top) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.padding(top = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = KColors.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KColors.InkSoft)
        }
    }
}

private fun qualityName(q: Int): String = when {
    q >= 400 -> "גבוהה מאוד"
    q >= 300 -> "גבוהה"
    q >= 200 -> "רגילה"
    else -> "בסיסית"
}

private fun syncDescription(s: SpeechState): String {
    val mode = if (s.effectiveSync == EffectiveSync.ENGINE_RANGES) "שורה שלמה, ההדגשה לפי דיווחי המנוע" else "מילה אחר מילה (מנגנון חלופי מסונכרן)"
    val check = when (s.rangeSupport) {
        RangeSupport.UNKNOWN -> "בודקים את יכולות המנוע…"
        RangeSupport.SUPPORTED -> "המנוע מדווח על כל מילה ✓"
        RangeSupport.SUPPORTED_PLAIN -> "המנוע מדווח על כל מילה (בטקסט ללא ניקוד) ✓"
        RangeSupport.UNSUPPORTED -> "המנוע לא מדווח על מילים"
    }
    return "הדגשה: $mode. $check"
}
