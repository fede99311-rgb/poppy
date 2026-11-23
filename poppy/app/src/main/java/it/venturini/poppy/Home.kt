package it.venturini.poppy

import androidx.compose.foundation.clickable
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
    var sessionDlg by remember { mutableStateOf<Session?>(null) }
    var filter by remember { mutableStateOf("all") }

    val entries = vm.entries
    val oz = vm.oz
    val savedText = stringResource(R.string.saved)
    val undoText = stringResource(R.string.undo)

    val timer = baby.timer
    val running = timer?.start != null
    val nextSide = if (vm.fixedSide == "auto") d.nextSide else vm.fixedSide

    val sod = startOfDay(now)
    val todayFeeds = d.items.count { it.t >= sod && (it.kind == "seno" || it.kind == "biberon") }
    val bottleMl = d.items.filter { it.t >= sod && it.kind == "biberon" }.sumOf { it.entry?.ml ?: 0 }
    val pumpMl = d.items.filter { it.t >= sod && it.kind == "tiralatte" }.sumOf { it.entry?.ml ?: 0 }
    val pees = d.items.count { it.t >= sod && it.kind == "pannolino" && it.entry?.kind != "cacca" }
    val poops = d.items.count { it.t >= sod && it.kind == "pannolino" && it.entry?.kind != "pipi" }
    val vitDone = entries.any { it.type == "farmaco" && it.name == VITD && it.t >= sod }
    var sleepMs = 0L
    for (e in entries) if (e.type == "sonno") {
        sleepMs += (minOf(e.end, now) - maxOf(e.t, sod)).coerceAtLeast(0L)
    }
    val sl = baby.sleepStart
    if (sl != null) sleepMs += (now - maxOf(sl, sod)).coerceAtLeast(0L)

    val visible = d.items.filter { filter == "all" || it.kind == filter }

    fun quick(data: Map<String, Any?>) {
        vm.saveEntry(baby.id, null, data) { id ->
            scope.launch {
                val r = snack.showSnackbar(savedText, actionLabel = undoText, duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) vm.deleteEntry(baby.id, id)
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ----- poppata -----
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (timer != null) {
                        val done = entries.filter { it.session == timer.session }.sumOf { it.dur }
                        val live = if (running) ((now - (timer.start ?: now)) / 1000).toInt() else 0
                        Text(
                            if (running) stringResource(R.string.nursing_now, sideLabel(timer.side))
                            else stringResource(R.string.paused),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(clock((done + live).toLong()), fontSize = 52.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (running) {
                                val other = if (timer.side == "sx") "dx" else "sx"
                                FilledTonalButton(onClick = { vm.timer(baby, "switch") }, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.btn_switch_to, sideLabel(other)))
                                }
                                OutlinedButton(onClick = { vm.timer(baby, "pause") }) { Text(stringResource(R.string.btn_pause)) }
                            } else {
                                FilledTonalButton(onClick = { vm.timer(baby, "resume") }, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.btn_resume))
                                }
                                OutlinedButton(onClick = { vm.timer(baby, "cancel") }) { Text(stringResource(R.string.btn_discard)) }
                            }
                        }
                        Button(onClick = { vm.timer(baby, "stop") }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            Text(stringResource(R.string.btn_done))
                        }
                    } else {
                        Text(stringResource(R.string.next_side, sideLabel(nextSide)), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (s in listOf("sx", "dx")) {
                                val rec = s == nextSide
                                Button(
                                    onClick = { vm.timer(baby, "start", side = s) },
                                    modifier = Modifier.weight(1f).height(72.dp),
                                    colors = if (rec) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                                ) { Text(sideLabel(s), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }

        // ----- sonno -----
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Row(
                    Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (sl != null) {
                        Text("🌙 " + stringResource(R.string.sleeping, hDur(now - sl)))
                        Button(onClick = { vm.sleep(baby, "stop") }) { Text(stringResource(R.string.sleep_stop)) }
                    } else {
                        Text("🌙 " + stringResource(R.string.type_sleep))
                        FilledTonalButton(onClick = { vm.sleep(baby, "start") }) { Text(stringResource(R.string.sleep_start)) }
                    }
                }
            }
        }

        // ----- biberon / tiralatte -----
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { sheet = SheetReq("biberon", null) },
                    modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) { Text(stringResource(R.string.quick_bottle)) }
                Button(
                    onClick = { sheet = SheetReq("tiralatte", null) },
                    modifier = Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) { Text(stringResource(R.string.quick_pump)) }
            }
        }

        // ----- azioni rapide -----
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { quick(mapOf("type" to "pannolino", "t" to nowMs(), "kind" to "pipi")) }) {
                    Text(stringResource(R.string.q_pee))
                }
                FilledTonalButton(onClick = { quick(mapOf("type" to "pannolino", "t" to nowMs(), "kind" to "cacca")) }) {
                    Text(stringResource(R.string.q_poop))
                }
                FilledTonalButton(onClick = { quick(mapOf("type" to "bagno", "t" to nowMs())) }) {
                    Text(stringResource(R.string.q_bath))
                }
                FilledTonalButton(onClick = { sheet = SheetReq("rumore", null) }) {
                    Text(stringResource(R.string.q_noise))
                }
                FilledTonalButton(onClick = { quick(mapOf("type" to "farmaco", "t" to nowMs(), "name" to VITD)) }) {
                    Text(stringResource(R.string.q_vitd))
                }
            }
        }

        // ----- tempo dall'ultimo ... -----
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow(stringResource(R.string.last_feed), agoText(d.lastFeed?.let { now - it }))
                    InfoRow(stringResource(R.string.last_bottle), agoText(d.lastBottle?.let { now - it }))
                    InfoRow(stringResource(R.string.last_pump), agoText(d.lastPump?.let { now - it }))
                    InfoRow(stringResource(R.string.last_diaper), agoText(d.lastDiaper?.let { now - it }))
                    InfoRow(stringResource(R.string.last_bath), agoText(d.lastBath?.let { now - it }))
                }
            }
        }

        // ----- oggi -----
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat(stringResource(R.string.stat_feeds), todayFeeds.toString())
                        Stat(stringResource(R.string.stat_bottle), fmtVol(bottleMl, oz))
                        Stat(stringResource(R.string.stat_pumped), fmtVol(pumpMl, oz))
                        Stat(stringResource(R.string.stat_sleep), hDur(sleepMs))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat(stringResource(R.string.q_pee_short), pees.toString())
                        Stat(stringResource(R.string.q_poop_short), poops.toString())
                        Stat(stringResource(R.string.vitd), if (vitDone) "✓" else "—")
                    }
                }
            }
        }

        // ----- filtri -----
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val opts = listOf(
                    "all" to R.string.filter_all, "seno" to R.string.type_breast, "biberon" to R.string.type_bottle,
                    "tiralatte" to R.string.type_pump, "sonno" to R.string.type_sleep, "pannolino" to R.string.type_diaper,
                    "bagno" to R.string.type_bath, "rumore" to R.string.type_noise, "farmaco" to R.string.type_med,
                    "febbre" to R.string.type_fever, "solido" to R.string.type_solid
                )
                for ((k, r) in opts) {
                    FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(stringResource(r)) })
                }
            }
        }

        if (visible.isEmpty()) {
            item { Text(stringResource(R.string.empty_history), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        var lastDay = -1L
        for (row in visible) {
            val day = startOfDay(row.t)
            if (day != lastDay) {
                lastDay = day
                item(key = "h$day") {
                    val label = when (day) {
                        sod -> stringResource(R.string.today)
                        startOfDay(sod - 1) -> stringResource(R.string.yesterday)
                        else -> dayStr(day)
                    }
                    Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item(key = row.key) {
                HistoryRow(row, d.gap[row.key], oz) {
                    if (row.kind == "seno") sessionDlg = row.session
                    else sheet = SheetReq(row.kind, row.entry)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
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
    "rumore" -> "🔊 " + noiseLabel(e?.name) + (if ((e?.dur ?: 0) > 0) " · " + ((e?.dur ?: 0) / 60).coerceAtLeast(1) + " min" else "")
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
    Card(Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(timeStr(row.t), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
