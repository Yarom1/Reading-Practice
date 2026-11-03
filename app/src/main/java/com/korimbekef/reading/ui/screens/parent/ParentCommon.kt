package com.korimbekef.reading.ui.screens.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.korimbekef.reading.AppContainer
import com.korimbekef.reading.core.PinHasher
import com.korimbekef.reading.data.prefs.AppSettings
import com.korimbekef.reading.ui.theme.KColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Shared state for the parent area screens. */
open class ParentBaseViewModel(protected val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val children = c.children.children.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val active = c.children.activeChild.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    suspend fun pinMatches(pin: String): Boolean {
        val s = c.settings.current()
        val hash = s.pinHash ?: return false
        val salt = s.pinSalt ?: return false
        return PinHasher.hash(pin, salt) == hash
    }

    suspend fun savePin(pin: String) {
        val salt = PinHasher.newSalt()
        c.settings.setPin(PinHasher.hash(pin, salt), salt)
    }
}

@Composable
fun ParentScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF7F4FC))) {
        TopAppBar(
            title = { Text(title, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "חזרה") }
            },
            actions = { actions() },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF7F4FC)),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.fillMaxWidth().widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(20.dp))
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, color = KColors.Ink)
        content()
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = KColors.Ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KColors.InkSoft)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = KColors.InkSoft)
}

fun formatMinutes(seconds: Int): String {
    val minutes = seconds / 60
    return if (minutes < 60) "$minutes דק׳" else "${minutes / 60} שע׳ ${minutes % 60} דק׳"
}
