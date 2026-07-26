package com.rootapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootapp.ui.common.Orb
import com.rootapp.ui.theme.LocalRootPalette

@Composable
fun HomeScreen(
    userName: String,
    onStartReflection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalRootPalette.current
    val greetingName = userName.ifBlank { "there" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Hey, $greetingName",
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.onSurface,
        )
        Text(
            text = "Good to see you. You're 4 days in.",
            fontSize = 13.sp,
            color = palette.dim,
        )
        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp, horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Orb(size = 72.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "\"You slept a little better last night. Proud of you. Want to talk for a bit?\"",
                    fontSize = 14.sp,
                    color = palette.onSurface,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("How are you feeling right now?", fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    listOf("😔", "😐", "🙂", "😌", "⚡️")
                        .forEach { Text(it, fontSize = 26.sp) }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onStartReflection,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start a reflection session", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "5 min · just talk, I'll listen",
            fontSize = 12.sp,
            color = palette.dim,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
