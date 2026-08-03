package com.rootapp.ui.weekly

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootapp.data.WeeklyReview
import com.rootapp.di.AppModule
import com.rootapp.shield.UsageStatsReader
import com.rootapp.ui.common.GlassCard
import com.rootapp.ui.common.IconTile
import com.rootapp.ui.common.SectionLabel
import com.rootapp.ui.common.enterUp
import com.rootapp.ui.theme.LocalRootPalette

@Composable
fun WeeklyReviewScreen(modifier: Modifier = Modifier) {
    val palette = LocalRootPalette.current
    val context = LocalContext.current
    val data = remember { WeeklyReview.build(context) }
    var focus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { focus = WeeklyReview.focusLine(AppModule.llmClient, data) }

    val t = WeeklyReview.trendPct(data)
    val screenSub = when {
        t == null -> "first week of data"
        t < 0 -> "down ${-t}% vs last week"
        t > 0 -> "up $t% vs last week"
        else -> "same as last week"
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Text("Your week", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
        Text(WeeklyReview.headline(data), fontSize = 13.sp, color = palette.dim)
        Spacer(Modifier.height(18.dp))

        StatCard(Icons.Rounded.PhoneIphone, "Screen time", UsageStatsReader.fmt(data.screenThisWeekMin), screenSub, 0)
        Spacer(Modifier.height(12.dp))
        StatCard(
            Icons.Rounded.Bedtime, "Sleep",
            data.avgSleepMin?.let { UsageStatsReader.fmt(it) + " avg" } ?: "not enough data",
            if (data.sleepNights > 0) "consistency ${data.bedtimeConsistency ?: "-"} - ${data.sleepNights} nights tracked" else null, 60,
        )
        Spacer(Modifier.height(12.dp))
        StatCard(
            Icons.Rounded.Restaurant, "Eating",
            data.eatingScore?.let { "$it/100" } ?: "no meals logged",
            "${data.healthyMeals} healthy, ${data.junkMeals} junk this week", 120,
        )
        Spacer(Modifier.height(12.dp))
        StatCard(
            Icons.Rounded.WbSunny, "Mood",
            data.moodScore?.let { "$it/100" } ?: "no check-ins",
            "from your check-ins", 180,
        )
        Spacer(Modifier.height(12.dp))
        StatCard(Icons.Rounded.SelfImprovement, "Pauses you took", "${data.pausesTaken}", "times you stepped away", 240)
        Spacer(Modifier.height(18.dp))

        SectionLabel("Focus for next week")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.enterUp(300)) {
            Text(focus ?: "Thinking it through...", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.accent)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, sub: String?, delay: Int) {
    val palette = LocalRootPalette.current
    GlassCard(Modifier.enterUp(delay)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon)
            Spacer(Modifier.width(14.dp))
            Column {
                SectionLabel(label)
                Spacer(Modifier.height(6.dp))
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
                if (sub != null) Text(sub, fontSize = 12.sp, color = palette.dim)
            }
        }
    }
}
