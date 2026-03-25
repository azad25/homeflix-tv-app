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
import androidx.compose.ui.focus.onFocusChanged
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
    // Professional focus state management
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var currentFocusedIndex by remember { mutableStateOf(0) }
    val itemFocusRequesters = remember(mediaList.size) { 
        List(minOf(mediaList.size, 20)) { FocusRequester() } // Limit to 20 for performance
    }
    
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
        
        // NETFLIX PRINCIPLE: Let individual cards handle focus, LazyRow handles scrolling
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = true,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(mediaList) { index, media ->
                val itemFocusRequester = if (index < itemFocusRequesters.size) itemFocusRequesters[index] else null
                
                NetflixMediaCard(
                    media = media,
                    onClick = { onMediaClick(media) },
                    modifier = Modifier
                        .width(130.dp)
                        .then(
                            if (itemFocusRequester != null && index == 0 && focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else if (itemFocusRequester != null) {
                                Modifier.focusRequester(itemFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                currentFocusedIndex = index
                                // Auto-scroll to focused item with padding - Netflix behavior
                                coroutineScope.launch {
                                    // Scroll with some padding to show neighboring items
                                    val targetIndex = when {
                                        index == 0 -> 0
                                        index >= mediaList.size - 2 -> maxOf(0, mediaList.size - 3)
                                        else -> maxOf(0, index - 1)
                                    }
                                    listState.animateScrollToItem(targetIndex)
                                }
                            }
                        }
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> {
                                        if (onNavigateUp != null) {
                                            onNavigateUp.invoke()
                                            true
                                        } else {
                                            false // Let Compose focus system handle navigation
                                        }
                                    }
                                    Key.DirectionDown -> {
                                        if (onNavigateDown != null) {
                                            onNavigateDown.invoke()
                                            true
                                        } else {
                                            false // Let Compose focus system handle navigation
                                        }
                                    }
                                    Key.DirectionLeft -> {
                                        // Navigate to previous item in row - MUST consume to prevent parent scroll
                                        if (index > 0) {
                                            val prevIndex = index - 1
                                            if (prevIndex < itemFocusRequesters.size) {
                                                itemFocusRequesters[prevIndex].requestFocus()
                                            }
                                            true
                                        } else {
                                            // At first item, let system handle (moves to sidebar)
                                            false
                                        }
                                    }
                                    Key.DirectionRight -> {
                                        // Navigate to next item in row - MUST consume to prevent parent scroll
                                        if (index < mediaList.size - 1) {
                                            val nextIndex = index + 1
                                            if (nextIndex < itemFocusRequesters.size) {
                                                itemFocusRequesters[nextIndex].requestFocus()
                                            }
                                            true
                                        } else {
                                            // At last item, try to navigate down
                                            onNavigateDown?.invoke()
                                            true
                                        }
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