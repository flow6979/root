package com.rootapp.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootapp.ui.theme.LocalRootPalette

/** Stand-in for tabs not yet built in this vertical slice (Shield / Moments / Stories / You). */
@Composable
fun PlaceholderScreen(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val palette = LocalRootPalette.current
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Orb(size = 72.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, fontSize = 13.sp, color = palette.dim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Coming in a later build.", fontSize = 12.sp, color = palette.dim)
    }
}
