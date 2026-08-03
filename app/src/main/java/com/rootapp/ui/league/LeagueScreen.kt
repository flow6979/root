package com.rootapp.ui.league

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootapp.data.Leaderboard
import com.rootapp.data.LeagueMember
import com.rootapp.data.LeaderStanding
import com.rootapp.data.SupabaseRepository
import com.rootapp.ui.common.GlassCard
import com.rootapp.ui.common.ScoreRing
import com.rootapp.ui.common.SectionLabel
import com.rootapp.ui.common.enterUp
import com.rootapp.ui.theme.LocalRootPalette
import kotlinx.coroutines.launch

/**
 * The weekly wellbeing league: your live percentile and the ranked board. Effort Points come from
 * healthy actions (check-ins, focus, reflections, heeded nudges, meals), so everyone can climb by
 * doing the things Root is here to help with. Needs a username the first time.
 */
@Composable
fun LeagueScreen(modifier: Modifier = Modifier) {
    val palette = LocalRootPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SupabaseRepository(context) }
    val week = remember { Leaderboard.weekStartString() }

    var loadingUser by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var standing by remember { mutableStateOf<LeaderStanding?>(null) }
    var division by remember { mutableStateOf<List<LeagueMember>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            refreshing = true
            Leaderboard.syncScore(context)
            standing = repo.myStanding(week)
            division = repo.myDivision(week)
            refreshing = false
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!repo.configured) { loadingUser = false; error = "Leaderboard is offline right now."; return@LaunchedEffect }
        username = repo.getUsername()
        loadingUser = false
        if (username != null) reload()
    }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        Text("Weekly league", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
        Text("Earn points by doing what's good for you. Everyone starts fresh each Monday.",
            fontSize = 12.sp, color = palette.dim)
        Spacer(Modifier.height(16.dp))

        when {
            loadingUser -> CircularProgressIndicator(color = palette.accent)
            error != null -> GlassCard(Modifier.enterUp(0)) {
                Text(error!!, fontSize = 14.sp, color = palette.onSurface)
            }
            username == null -> {
                // ---- first-time username setup ----
                GlassCard(Modifier.enterUp(0)) {
                    SectionLabel("Pick a name")
                    Spacer(Modifier.height(8.dp))
                    Text("This is how you'll show up on the board. Pick something fun, not your full name.",
                        fontSize = 12.sp, color = palette.dim)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(20); error = null },
                        singleLine = true,
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val name = draft.trim()
                            if (name.length < 3) { error = "At least 3 characters."; return@Button }
                            saving = true; error = null
                            scope.launch {
                                val ok = repo.setUsername(name)
                                saving = false
                                if (ok) { username = name; reload() } else error = "That name is taken or couldn't be saved."
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Join the league", fontWeight = FontWeight.SemiBold) }
                    error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = palette.accent, fontSize = 13.sp) }
                }
            }
            else -> {
                StandingHero(standing, refreshing)
                Spacer(Modifier.height(14.dp))
                DivisionCard(division, refreshing)
                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = { reload() }, enabled = !refreshing, modifier = Modifier.fillMaxWidth(),
                ) { Text(if (refreshing) "Refreshing..." else "Refresh", color = palette.accent) }
            }
        }
        // Season + cosmetic sky themes are fully local, so they show regardless of backend state.
        if (!loadingUser) {
            Spacer(Modifier.height(14.dp))
            SeasonCard(context)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StandingHero(standing: LeaderStanding?, refreshing: Boolean) {
    val palette = LocalRootPalette.current
    GlassCard(Modifier.enterUp(0)) {
        if (standing == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("This week")
                    Spacer(Modifier.height(6.dp))
                    Text(if (refreshing) "Loading your standing..." else "Do something kind for yourself to get on the board.",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                }
            }
            return@GlassCard
        }
        val topPct = (100 - standing.effortPercentile).coerceIn(0, 100)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(standing.effortPercentile, "pctile", size = 104.dp, stroke = 11.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                SectionLabel("This week")
                Spacer(Modifier.height(6.dp))
                Text("Top $topPct%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("#${standing.rank} of ${standing.players} - ${standing.points} points",
                    fontSize = 12.sp, color = palette.dim)
                if (standing.growthPercentile > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("Most improved: top ${(100 - standing.growthPercentile).coerceIn(0, 100)}%",
                        fontSize = 12.sp, color = palette.accent)
                }
            }
        }
    }
}

