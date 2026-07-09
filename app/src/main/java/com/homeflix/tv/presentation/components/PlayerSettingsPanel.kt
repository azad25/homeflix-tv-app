package com.homeflix.tv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homeflix.tv.presentation.theme.PrimeBlue
import com.homeflix.tv.presentation.theme.PrimeTextDim
import com.homeflix.tv.presentation.theme.TextPrimary
import kotlinx.coroutines.delay

/**
 * One selectable entry inside the player settings drawer.
 */
data class SettingsOption(
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit
)

data class SettingsSection(
    val title: String,
    val options: List<SettingsOption>
)

/**
 * PLAYER SETTINGS - Netflix/Prime style right-side drawer, fully D-pad
 * navigable: UP/DOWN moves through options, CENTER selects, BACK or LEFT
 * closes the drawer and returns focus to the player controls.
 */
@Composable
fun PlayerSettingsPanel(
    sections: List<SettingsSection>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firstFocus = remember { FocusRequester() }

    // Retry until the first row is laid out and actually takes focus, so the
    // drawer reliably captures D-pad input instead of the player behind it.
    LaunchedEffect(Unit) {
        repeat(20) {
            try {
                firstFocus.requestFocus()
                return@LaunchedEffect
            } catch (_: Exception) {
                delay(50)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim - clicking/back closes
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onClose)
        )

        // Right drawer
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(380.dp)
                .focusGroup()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xF0101820), Color(0xFA0C121A))
                    )
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                var focusAssigned = false
                sections.forEachIndexed { sectionIndex, section ->
                    item(key = "header_$sectionIndex") {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                letterSpacing = 1.sp
                            ),
                            modifier = Modifier.padding(
                                top = if (sectionIndex == 0) 0.dp else 24.dp,
                                bottom = 10.dp
                            )
                        )
                    }
                    section.options.forEachIndexed { optIndex, option ->
                        val takeFocus = !focusAssigned
                        if (takeFocus) focusAssigned = true
                        item(key = "opt_${sectionIndex}_$optIndex") {
                            SettingsRow(
                                option = option,
                                onClose = onClose,
                                focusRequester = if (takeFocus) firstFocus else null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    option: SettingsOption,
    onClose: () -> Unit,
    focusRequester: FocusRequester?
) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (focused) PrimeBlue else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Enter, Key.DirectionCenter -> {
                            option.onSelect(); true
                        }
                        Key.Back, Key.Escape, Key.DirectionLeft -> {
                            onClose(); true
                        }
                        Key.DirectionRight -> true // stay in the drawer
                        else -> false
                    }
                } else false
            }
            .focusable()
            .clickable { option.onSelect() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = when {
                    option.selected && focused -> Color.White
                    option.selected -> PrimeBlue
                    else -> Color.Transparent
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (option.selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (focused) Color.White else if (option.selected) TextPrimary else PrimeTextDim
                )
            )
        }
    }
}
