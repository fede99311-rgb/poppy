package it.venturini.poppy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.Icon
import androidx.compose.ui.text.withStyle
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun hShort(ms: Long): String {
    val m = Math.round(ms / 60000.0).toInt()
    return if (m < 60) "$m\u2032" else (m / 60).toString() + "h" + (if (m % 60 > 0) (m % 60).toString().padStart(2, '0') else "")
}

private fun overlapMs(a1: Long, a2: Long, b1: Long, b2: Long): Long = maxOf(0L, minOf(a2, b2) - maxOf(a1, b1))

@Composable
private fun SideChip(side: String?, text: String) {
    val bg = when (side) { "sx" -> pal.sxSoft; "dx" -> pal.dxSoft; else -> pal.pumpSoft }
    val fg = when (side) { "sx" -> pal.sx; "dx" -> pal.dx; else -> pal.pump }
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun FeedRow(row: Item, gap: Long?, slept: Long, oz: Boolean, onClick: () -> Unit) {
    val nap = row.kind == "sonno"
    val e = row.entry
    val icon = when (row.kind) { "seno" -> Ico.heart; "biberon" -> Ico.bottle; "sonno" -> Ico.moon; else -> Ico.drop }
    val fg = when (row.kind) {
        "seno" -> if (row.session?.firstSide == "dx") pal.dx else pal.sx
        "biberon" -> pal.bot; "sonno" -> pal.nap; else -> pal.pump
    }
    val soft = when (row.kind) {
        "seno" -> if (row.session?.firstSide == "dx") pal.dxSoft else pal.sxSoft
        "biberon" -> pal.botSoft; "sonno" -> pal.napSoft; else -> pal.pumpSoft
    }
    val dashColor = pal.napSoft
    val mod = if (nap) Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .drawBehind { drawRoundRect(dashColor, cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))) }
    else Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.card)
    Row(
        mod.clickable { onClick() }.padding(start = 11.dp, top = if (nap) 8.dp else 11.dp, bottom = if (nap) 8.dp else 11.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(if (nap) 34.dp else 42.dp).clip(RoundedCornerShape(if (nap) 11.dp else 13.dp)).background(soft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(if (nap) 18.dp else 24.dp))
        }
        Column(Modifier.weight(1f)) {
            val title = when (row.kind) {
                "seno" -> stringResource(R.string.type_breast)
                "biberon" -> fmtVol(e?.ml ?: 0, oz)
                else -> stringResource(R.string.type_sleep) + " \u00b7 " + hDur((e?.end ?: row.t) - row.t)
            }
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = if (nap) 15.sp else 16.sp, color = if (nap) pal.nap else pal.ink)
            when (row.kind) {
                "seno" -> Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (g in row.session?.segs ?: emptyList()) SideChip(g.side, (g.side ?: "").uppercase() + " " + (g.dur / 60).coerceAtLeast(1) + "\u2032")
                }
                "biberon" -> Text(
                    stringResource(R.string.type_bottle) + " \u00b7 " + stringResource(if (e?.milk == "formula") R.string.milk_formula else R.string.milk_breast),
                    fontSize = 13.sp, color = pal.muted, modifier = Modifier.padding(top = 3.dp)
                )
                else -> Text(timeStr(row.t) + " \u2192 " + timeStr(e?.end ?: row.t), fontSize = 13.sp, color = pal.muted, modifier = Modifier.padding(top = 3.dp))
            }
            if (gap != null) {
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append(stringResource(R.string.gap_before, hDur(gap)))
                        if (slept >= 60000L) {
                            append(" \u00b7 ")
                            withStyle(androidx.compose.ui.text.SpanStyle(color = pal.nap)) { append(stringResource(R.string.slept_in, hDur(slept))) }
                        }
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pal.muted, modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        Text(timeStr(row.t), fontWeight = FontWeight.ExtraBold, fontSize = if (nap) 14.sp else 16.sp, color = if (nap) pal.muted else pal.ink)
    }
}

@Composable
private fun PumpRow(row: Item, oz: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.card).clickable { onClick() }
            .padding(start = 11.dp, top = 11.dp, bottom = 11.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(pal.pumpSoft), contentAlignment = Alignment.Center) {
            Icon(Ico.drop, null, tint = pal.pump, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(fmtVol(row.entry?.ml ?: 0, oz), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = pal.ink)
            Box(Modifier.padding(top = 3.dp)) { SideChip(row.entry?.side, sideFull(row.entry?.side)) }
        }
        Text(timeStr(row.t), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = pal.ink)
    }
}

