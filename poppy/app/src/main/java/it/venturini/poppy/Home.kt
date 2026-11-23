package it.venturini.poppy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SheetReq(val kind: String, val entry: Entry?)

@Composable
fun sideLabel(s: String?): String = when (s) {
    "sx" -> stringResource(R.string.side_left)
    "dx" -> stringResource(R.string.side_right)
    else -> stringResource(R.string.side_both)
}

@Composable
fun agoText(ms: Long?): String =
    if (ms == null) stringResource(R.string.dash)
    else if (ms < 60000) stringResource(R.string.now_label)
    else stringResource(R.string.ago, hDur(ms))

@Composable
fun HomeScreen(vm: AppVM, baby: Baby, d: Derived, snack: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(nowMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = nowMs()
        }
    }
    var sheet by remember { mutableStateOf<SheetReq?>(null) }

    val entries = vm.entries
    val oz = vm.oz

    val timer = baby.timer
    val running = timer?.start != null
    val nextSide = if (vm.fixedSide == "auto") d.nextSide else vm.fixedSide

    val sod = startOfDay(now)
    val todayFeeds = d.items.count { it.t >= sod && (it.kind == "seno" || it.kind == "biberon") }
    val bottleMl = d.items.filter { it.t >= sod && it.kind == "biberon" }.sumOf { it.entry?.ml ?: 0 }
    val pumpMl = d.items.filter { it.t >= sod && it.kind == "tiralatte" }.sumOf { it.entry?.ml ?: 0 }
    val pees = d.items.count { it.t >= sod && it.kind == "pannolino" && it.entry?.kind != "cacca" }
    val poops = d.items.count { it.t >= sod && it.kind == "pannolino" && it.entry?.kind != "pipi" }
    var sleepMs = 0L
    for (e in entries) if (e.type == "sonno") {
        sleepMs += (minOf(e.end, now) - maxOf(e.t, sod)).coerceAtLeast(0L)
    }
    val sl = baby.sleepStart
    if (sl != null) sleepMs += (now - maxOf(sl, sod)).coerceAtLeast(0L)


    val quick = rememberQuick(vm, baby, snack)

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ----- poppata -----
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (timer != null) {
                        val done = entries.filter { it.session == timer.session }.sumOf { it.dur }
                        val live = if (running) ((now - (timer.start ?: now)) / 1000).toInt() else 0
                        Text(
                            (if (running) stringResource(R.string.nursing_now, sideLabel(timer.side))
                            else stringResource(R.string.paused)).uppercase(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, letterSpacing = 2.sp
                        )
                        Text(clock((done + live).toLong()), fontSize = 64.sp, fontWeight = FontWeight.ExtraBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (running) {
                                val other = if (timer.side == "sx") "dx" else "sx"
                                FilledTonalButton(onClick = { vm.timer(baby, "switch") }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(18.dp)) {
                                    Text(stringResource(R.string.btn_switch_to, sideLabel(other)), fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(onClick = { vm.timer(baby, "pause") }, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(18.dp)) { Text(stringResource(R.string.btn_pause)) }
                            } else {
                                FilledTonalButton(onClick = { vm.timer(baby, "resume") }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(18.dp)) {
                                    Text(stringResource(R.string.btn_resume), fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(onClick = { vm.timer(baby, "cancel") }, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(18.dp)) { Text(stringResource(R.string.btn_discard)) }
                            }
                        }
                        Button(onClick = { vm.timer(baby, "stop") }, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(20.dp)) {
                            Text(stringResource(R.string.btn_done), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    } else {
                        val sideColor = if (nextSide == "sx") Color(0xFFE8756B) else Color(0xFF5B96D8)
                        Text(
                            stringResource(R.string.next_feed_title).uppercase(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, letterSpacing = 2.sp
                        )
                        val headFmt = stringResource(R.string.start_from, "\u0001")
                        val headParts = headFmt.split("\u0001")
                        Text(
                            androidx.compose.ui.text.buildAnnotatedString {
                                append(headParts[0])
                                withStyle(androidx.compose.ui.text.SpanStyle(color = sideColor)) { append(sideLabel(nextSide).lowercase()) }
                                if (headParts.size > 1) append(headParts[1])
                            },
                            fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp
                        )
                        val lastFeedT = d.lastFeed
                        if (lastFeedT != null) {
                            Text(
                                stringResource(R.string.last_feed_at, agoText(now - lastFeedT), timeStr(lastFeedT)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            for (s in listOf("sx", "dx")) {
                                val rec = s == nextSide
                                val col = if (s == "sx") Color(0xFFE8756B) else Color(0xFF5B96D8)
                                val bg = if (s == "sx") Color(0xFF402A2E) else Color(0xFF223248)
                                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                    Box(
                                        Modifier.fillMaxWidth().padding(top = 14.dp).height(150.dp)
                                            .clip(RoundedCornerShape(30.dp)).background(bg)
                                            .then(if (rec) Modifier.border(3.dp, col, RoundedCornerShape(30.dp)) else Modifier)
                                            .clickable { vm.timer(baby, "start", side = s) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(s.uppercase(), color = col, fontSize = 54.sp, fontWeight = FontWeight.ExtraBold)
                                            Text(sideLabel(s), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                            Text("▶ " + stringResource(R.string.start_btn), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
                                        }
                                    }
                                    if (rec) {
                                        Box(Modifier.clip(RoundedCornerShape(50)).background(col).padding(horizontal = 16.dp, vertical = 6.dp)) {
                                            Text(stringResource(R.string.her_turn).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 1.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ----- tasti rapidi -----
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("🍼", stringResource(R.string.type_bottle), Color(0xFFE8A13C), Modifier.weight(1f)) { sheet = SheetReq("biberon", null) }
                QuickTile("🥛", stringResource(R.string.type_pump), Color(0xFF9B7BE8), Modifier.weight(1f)) { sheet = SheetReq("tiralatte", null) }
                QuickTile(
                    "🌙", stringResource(R.string.tile_nap), Color(0xFF3FC59A), Modifier.weight(1f),
                    sub = if (sl != null) hDur(now - sl) else null
                ) { vm.sleep(baby, if (sl != null) "stop" else "start") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("💧", stringResource(R.string.diaper_pee), Color(0xFFE8C84A), Modifier.weight(1f)) {
                    quick(mapOf("type" to "pannolino", "t" to nowMs(), "kind" to "pipi"))
                }
                QuickTile("💩", stringResource(R.string.diaper_poop), Color(0xFFB88A5E), Modifier.weight(1f)) {
                    quick(mapOf("type" to "pannolino", "t" to nowMs(), "kind" to "cacca"))
                }
                Spacer(Modifier.weight(1f))
            }
        }

        // ----- tempo dall'ultimo ... -----
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow(stringResource(R.string.last_feed), agoText(d.lastFeed?.let { now - it }))
                    InfoRow(stringResource(R.string.last_bottle), agoText(d.lastBottle?.let { now - it }))
                    InfoRow(stringResource(R.string.last_pump), agoText(d.lastPump?.let { now - it }))
                    InfoRow(stringResource(R.string.last_diaper), agoText(d.lastDiaper?.let { now - it }))
                }
            }
        }

        // ----- oggi -----
        item {
            Text(stringResource(R.string.today), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatChip(todayFeeds.toString(), stringResource(R.string.stat_feeds), Modifier.weight(1f))
                    StatChip(fmtVol(bottleMl, oz), stringResource(R.string.stat_bottle), Modifier.weight(1f))
                    StatChip(fmtVol(pumpMl, oz), stringResource(R.string.stat_pumped), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatChip(hDur(sleepMs), stringResource(R.string.stat_sleep), Modifier.weight(1f))
                    StatChip(pees.toString(), stringResource(R.string.q_pee_short), Modifier.weight(1f))
                    StatChip(poops.toString(), stringResource(R.string.q_poop_short), Modifier.weight(1f))
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    SheetHost(vm, baby, snack, sheet) { sheet = null }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun entryTitle(kind: String, e: Entry?, oz: Boolean, session: Session?): String = when (kind) {
    "seno" -> {
        val parts = session?.segs?.map { g -> sideLabel(g.side) + " " + (g.dur / 60).coerceAtLeast(1) + "'" } ?: emptyList()
        "🤱 " + stringResource(R.string.type_breast) + " · " + parts.joinToString(" → ")
    }
    "biberon" -> "🍼 " + stringResource(R.string.type_bottle) + " · " + fmtVol(e?.ml ?: 0, oz) + " · " +
        stringResource(if (e?.milk == "formula") R.string.milk_formula else R.string.milk_breast)
    "tiralatte" -> "🥛 " + stringResource(R.string.type_pump) + " · " + fmtVol(e?.ml ?: 0, oz) + " · " + sideLabel(e?.side)
    "pannolino" -> when (e?.kind) {
        "cacca" -> "💩 " + stringResource(R.string.diaper_poop)
        "entrambi" -> "💧💩 " + stringResource(R.string.diaper_both)
        else -> "💧 " + stringResource(R.string.diaper_pee)
    }
    "bagno" -> "🛁 " + stringResource(R.string.type_bath)
    "farmaco" -> "💊 " + medName(e?.name) + (if (!e?.dose.isNullOrBlank()) " · " + e?.dose else "")
    "solido" -> "🥣 " + (e?.name ?: "") + (if (e?.kind == "nuovo") " · " + stringResource(R.string.solid_new) else "")
    "febbre" -> "🌡 " + String.format(java.util.Locale.getDefault(), "%.1f °C", e?.temp ?: 0.0)
    "crescita" -> "📏 " + growthText(e)
    "tappa" -> "⭐ " + milestoneLabel(e?.name ?: "")
    else -> "🌙 " + stringResource(R.string.type_sleep) + " · " + hDur((e?.end ?: (e?.t ?: 0L)) - (e?.t ?: 0L))
}

fun growthText(e: Entry?): String {
    if (e == null) return ""
    val parts = mutableListOf<String>()
    if (e.wG > 0) parts.add(String.format(java.util.Locale.getDefault(), "%.2f kg", e.wG / 1000.0))
    if (e.lMm > 0) parts.add(String.format(java.util.Locale.getDefault(), "%.1f cm", e.lMm / 10.0))
    if (e.hMm > 0) parts.add(String.format(java.util.Locale.getDefault(), "%.1f cm ⌀", e.hMm / 10.0))
    return parts.joinToString(" · ")
}

@Composable
fun HistoryRow(row: Item, gap: Long?, oz: Boolean, onClick: () -> Unit) {
    val e = row.entry
    val title = entryTitle(row.kind, e, oz, row.session)
    var sub = ""
    when (row.kind) {
        "seno" -> sub = ((row.session?.sec ?: 0) / 60).coerceAtLeast(1).toString() + " min"
        "biberon", "tiralatte" -> if (gap != null) sub = stringResource(R.string.since_prev, hDur(gap))
        "sonno" -> sub = timeStr(row.t) + " – " + timeStr(e?.end ?: row.t)
    }
    if (e?.note?.isNotBlank() == true) sub = (if (sub.isEmpty()) "" else "$sub · ") + e.note
    Card(Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(timeStr(row.t), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
