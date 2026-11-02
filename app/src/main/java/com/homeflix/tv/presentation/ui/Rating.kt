package com.homeflix.tv.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.homeflix.tv.data.repository.Thumbs

/**
 * "Rate" on a title page, Netflix-style: shows your current rating; OK opens
 * Not for me / I like this / Love this!. Ratings personalise your rows.
 * [rating] null = still loading (button hidden).
 */
@Composable
fun RateButton(rating: Int?, onRate: (Int) -> Unit) {
    if (rating == null) return
    var open by remember { mutableStateOf(false) }
    val label = when (rating) {
        Thumbs.LOVE -> "Loved"
        Thumbs.LIKE -> "Liked"
        Thumbs.DISLIKE -> "Not for me"
        else -> "Rate"
    }
    val icon = when (rating) {
        Thumbs.DISLIKE -> Icons.Filled.ThumbDown
        Thumbs.LIKE, Thumbs.LOVE -> Icons.Filled.ThumbUp
        else -> Icons.Outlined.ThumbUp
    }
    PillButton(label, { open = true }, icon = icon)
    if (open) {
        RateDialog(current = rating, onPick = { v ->
            open = false
            // Picking your current rating again clears it (as on Netflix).
            onRate(if (v == rating) Thumbs.NONE else v)
        }, onDismiss = { open = false })
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun RateDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
        withFrameNanos { }
        runCatching { first.requestFocus() } // the dialog window may only take focus a frame later
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).testTag("rate_dialog"), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(560.dp).clip(RoundedCornerShape(12.dp)).background(Tv.Surface).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Rate this title", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Your ratings shape what HomeFlix recommends", color = Tv.TextSecondary, fontSize = 13.sp)
                // However focus first enters the dialog, it lands on the current
                // rating (or "I like this"), never just on the first button.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                    modifier = Modifier.padding(top = 18.dp)
                        .focusProperties { enter = { first } }
                        .focusGroup()
                ) {
                    val initial = when (current) { Thumbs.DISLIKE -> 0; Thumbs.LOVE -> 2; else -> 1 }
                    ThumbOption("Not for me", Thumbs.DISLIKE, current, onPick, if (initial == 0) first else null) { tint, size ->
                        Icon(if (current == Thumbs.DISLIKE) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, null, tint = tint, modifier = Modifier.size(size))
                    }
                    ThumbOption("I like this", Thumbs.LIKE, current, onPick, if (initial == 1) first else null) { tint, size ->
                        Icon(if (current == Thumbs.LIKE) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, null, tint = tint, modifier = Modifier.size(size))
                    }
                    ThumbOption("Love this!", Thumbs.LOVE, current, onPick, if (initial == 2) first else null) { tint, size ->
                        // Two thumbs, like Netflix's "Love this!".
                        Box(Modifier.size(size + 12.dp)) {
                            val i = if (current == Thumbs.LOVE) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp
                            Icon(i, null, tint = tint.copy(alpha = 0.55f), modifier = Modifier.size(size).align(Alignment.TopStart))
                            Icon(i, null, tint = tint, modifier = Modifier.size(size).align(Alignment.BottomEnd).offset(x = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbOption(
    label: String,
    value: Int,
    current: Int,
    onPick: (Int) -> Unit,
    focusRequester: FocusRequester?,
    icon: @Composable (Color, Dp) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val selected = current == value
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label }
                .size(84.dp)
                .clip(CircleShape)
                .background(if (focused) Color.White else Color(0x26FFFFFF))
                .border(2.dp, if (selected && !focused) Color.White else Color.Transparent, CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(value) }
        ) {
            icon(if (focused) Color.Black else Color.White, 34.dp)
        }
        Text(label, color = if (focused) Color.White else Tv.TextSecondary, fontSize = 14.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