private val TIER_NAMES = listOf("Ember", "Dawn", "Sky", "Aurora", "Zenith")
private fun tierName(t: Int): String = TIER_NAMES.getOrElse(t) { "Ember" }

private val promoteGreen = androidx.compose.ui.graphics.Color(0xFF3E9C6B)
private val relegateRed = androidx.compose.ui.graphics.Color(0xFFC0563F)

@Composable
private fun DivisionCard(division: List<LeagueMember>, refreshing: Boolean) {
    val palette = LocalRootPalette.current
    val tier = division.firstOrNull()?.tier ?: 0
    val size = division.firstOrNull()?.leagueSize ?: division.size
    GlassCard(Modifier.enterUp(40)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(tierName(tier) + " league")
            Spacer(Modifier.weight(1f))
            if (tier < 4) Text("Top 5 promote", fontSize = 11.sp, color = promoteGreen, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        if (division.isEmpty()) {
            Text(if (refreshing) "Loading your league..." else "No one here yet. Earn a point to open your league.",
                fontSize = 13.sp, color = palette.dim)
        } else {
            division.forEachIndexed { i, row ->
                val promote = tier < 4 && row.rank <= 5
                val relegate = tier > 0 && size > 5 && row.rank > size - 5
                // Zone dividers, Duolingo-style.
                if (promote && (i == 0)) ZoneLabel("Promotion zone", promoteGreen)
                if (relegate && (i == 0 || !isRelegate(division[i - 1], tier, size))) {
                    Spacer(Modifier.height(6.dp)); ZoneLabel("Relegation zone", relegateRed)
                }
                if (i > 0) Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (row.isMe) palette.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val badge = when {
                        promote -> promoteGreen
                        relegate -> relegateRed
                        else -> palette.surface
                    }
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(badge),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${row.rank}", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (promote || relegate) androidx.compose.ui.graphics.Color.White else palette.onSurface)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        row.username, fontSize = 14.sp,
                        fontWeight = if (row.isMe) FontWeight.Bold else FontWeight.Medium,
                        color = if (row.isMe) palette.accent else palette.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${row.points}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
                    Text(" pts", fontSize = 11.sp, color = palette.dim)
                }
            }
        }
    }
}

private fun isRelegate(row: LeagueMember, tier: Int, size: Int): Boolean =
    tier > 0 && size > 5 && row.rank > size - 5

/** Season progress + the cosmetic sky-theme picker (unlock by earning points across the season). */
@Composable
private fun SeasonCard(context: android.content.Context) {
    val palette = LocalRootPalette.current
    val season = remember { com.rootapp.data.SeasonStore(context) }
    // Re-read on each selection so the ticks/highlight update.
    var selected by remember { mutableStateOf(season.selectedTheme()) }
    val points = remember { season.points() }
    val daysLeft = remember { season.daysLeft() }

    GlassCard(Modifier.enterUp(60)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Season ${season.seasonNumber()}")
            Spacer(Modifier.weight(1f))
            Text("$daysLeft days left", fontSize = 11.sp, color = palette.dim)
        }
        Spacer(Modifier.height(8.dp))
        Text("$points season points", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
        Text("Earn points to unlock skins for your sky. They're yours to keep.", fontSize = 12.sp, color = palette.dim)
        Spacer(Modifier.height(14.dp))
        com.rootapp.data.SkyTheme.all().forEach { theme ->
            val unlocked = season.isUnlocked(theme)
            val isSel = selected.key == theme.key
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) palette.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                    .let { m -> if (unlocked) m.clickable { selected = theme; com.rootapp.data.SkyThemeState.set(context, theme) } else m }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        theme.label, fontSize = 14.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (unlocked) palette.onSurface else palette.dim,
                    )
                    Text(
                        if (unlocked) theme.blurb else "${theme.blurb} - unlock at ${theme.unlockPoints} pts",
                        fontSize = 12.sp, color = palette.dim,
                    )
                }
                when {
                    isSel -> Text("Active", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.accent)
                    unlocked -> Text("Use", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.accent)
                    else -> Text("Locked", fontSize = 12.sp, color = palette.dim)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ZoneLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier.padding(vertical = 4.dp))
}
