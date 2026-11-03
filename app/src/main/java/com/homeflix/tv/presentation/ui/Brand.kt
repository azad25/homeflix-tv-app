package com.homeflix.tv.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The HomeFlix mark: a rounded play triangle (same path as the web app's icon.svg). */
val HomeFlixMark: ImageVector by lazy {
    ImageVector.Builder("HomeFlixMark", 64.dp, 64.dp, viewportWidth = 32f, viewportHeight = 36f).apply {
        // The 64-unit path, cropped to the triangle (x 23..55, y 14..50).
        addPath(
            pathData = PathParser().parsePathString(
                "M1,3.6v28.8c0,2.4 2.6,3.9 4.7,2.7l24,-14.4c2,-1.2 2,-4.1 0,-5.3l-24,-14.4c-2.1,-1.3 -4.7,0.2 -4.7,2.6Z"
            ).toNodes(),
            fill = SolidColor(Color.White)
        )
    }.build()
}

/** "▶ HomeFlix": mark + wordmark, white ("Home" heavy, "Flix" light), as on the web app. */
@Composable
fun BrandWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 19.sp, markSize: Dp = 15.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(markSize * 0.45f)) {
        Icon(HomeFlixMark, contentDescription = null, tint = Color.White, modifier = Modifier.size(markSize * 0.89f, markSize))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color.White)) { append("Home") }
                withStyle(SpanStyle(fontWeight = FontWeight.Light, color = Color.White.copy(alpha = 0.8f))) { append("Flix") }
            },
            fontSize = fontSize,
            letterSpacing = (-0.03).em
        )
    }
}
