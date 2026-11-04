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
import com.korimbekef.reading.speech.SpeechController
import com.korimbekef.reading.speech.SpeechState
import com.korimbekef.reading.ui.T
import com.korimbekef.reading.ui.SystemIntents
import com.korimbekef.reading.ui.appViewModel
import com.korimbekef.reading.ui.theme.KColors
import kotlinx.coroutines.launch

class VoiceSettingsViewModel(c: AppContainer) : ParentBaseViewModel(c) {
    val speech = c.speech.state

    fun preview(voice: String?) {
        viewModelScope.launch {
            val rate = c.children.active()?.speechRate ?: 0.85f
            c.speech.preview(voice, T.VOICE_SAMPLE, rate)
        }
    }

    fun useRecorded(on: Boolean) = viewModelScope.launch { c.settings.setUseRecordedVoice(on) }
    fun select(voice: String) = viewModelScope.launch { c.settings.setVoice(voice) }
    fun engine(pkg: String) = viewModelScope.launch { c.settings.setEngine(pkg) }
    fun pitch(p: Float) = viewModelScope.launch { c.settings.setPitch(p) }
    fun sync(mode: SyncMode) = viewModelScope.launch { c.settings.setSyncMode(mode) }
    fun speakNikud(on: Boolean) = viewModelScope.launch { c.settings.setSpeakNikud(on) }
    fun prompts(on: Boolean) = viewModelScope.launch { c.settings.setVoicePrompts(on) }
    fun sounds(on: Boolean) = viewModelScope.launch { c.settings.setSoundEffects(on) }
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
        SectionCard(title = "הקול של האפליקציה") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🎙️ נועה – קול מוקלט", style = MaterialTheme.typography.titleMedium, color = KColors.Ink)
                    Text(
                        "מקריאה את כל הסיפורים, ההודעות והמילים שבאפליקציה. ההטעמה (מלעיל / מלרע) של כל מילה נבדקה, וההדגשה צמודה בדיוק למילה שנשמעת.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KColors.InkSoft,
                    )
                }
                if (speech.recorded) {
                    FilledTonalIconButton(onClick = { vm.preview(SpeechController.RECORDED_VOICE) }) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "השמעת דוגמה")
                    }
                }
            }
            SwitchRow(
                "להקריא בקול המוקלט (מומלץ)",
                "סיפורים ומילים שתוסיפו בעצמכם יוקראו בקול של המכשיר.",
                settings.useRecordedVoice,
                vm::useRecorded,
            )
            if (settings.useRecordedVoice && !speech.recorded) {
                InfoText("בגרסה הזו אין הקלטות, ולכן משתמשים בקול של המכשיר.")
            }
        }

        SectionCard(title = "קול המכשיר – לטקסטים שתוסיפו") {
            when (speech.deviceStatus) {
                EngineStatus.INITIALIZING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text("טוען את מנוע ההקראה…")
                }
                EngineStatus.READY -> {
                    Text("✅ נמצא קול עברי במכשיר", style = MaterialTheme.typography.bodyLarge, color = KColors.Ink)
                    Text(syncDescription(speech), style = MaterialTheme.typography.bodyMedium, color = KColors.InkSoft)
                }
                EngineStatus.NO_HEBREW -> Text("⚠️ במנוע ההקראה של המכשיר אין קול עברי מותקן.", color = KColors.Ink)
                EngineStatus.NO_ENGINE -> Text("⚠️ לא נמצא מנוע הקראה במכשיר.", color = KColors.Ink)
            }
            speech.notice?.let { Text(it, color = KColors.Orange, style = MaterialTheme.typography.bodySmall) }
            InfoText(
                "קול המכשיר משמש רק לסיפורים ולמילים שהוספתם בעצמכם (או לכל הטקסטים, אם תכבו את הקול המוקלט). " +
                    "מומלץ \"שירותי הדיבור של Google\" עם הקול העברי המורד למכשיר – כך ההקראה עובדת גם בלי אינטרנט. " +
                    "קולות המכשיר לא תמיד מטעימים נכון.",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { SystemIntents.installVoiceData(context) }) { Text("התקנת קולות") }
                OutlinedButton(onClick = { SystemIntents.ttsSettings(context) }) { Text("הגדרות הקראה במכשיר") }
            }
        }

        if (speech.engines.size > 1) {
            SectionCard(title = "מנוע הקראה במכשיר") {
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

        SectionCard(title = "בחירת קול במכשיר") {
            if (speech.voices.isEmpty()) {
                InfoText(
                    if (speech.deviceStatus == EngineStatus.READY) "המנוע לא מציג רשימת קולות, ההקראה תשתמש בקול העברי ברירת המחדל." else "אין קולות עבריים זמינים כרגע.",
                )
            } else {
                InfoText("האזינו לדוגמה של כל קול ובחרו את זה שנשמע לכם הכי טוב.")
            }
            speech.voices.forEach { v ->
                val selected = v.name == speech.currentVoice
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) KColors.VioletLight else Color.White, RoundedCornerShape(16.dp))
                        .border(2.dp, if (selected) KColors.Violet else KColors.Line, RoundedCornerShape(16.dp))
                        .clickable { vm.select(v.name) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = { vm.select(v.name) })
                    Column(Modifier.weight(1f)) {
                        Text(v.label, style = MaterialTheme.typography.titleMedium, color = KColors.Ink)
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
            }
            if (speech.voices.isEmpty() && speech.deviceStatus == EngineStatus.READY) {
                OutlinedButton(onClick = { vm.preview(null) }) { Text("🔊 השמעת דוגמה") }
            }
        }

        SectionCard(title = "גובה קול המכשיר") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.85f to "נמוך", 1.0f to "רגיל", 1.15f to "גבוה").forEach { (p, name) ->
                    FilterChip(selected = kotlin.math.abs(settings.pitch - p) < 0.01f, onClick = { vm.pitch(p) }, label = { Text(name) })
                }
            }
            InfoText("מהירות ההקראה (גם של הקול המוקלט) נקבעת לכל ילד בנפרד – בהגדרות הילד/ה ובמסך הקריאה.")
        }

        SectionCard(title = "הדגשת מילים בקול המכשיר") {
            InfoText("בקול המוקלט ההדגשה תמיד מדויקת. ההגדרות כאן חלות רק על קול המכשיר.")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SyncOption("אוטומטי (מומלץ)", "משתמש בדיווח המנוע על המילה הנשמעת אם הוא אמין, ואחרת מקריא מילה אחר מילה.", settings.syncMode == SyncMode.AUTO) { vm.sync(SyncMode.AUTO) }
                SyncOption("לפי דיווחי המנוע", "שורה שלמה ברצף טבעי; מתאים רק למנועים שמדווחים על כל מילה.", settings.syncMode == SyncMode.ENGINE) { vm.sync(SyncMode.ENGINE) }
                SyncOption("מילה אחר מילה", "כל מילה מוקראת בנפרד ומודגשת כשהיא מתחילה להישמע. תמיד מסונכרן, פחות זורם.", settings.syncMode == SyncMode.WORD_BY_WORD) { vm.sync(SyncMode.WORD_BY_WORD) }
            }
            TextButton(onClick = vm::recheck) { Text("בדיקה חוזרת של יכולת המנוע") }
        }

        SectionCard(title = "עוד") {
            SwitchRow("קול המכשיר קורא את הטקסט המנוקד", "הגייה מדויקת יותר. כבו אם המנוע מתקשה בניקוד.", settings.speakNikud, vm::speakNikud)
            SwitchRow("הודעות עידוד קוליות", "״עכשיו תורך!״ ו״כל הכבוד!״ בקול", settings.voicePrompts, vm::prompts)
            SwitchRow("צלילי משחק", "צלילים קצרים ועדינים להצלחות", settings.soundEffects, vm::sounds)
        }
        Button(
            onClick = { vm.preview(if (speech.recorded) SpeechController.RECORDED_VOICE else speech.currentVoice) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("🔊 השמעת הקול שמקריא את הסיפורים") }
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
