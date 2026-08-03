package com.rootapp.ui.league

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootapp.data.Badges
import com.rootapp.data.BadgeStore
import com.rootapp.data.Challenges
import com.rootapp.data.Leaderboard
import com.rootapp.data.LeaderStanding
import com.rootapp.data.LeagueMember
import com.rootapp.data.SeasonStore
import com.rootapp.data.SkyTheme
import com.rootapp.data.SkyThemeState
import com.rootapp.data.SupabaseRepository
import com.rootapp.ui.common.GlassCard
import com.rootapp.ui.common.SectionLabel
import com.rootapp.ui.common.enterUp
import com.rootapp.ui.theme.LocalRootPalette
import kotlinx.coroutines.launch

// ---- tier + accent palette (gives climbing a visible identity) ----
private data class Tier(val name: String, val color: Color)

private val TIERS = listOf(
    Tier("Ember", Color(0xFFE0703A)),
    Tier("Dawn", Color(0xFFE86A8E)),
    Tier("Sky", Color(0xFF4E86E0)),
    Tier("Aurora", Color(0xFF37B59E)),
    Tier("Zenith", Color(0xFF8A6BE0)),
)

private fun tier(t: Int): Tier = TIERS.getOrElse(t) { TIERS.first() }

private val promoteGreen = Color(0xFF3E9C6B)
private val relegateRed = Color(0xFFC0563F)
private val gold = Color(0xFFD9A02B)
private val silver = Color(0xFF9AA3AD)
private val bronze = Color(0xFFB5763E)
private val THEME_SWATCH = mapOf(
    "default" to Color(0xFF6FA8DC),
    "golden" to Color(0xFFF3B44C),
    "starfield" to Color(0xFF9BB4FF),
    "meteor" to Color(0xFFB7C6E6),
    "aurora" to Color(0xFF5CE1B6),
)

