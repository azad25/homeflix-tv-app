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
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            ),
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
        )
        
        // NETFLIX PRINCIPLE: Let individual cards handle focus, LazyRow handles scrolling
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp),
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
                                // Only scroll LazyRow if item is outside visible range
                                coroutineScope.launch {
                                    val layoutInfo = listState.layoutInfo
                                    val visibleItems = layoutInfo.visibleItemsInfo
                                    if (visibleItems.isNotEmpty()) {
                                        val firstVisible = visibleItems.first().index
                                        val lastVisible = visibleItems.last().index
                                        // Only scroll if item is outside visible range
                                        if (index < firstVisible || index > lastVisible) {
                                            listState.scrollToItem(maxOf(0, index - 1))
                                        }
                                    }
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
                                            false // Let focus system handle
                                        }
                                    }
                                    Key.DirectionDown -> {
                                        if (onNavigateDown != null) {
                                            onNavigateDown.invoke()
                                            true
                                        } else {
                                            false // Let focus system handle
                                        }
                                    }
                                    Key.DirectionLeft -> {
                                        // Navigate to previous item in row, or let focus escape to sidebar if at first item
                                        if (index > 0) {
                                            val prevIndex = index - 1
                                            if (prevIndex < itemFocusRequesters.size) {
                                                itemFocusRequesters[prevIndex].requestFocus()
                                            }
                                            true // Consume event
                                        } else {
                                            // At first item - let focus system handle (allows navigation to sidebar)
                                            false
                                        }
                                    }
                                    Key.DirectionRight -> {
                                        // Navigate to next item in row
                                        if (index < mediaList.size - 1) {
                                            val nextIndex = index + 1
                                            if (nextIndex < itemFocusRequesters.size) {
                                                itemFocusRequesters[nextIndex].requestFocus()
                                            }
                                        }
                                        true // Consume to prevent parent scroll
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