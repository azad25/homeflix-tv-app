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
    focusRequester: FocusRequester? = null
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequesters = remember { List(mediaList.size) { FocusRequester() } }
    var focusedIndex by remember { mutableStateOf(0) }
    
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Section Title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            ),
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
        )
        
        // Media Cards Row
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(mediaList) { index, media ->
                MediaCard(
                    media = media,
                    onClick = { onMediaClick(media) },
                    modifier = Modifier
                        .width(220.dp) // Netflix TV card size
                        .then(
                            if (index == 0 && focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier.focusRequester(focusRequesters[index])
                            }
                        )
                        .onKeyEvent { keyEvent ->
                            when {
                                keyEvent.key == Key.DirectionLeft && keyEvent.type == KeyEventType.KeyDown -> {
                                    if (index > 0) {
                                        focusedIndex = index - 1
                                        focusRequesters[index - 1].requestFocus()
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(maxOf(0, index - 2))
                                        }
                                    }
                                    true
                                }
                                keyEvent.key == Key.DirectionRight && keyEvent.type == KeyEventType.KeyDown -> {
                                    if (index < mediaList.size - 1) {
                                        focusedIndex = index + 1
                                        focusRequesters[index + 1].requestFocus()
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(minOf(mediaList.size - 1, index - 1))
                                        }
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                )
            }
        }
    }
}