/**
 * The weekly wellbeing league, redesigned as a game surface: a bold tier hero, a ranked board with
 * medals and promotion/relegation zones, this week's challenge, the season + cosmetic sky picker,
 * and a badge shelf. Effort Points come from healthy actions, so everyone can climb.
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

    LaunchedEffect(Unit) {
        if (!repo.configured) { loadingUser = false; error = "Leaderboard is offline right now."; return@LaunchedEffect }
        username = repo.getUsername()
        loadingUser = false
        if (username != null) reload()
    }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(6.dp))
        Text("Weekly league", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
        Text("Do what's good for you. Everyone starts fresh each Monday.", fontSize = 13.sp, color = palette.dim)
        Spacer(Modifier.height(16.dp))

        when {
            loadingUser -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            error != null -> GlassCard(Modifier.enterUp(0)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = palette.dim, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(error!!, fontSize = 14.sp, color = palette.onSurface)
                }
            }
            username == null -> UsernameSetup(draft, saving, error, palette,
                onDraft = { draft = it.take(20); error = null },
                onJoin = {
                    val name = draft.trim()
                    if (name.length < 3) {
                        error = "At least 3 characters."
                    } else {
                        saving = true; error = null
                        scope.launch {
                            val err = repo.setUsername(name)
                            saving = false
                            if (err == null) { username = name; reload() } else error = err
                        }
                    }
                })
            else -> {
                val tierIdx = division.firstOrNull()?.tier ?: 0
                HeroCard(standing, tierIdx, refreshing)
                Spacer(Modifier.height(14.dp))
                DivisionCard(division, refreshing)
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = { reload() }, enabled = !refreshing, modifier = Modifier.fillMaxWidth(),
                ) { Text(if (refreshing) "Refreshing..." else "Refresh", color = palette.accent) }
            }
        }

        if (!loadingUser) {
            Spacer(Modifier.height(14.dp))
            ChallengeCard(context)
            Spacer(Modifier.height(14.dp))
            SeasonCard(context)
            Spacer(Modifier.height(14.dp))
            BadgesCard(context)
        }
        Spacer(Modifier.height(28.dp))
    }
}

/** A glossy circular tier crest with a trophy, in the tier's colour. */
@Composable
private fun TierCrest(tierIdx: Int, size: androidx.compose.ui.unit.Dp) {
    val t = tier(tierIdx)
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(t.color, t.color.copy(alpha = 0.72f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

@Composable
private fun HeroCard(standing: LeaderStanding?, tierIdx: Int, refreshing: Boolean) {
    val palette = LocalRootPalette.current
    val t = tier(tierIdx)
    GlassCard(Modifier.enterUp(0)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TierCrest(tierIdx, 72.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name.uppercase() + " LEAGUE", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp, color = t.color)
                Spacer(Modifier.height(4.dp))
                if (standing == null) {
                    Text(if (refreshing) "Loading..." else "Get on the board", fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, color = palette.onSurface)
                    Text("Do something kind for yourself.", fontSize = 12.sp, color = palette.dim)
                } else {
                    Text("Top ${(100 - standing.effortPercentile).coerceIn(0, 100)}%", fontSize = 26.sp,
                        fontWeight = FontWeight.Bold, color = palette.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text("Rank #${standing.rank} of ${standing.players}", fontSize = 12.sp, color = palette.dim)
                }
            }
        }
        if (standing != null) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("${standing.points} points", t.color)
                if (standing.growthPercentile > 0) {
                    Pill("Most improved: top ${(100 - standing.growthPercentile).coerceIn(0, 100)}%", promoteGreen)
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) { Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color) }
}

@Composable
private fun DivisionCard(division: List<LeagueMember>, refreshing: Boolean) {
    val palette = LocalRootPalette.current
    val tierIdx = division.firstOrNull()?.tier ?: 0
    val size = division.firstOrNull()?.leagueSize ?: division.size
    GlassCard(Modifier.enterUp(40)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Standings")
            Spacer(Modifier.weight(1f))
            if (tierIdx < 4) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(promoteGreen))
                Spacer(Modifier.width(6.dp))
                Text("Top 5 move up", fontSize = 11.sp, color = promoteGreen, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (division.isEmpty()) {
            Text(if (refreshing) "Loading your league..." else "No one here yet. Earn a point to open your league.",
                fontSize = 13.sp, color = palette.dim)
        } else {
            division.forEachIndexed { i, row ->
                val promote = tierIdx < 4 && row.rank <= 5
                val relegate = tierIdx > 0 && size > 5 && row.rank > size - 5
                if (promote && i == 4) { Spacer(Modifier.height(8.dp)); ZoneDivider("Promotion line", promoteGreen) }
                if (relegate && !isRelegate(division.getOrNull(i - 1), tierIdx, size)) {
                    Spacer(Modifier.height(8.dp)); ZoneDivider("Relegation line", relegateRed)
                }
                MemberRow(row, promote, relegate)
            }
        }
    }
}

@Composable
private fun MemberRow(row: LeagueMember, promote: Boolean, relegate: Boolean) {
    val palette = LocalRootPalette.current
    val accentBar = when { promote -> promoteGreen; relegate -> relegateRed; else -> Color.Transparent }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (row.isMe) palette.accentSoft else Color.Transparent)
            .then(if (row.isMe) Modifier.border(1.dp, palette.accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(accentBar))
        Spacer(Modifier.width(8.dp))
        RankMedal(row.rank)
        Spacer(Modifier.width(12.dp))
        Text(
            row.username, fontSize = 15.sp,
            fontWeight = if (row.isMe) FontWeight.Bold else FontWeight.Medium,
            color = if (row.isMe) palette.accent else palette.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (row.isMe) { Pill("YOU", palette.accent); Spacer(Modifier.width(8.dp)) }
        Box(
            Modifier.clip(RoundedCornerShape(9.dp)).background(palette.surface).padding(horizontal = 9.dp, vertical = 5.dp),
        ) {
            Text("${row.points}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
        }
    }
}

/** Gold / silver / bronze medal for the top three, a plain numbered chip otherwise. */
@Composable
private fun RankMedal(rank: Int) {
    val palette = LocalRootPalette.current
    val medal = when (rank) { 1 -> gold; 2 -> silver; 3 -> bronze; else -> null }
    if (medal != null) {
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(medal, medal.copy(alpha = 0.7f)))),
            contentAlignment = Alignment.Center,
        ) { Text("$rank", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
    } else {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(palette.surface),
            contentAlignment = Alignment.Center,
        ) { Text("$rank", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.dim) }
    }
}

@Composable
private fun ZoneDivider(text: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(color.copy(alpha = 0.4f)))
        Text("  ${text.uppercase()}  ", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
        Box(Modifier.weight(1f).height(1.dp).background(color.copy(alpha = 0.4f)))
    }
}

private fun isRelegate(row: LeagueMember?, tier: Int, size: Int): Boolean =
    row != null && tier > 0 && size > 5 && row.rank > size - 5

@Composable
private fun UsernameSetup(
    draft: String,
    saving: Boolean,
    error: String?,
    palette: com.rootapp.ui.theme.RootPalette,
    onDraft: (String) -> Unit,
    onJoin: () -> Unit,
) {
    GlassCard(Modifier.enterUp(0)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TierCrest(0, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Join the league", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
                Text("Pick a name for the board.", fontSize = 12.sp, color = palette.dim)
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = draft, onValueChange = onDraft, singleLine = true,
            label = { Text("Username") }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onJoin, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (saving) "Joining..." else "Join the league", fontWeight = FontWeight.SemiBold)
        }
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = palette.accent, fontSize = 13.sp) }
    }
}