@Composable
fun HistoryScreen(vm: AppVM, baby: Baby, d: Derived, snack: SnackbarHostState) {
    var sheet by remember { mutableStateOf<SheetReq?>(null) }
    var sessionDlg by remember { mutableStateOf<Session?>(null) }
    var tab by remember { mutableStateOf("feed") }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val oz = vm.oz
    val now = nowMs()
    val sod = startOfDay(now)

    val tabs = listOf(
        "feed" to stringResource(R.string.hist_feed),
        "pump" to stringResource(R.string.hist_pump),
        "diaper" to stringResource(R.string.type_diaper),
        "other" to stringResource(R.string.hist_other)
    )
    val others = setOf("bagno", "farmaco", "febbre", "solido")
    val items = when (tab) {
        "feed" -> d.items.filter { it.kind == "seno" || it.kind == "biberon" || it.kind == "sonno" }
        "pump" -> d.items.filter { it.kind == "tiralatte" }
        "diaper" -> d.items.filter { it.kind == "pannolino" }
        else -> d.items.filter { it.kind in others }
    }.sortedByDescending { it.t }.take(150)

    // intervallo dal pasto prima + sonno nel mezzo (come nella webapp)
    val meals = d.items.filter { it.kind == "seno" || it.kind == "biberon" }.sortedBy { it.t }
    val gapMap = HashMap<String, Long>()
    val sleptMap = HashMap<String, Long>()
    for (i in 1 until meals.size) {
        val m = meals[i]
        val p = meals[i - 1]
        gapMap[m.key] = m.t - p.t
        var sl = 0L
        for (e in vm.entries) if (e.type == "sonno") sl += overlapMs(e.t, e.end, p.t, m.t)
        sleptMap[m.key] = sl
    }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(pal.line).padding(4.dp)) {
                for ((k, lbl) in tabs) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                            .background(if (tab == k) pal.card else Color.Transparent)
                            .clickable { tab = k }.padding(vertical = 10.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(lbl, color = if (tab == k) pal.ink else pal.muted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
        if (items.isEmpty()) {
            item { Text(stringResource(R.string.empty_history), color = pal.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(vertical = 34.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        var lastDay = -1L
        for ((idx, row) in items.withIndex()) {
            val day = startOfDay(row.t)
            if (day != lastDay) {
                lastDay = day
                val dayItems = items.filter { startOfDay(it.t) == day }
                val tot = when (tab) {
                    "feed" -> {
                        val ms = dayItems.filter { it.kind != "sonno" }
                        val sec = dayItems.filter { it.kind == "seno" }.sumOf { it.session?.sec ?: 0 }
                        val ml = dayItems.filter { it.kind == "biberon" }.sumOf { it.entry?.ml ?: 0 }
                        var sl = 0L
                        for (e in vm.entries) if (e.type == "sonno") sl += overlapMs(e.t, e.end, day, minOf(day + 86400000L, now))
                        ctx.getString(R.string.hist_meals, ms.size) + " \u00b7 " + Math.round(sec / 60.0) + "\u2032 " + ctx.getString(R.string.hist_breast_short) +
                            (if (ml > 0) " \u00b7 " + fmtVol(ml, oz) else "") + (if (sl > 0) " \u00b7 " + hShort(sl) + " " + ctx.getString(R.string.tile_nap).lowercase() else "")
                    }
                    "pump" -> fmtVol(dayItems.sumOf { it.entry?.ml ?: 0 }, oz)
                    else -> dayItems.size.toString()
                }
                val label = when (day) {
                    sod -> ctx.getString(R.string.today)
                    startOfDay(sod - 1) -> ctx.getString(R.string.yesterday)
                    else -> dayStr(day)
                }
                item(key = "d$day$tab") {
                    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label.replaceFirstChar { it.uppercase() }, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = pal.ink)
                        Text(tot, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = pal.muted)
                    }
                }
            }
            item(key = row.key + tab) {
                Box(Modifier.padding(bottom = 8.dp)) {
                    val click = { if (row.kind == "seno") sessionDlg = row.session else sheet = SheetReq(row.kind, row.entry) }
                    when (tab) {
                        "feed" -> FeedRow(row, gapMap[row.key], sleptMap[row.key] ?: 0L, oz) { click() }
                        "pump" -> PumpRow(row, oz) { click() }
                        else -> HistoryRow(row, d.gap[row.key], oz) { click() }
                    }
                }
            }
        }
    }

    SheetHost(vm, baby, snack, sheet) { sheet = null }

    val sd = sessionDlg
    if (sd != null) {
        AlertDialog(
            onDismissRequest = { sessionDlg = null },
            title = { Text(stringResource(R.string.session_title) + " · " + timeStr(sd.t)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (seg in sd.segs) {
                        TextButton(onClick = { sessionDlg = null; sheet = SheetReq("seno", seg) }) {
                            Text(sideLabel(seg.side) + " · " + (seg.dur / 60).coerceAtLeast(1) + " min · " + timeStr(seg.t))
                        }
                    }
                    TextButton(onClick = { vm.deleteSession(baby.id, sd.segs); sessionDlg = null }) {
                        Text(stringResource(R.string.delete_session), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.timer(baby, "resume", session = sd.id); sessionDlg = null }) {
                    Text(stringResource(R.string.resume_session))
                }
            },
            dismissButton = { TextButton(onClick = { sessionDlg = null }) { Text(stringResource(R.string.close)) } }
        )
    }
}
