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
        
        // NETFLIX-LEVEL LazyRow with professional focus management
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (focusRequester != null) {
                        Modifier
                            .focusRequester(focusRequester)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            if (currentFocusedIndex > 0) {
                                                currentFocusedIndex--
                                                val requesterIndex = minOf(currentFocusedIndex, itemFocusRequesters.size - 1)
                                                try {
                                                    itemFocusRequesters[requesterIndex].requestFocus()
                                                    coroutineScope.launch {
                                                        listState.animateScrollToItem(
                                                            maxOf(0, currentFocusedIndex - 2)
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    // Ignore scroll errors
                                                }
                                                true
                                            } else false
                                        }
                                        Key.DirectionRight -> {
                                            if (currentFocusedIndex < mediaList.size - 1) {
                                                currentFocusedIndex++
                                                val requesterIndex = minOf(currentFocusedIndex, itemFocusRequesters.size - 1)
                                                try {
                                                    itemFocusRequesters[requesterIndex].requestFocus()
                                                    coroutineScope.launch {
                                                        listState.animateScrollToItem(
                                                            maxOf(0, currentFocusedIndex - 2)
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    // Ignore scroll errors
                                                }
                                                true
                                            } else false
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
                    } else {
                        Modifier
                    }
                )
        ) {
            itemsIndexed(mediaList) { index, media ->
                val requesterIndex = minOf(index, itemFocusRequesters.size - 1)
                MediaCard(
                    media = media,
                    onClick = { onMediaClick(media) },
                    modifier = Modifier
                        .width(160.dp)
                        .then(
                            if (index < itemFocusRequesters.size) {
                                Modifier
                                    .focusRequester(itemFocusRequesters[requesterIndex])
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            currentFocusedIndex = index
                                        }
                                    }
                            } else {
                                Modifier
                            }
                        )
                )
            }
        }
    }
}