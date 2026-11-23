package it.venturini.poppy

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

@Composable
fun HistoryScreen(vm: AppVM, baby: Baby, d: Derived, snack: SnackbarHostState) {
    var sheet by remember { mutableStateOf<SheetReq?>(null) }
    var sessionDlg by remember { mutableStateOf<Session?>(null) }
    var section by remember { mutableStateOf("all") }
    val oz = vm.oz
    val now = nowMs()
    val sod = startOfDay(now)

    val sections = listOf(
        "all" to stringResource(R.string.hist_overview),
        "seno" to stringResource(R.string.type_breast),
        "biberon" to stringResource(R.string.type_bottle),
        "tiralatte" to stringResource(R.string.type_pump),
        "sonno" to stringResource(R.string.type_sleep),
        "pannolino" to stringResource(R.string.type_diaper),
        "altro" to stringResource(R.string.hist_other)
    )
    val others = setOf("bagno", "farmaco", "febbre", "solido")
    val visible = d.items.filter { section != "all" && (if (section == "altro") it.kind in others else it.kind == section) }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, lbl) in sections) {
                    FilterChip(
                        colors = chipColors(), border = null, shape = ChipShape,
                        selected = section == k, onClick = { section = k },
                        label = { Text(lbl, fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp)) }
                    )
                }
            }
        }

        if (section == "all") {
            val days = d.items.map { startOfDay(it.t) }.distinct().take(30)
            if (days.isEmpty()) item { Text(stringResource(R.string.empty_history), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            for (day in days) {
                item(key = "o$day") {
                    val its = d.items.filter { startOfDay(it.t) == day }
                    val feeds = its.count { it.kind == "seno" || it.kind == "biberon" }
                    val bottle = its.filter { it.kind == "biberon" }.sumOf { it.entry?.ml ?: 0 }
                    val pump = its.filter { it.kind == "tiralatte" }.sumOf { it.entry?.ml ?: 0 }
                    val pees = its.count { it.kind == "pannolino" && it.entry?.kind != "cacca" }
                    val poops = its.count { it.kind == "pannolino" && it.entry?.kind != "pipi" }
                    var sleepMs = 0L
                    for (e in vm.entries) if (e.type == "sonno") sleepMs += (minOf(e.end, day + 86400000L) - maxOf(e.t, day)).coerceAtLeast(0L)
                    val label = when (day) {
                        sod -> stringResource(R.string.today)
                        startOfDay(sod - 1) -> stringResource(R.string.yesterday)
                        else -> dayStr(day)
                    }
                    Card(
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(label, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatChip(feeds.toString(), stringResource(R.string.stat_feeds), Modifier.weight(1f))
                                StatChip(fmtVol(bottle, oz), stringResource(R.string.stat_bottle), Modifier.weight(1f))
                                StatChip(fmtVol(pump, oz), stringResource(R.string.stat_pumped), Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatChip(hDur(sleepMs), stringResource(R.string.stat_sleep), Modifier.weight(1f))
                                StatChip(pees.toString(), stringResource(R.string.q_pee_short), Modifier.weight(1f))
                                StatChip(poops.toString(), stringResource(R.string.q_poop_short), Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else {
            if (visible.isEmpty()) item { Text(stringResource(R.string.empty_history), color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                item(key = row.key) {
                    HistoryRow(row, d.gap[row.key], oz) {
                        if (row.kind == "seno") sessionDlg = row.session
                        else sheet = SheetReq(row.kind, row.entry)
                    }
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
