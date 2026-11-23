package it.venturini.poppy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
fun sideFull(s: String?): String = when (s) {
    "sx" -> stringResource(R.string.side_left_full)
    "dx" -> stringResource(R.string.side_right_full)
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
            val runSide = timer?.side
            val bgCard = if (running && runSide == "sx") pal.sxSoft else if (running && runSide == "dx") pal.dxSoft else pal.card
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = bgCard)
            ) {
                Column(Modifier.padding(20.dp)) {
                    if (timer != null) {
                        val done = entries.filter { it.session == timer.session }.sumOf { it.dur }
                        val live = if (running) ((now - (timer.start ?: now)) / 1000).toInt() else 0
                        val sc = if (runSide == "sx") pal.sx else pal.dx
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                (if (running) stringResource(R.string.nursing_now, sideFull(timer.side)) else stringResource(R.string.paused)).uppercase(),
                                color = if (running) sc else pal.muted, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.1.sp
                            )
                            Text(
                                clock((done + live).toLong()), fontSize = 68.sp, fontWeight = FontWeight.ExtraBold,
                                color = if (running) pal.ink else pal.muted, modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        if (running) {
                            val other = if (timer.side == "sx") "dx" else "sx"
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PBtn(stringResource(R.string.btn_switch_to, sideFull(other)), pal.card, pal.ink, Modifier.weight(1f)) { vm.timer(baby, "switch") }
                                PBtn(stringResource(R.string.btn_pause), pal.card, pal.ink, Modifier.weight(1f)) { vm.timer(baby, "pause") }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PBtn(stringResource(R.string.btn_resume), pal.bg, pal.ink, Modifier.weight(1f)) { vm.timer(baby, "resume") }
                                PBtn(stringResource(R.string.btn_discard), pal.bg, pal.ink, Modifier.weight(1f)) { vm.timer(baby, "cancel") }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        PBtn(stringResource(R.string.btn_done), pal.ink, pal.bg, Modifier.fillMaxWidth()) { vm.timer(baby, "stop") }
                    } else {
                        val sideColor = if (nextSide == "sx") pal.sx else pal.dx
                        Text(
                            stringResource(R.string.next_feed_title).uppercase(),
                            color = pal.muted, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.1.sp
                        )
                        val headFmt = stringResource(R.string.start_from, "\u0001")
                        val headParts = headFmt.split("\u0001")
                        Text(
                            androidx.compose.ui.text.buildAnnotatedString {
                                append(headParts[0])
                                withStyle(androidx.compose.ui.text.SpanStyle(color = sideColor)) { append(sideFull(nextSide).lowercase()) }
                                if (headParts.size > 1) append(headParts[1])
                            },
                            fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 3.dp)
                        )
                        val lastFeedT = d.lastFeed
                        if (lastFeedT != null) {
                            Text(
                                stringResource(R.string.last_feed_at, agoText(now - lastFeedT), timeStr(lastFeedT)),
                                color = pal.muted, fontSize = 15.sp, fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(22.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (s in listOf("sx", "dx")) {
                                val rec = s == nextSide
                                val col = if (s == "sx") pal.sx else pal.dx
                                val bg = if (s == "sx") pal.sxSoft else pal.dxSoft
                                Box(Modifier.weight(1f)) {
                                    Column(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(bg)
                                            .then(if (rec) Modifier.border(3.dp, col, RoundedCornerShape(22.dp)) else Modifier)
                                            .clickable { vm.timer(baby, "start", side = s) }
                                            .padding(top = 26.dp, bottom = 20.dp, start = 10.dp, end = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(s.uppercase(), color = col, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
                                        Text(sideFull(s), color = pal.ink.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text("▶ " + stringResource(R.string.start_btn), color = pal.ink.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (rec) {
                                        Box(
                                            Modifier.align(Alignment.TopCenter).offset(y = (-10).dp)
                                                .clip(RoundedCornerShape(50)).background(col).padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(stringResource(R.string.her_turn).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.7.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ----- nanna in corso -----
        if (sl != null) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(pal.napSoft).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(pal.card), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(Ico.moon, null, tint = pal.nap, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(hDur(now - sl), color = pal.nap, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text(stringResource(R.string.sleeping, hDur(now - sl)), color = pal.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(15.dp)).background(pal.nap).clickable { vm.sleep(baby, "stop") }.padding(horizontal = 14.dp, vertical = 13.dp)
                    ) { Text(stringResource(R.string.sleep_stop), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
                }
            }
        }

        // ----- tasti rapidi -----
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(Ico.bottle, stringResource(R.string.type_bottle), pal.bot, pal.botSoft, Modifier.weight(1f)) { sheet = SheetReq("biberon", null) }
                QuickTile(Ico.drop, stringResource(R.string.type_pump), pal.pump, pal.pumpSoft, Modifier.weight(1f)) { sheet = SheetReq("tiralatte", null) }
                QuickTile(Ico.moon, stringResource(R.string.tile_nap), pal.nap, pal.napSoft, Modifier.weight(1f)) {
                    if (sl == null) vm.sleep(baby, "start") else vm.sleep(baby, "stop")
                }
                QuickTile(Ico.diaper, stringResource(R.string.type_diaper), pal.ink, pal.line, Modifier.weight(1f)) { sheet = SheetReq("pannolino", null) }
            }
        }

        // ----- tempo dall'ultimo ... -----
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = pal.card)) {
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
            Text(stringResource(R.string.today), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatChip(todayFeeds.toString(), stringResource(R.string.stat_feeds), Modifier.weight(1f))
                StatChip(fmtVol(bottleMl, oz), stringResource(R.string.stat_bottle), Modifier.weight(1f))
                StatChip(fmtVol(pumpMl, oz), stringResource(R.string.stat_pumped), Modifier.weight(1f))
                StatChip(hDur(sleepMs), stringResource(R.string.stat_sleep), Modifier.weight(1f))
                StatChip((pees + poops).toString(), stringResource(R.string.type_diaper), Modifier.weight(1f))
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
    val soft = when (row.kind) {
        "seno" -> pal.sxSoft
        "biberon" -> pal.botSoft
        "tiralatte" -> pal.pumpSoft
        "sonno" -> pal.napSoft
        else -> pal.line
    }
    val shown = if (title.contains(' ')) title.substringAfter(' ') else title
    val icon = if (title.contains(' ')) title.substringBefore(' ') else ""
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.card).clickable { onClick() }
            .padding(start = 11.dp, top = 11.dp, bottom = 11.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(soft), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(shown, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = pal.ink)
            if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = pal.muted, modifier = Modifier.padding(top = 3.dp))
        }
        Text(timeStr(row.t), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = pal.ink)
    }
}
