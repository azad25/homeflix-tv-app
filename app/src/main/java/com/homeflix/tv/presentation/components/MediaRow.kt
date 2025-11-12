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
        
        // FIXED: Enable proper scrolling and focus management
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = true, // ENABLE scrolling
            modifier = Modifier
                .fillMaxWidth()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                // Handle left navigation within row
                                if (currentFocusedIndex > 0) {
                                    currentFocusedIndex--
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(currentFocusedIndex)
                                    }
                                    if (currentFocusedIndex < itemFocusRequesters.size) {
                                        try {
                                            itemFocusRequesters[currentFocusedIndex].requestFocus()
                                        } catch (e: Exception) {
                                            // Ignore focus errors
                                        }
                                    }
                                    true
                                } else false
                            }
                            Key.DirectionRight -> {
                                // Handle right navigation within row
                                if (currentFocusedIndex < mediaList.size - 1) {
                                    currentFocusedIndex++
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(currentFocusedIndex)
                                    }
                                    if (currentFocusedIndex < itemFocusRequesters.size) {
                                        try {
                                            itemFocusRequesters[currentFocusedIndex].requestFocus()
                                        } catch (e: Exception) {
                                            // Ignore focus errors
                                        }
                                    }
                                    true
                                } else false
                            }
                            Key.DirectionUp -> {
                                // Navigate to previous row
                                onNavigateUp?.invoke()
                                true
                            }
                            Key.DirectionDown -> {
                                // Navigate to next row
                                onNavigateDown?.invoke()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            itemsIndexed(mediaList) { index, media ->
                MediaCard(
                    media = media,
                    onClick = { 
                        currentFocusedIndex = index
                        onMediaClick(media) 
                    },
                    modifier = Modifier
                        .width(160.dp)
                        .then(
                            if (index == 0 && focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else if (index < itemFocusRequesters.size) {
                                Modifier.focusRequester(itemFocusRequesters[index])
                            } else {
                                Modifier
                            }
                        )
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                currentFocusedIndex = index
                                // Auto-scroll to focused item
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            }
                        }
                )
            }
        }
    }
}