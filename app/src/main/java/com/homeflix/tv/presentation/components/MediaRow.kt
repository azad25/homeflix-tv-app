package com.homeflix.tv.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.presentation.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun MediaRow(
    title: String,
    mediaList: List<Media>,
    onMediaClick: (Media) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequesters = remember { List(mediaList.size) { FocusRequester() } }
    
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Section Title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            ),
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
        )
        
        // RESTORED D-PAD NAVIGATION with crash protection
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(mediaList) { index, media ->
                MediaCard(
                    media = media,
                    onClick = { onMediaClick(media) },
                    modifier = Modifier
                        .width(160.dp)
                        .then(
                            if (index == 0 && focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier.focusRequester(focusRequesters[index])
                            }
                        )
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionLeft -> {
                                        if (index > 0) {
                                            try {
                                                focusRequesters[index - 1].requestFocus()
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(maxOf(0, index - 2))
                                                }
                                            } catch (e: Exception) {
                                                // Ignore focus/scroll errors
                                            }
                                        }
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        if (index < mediaList.size - 1) {
                                            try {
                                                focusRequesters[index + 1].requestFocus()
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(minOf(mediaList.size - 1, index - 1))
                                                }
                                            } catch (e: Exception) {
                                                // Ignore focus/scroll errors
                                            }
                                        }
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        onNavigateUp?.invoke()
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        onNavigateDown?.invoke()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                )
            }
        }
    }
}