/** This week's challenge with an icon, thick progress bar, and reward chip. */
@Composable
private fun ChallengeCard(context: android.content.Context) {
    val palette = LocalRootPalette.current
    val ch = remember { Challenges.current() }
    val progress = remember { Challenges.progress(context) }
    val done = progress >= ch.target
    val frac = (progress.toFloat() / ch.target).coerceIn(0f, 1f)
    GlassCard(Modifier.enterUp(50)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (done) promoteGreen.copy(alpha = 0.18f) else palette.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (done) Icons.Rounded.CheckCircle else Icons.Rounded.Bolt, null,
                    tint = if (done) promoteGreen else palette.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                SectionLabel("This week's challenge")
                Spacer(Modifier.height(3.dp))
                Text(ch.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
            }
            Pill(if (done) "Done" else "+${ch.bonus}", if (done) promoteGreen else palette.accent)
        }
        Spacer(Modifier.height(10.dp))
        Text(ch.desc, fontSize = 12.sp, color = palette.dim)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)),
            color = if (done) promoteGreen else palette.accent,
            trackColor = palette.surface,
        )
        Spacer(Modifier.height(6.dp))
        Text("$progress / ${ch.target}", fontSize = 12.sp, color = palette.dim)
    }
}

/** Season progress + a "next unlock" bar + the cosmetic sky picker with colour swatches. */
@Composable
private fun SeasonCard(context: android.content.Context) {
    val palette = LocalRootPalette.current
    val season = remember { SeasonStore(context) }
    var selected by remember { mutableStateOf(season.selectedTheme()) }
    val points = remember { season.points() }
    val daysLeft = remember { season.daysLeft() }
    val next = remember { SkyTheme.all().firstOrNull { !season.isUnlocked(it) } }

    GlassCard(Modifier.enterUp(60)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Season ${season.seasonNumber()}")
            Spacer(Modifier.weight(1f))
            Text("$daysLeft days left", fontSize = 11.sp, color = palette.dim)
        }
        Spacer(Modifier.height(8.dp))
        Text("$points season points", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.onSurface)
        if (next != null) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (points.toFloat() / next.unlockPoints).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(5.dp)),
                color = THEME_SWATCH[next.key] ?: palette.accent, trackColor = palette.surface,
            )
            Spacer(Modifier.height(5.dp))
            Text("${(next.unlockPoints - points).coerceAtLeast(0)} pts to ${next.label}", fontSize = 12.sp, color = palette.dim)
        } else {
            Text("Every sky unlocked. Nice.", fontSize = 12.sp, color = palette.dim)
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("Your sky")
        Spacer(Modifier.height(8.dp))
        SkyTheme.all().forEach { theme ->
            val unlocked = season.isUnlocked(theme)
            val isSel = selected.key == theme.key
            val swatch = THEME_SWATCH[theme.key] ?: palette.accent
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSel) palette.accentSoft else Color.Transparent)
                    .let { m -> if (unlocked) m.clickable { selected = theme; SkyThemeState.set(context, theme) } else m }
                    .padding(horizontal = 10.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(swatch, swatch.copy(alpha = 0.6f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!unlocked) Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(theme.label, fontSize = 14.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (unlocked) palette.onSurface else palette.dim)
                    Text(if (unlocked) theme.blurb else "Unlock at ${theme.unlockPoints} pts",
                        fontSize = 12.sp, color = palette.dim)
                }
                when {
                    isSel -> Pill("Active", palette.accent)
                    unlocked -> Text("Use", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.accent)
                    else -> Icon(Icons.Rounded.Lock, null, tint = palette.dim, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** A horizontally-scrolling shelf of badge medals. */
@Composable
private fun BadgesCard(context: android.content.Context) {
    val palette = LocalRootPalette.current
    val store = remember { BadgeStore(context) }
    GlassCard(Modifier.enterUp(70)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Badges")
            Spacer(Modifier.weight(1f))
            Text("${store.earnedCount()} / ${Badges.CATALOG.size}", fontSize = 11.sp, color = palette.dim)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Badges.CATALOG.forEach { badge ->
                val earned = store.isEarned(badge.key)
                Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(
                            if (earned) Brush.verticalGradient(listOf(gold, gold.copy(alpha = 0.7f)))
                            else Brush.verticalGradient(listOf(palette.surface, palette.surface)),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (earned) Icons.Rounded.MilitaryTech else Icons.Rounded.Lock,
                            null, tint = if (earned) Color.White else palette.dim, modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(badge.title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        color = if (earned) palette.onSurface else palette.dim,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}
