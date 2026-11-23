package it.venturini.poppy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GrowthScreen(vm: AppVM, baby: Baby, onSheet: (SheetReq) -> Unit) {
    val now = nowMs()
    val months = ageMonths(baby.birth, now)
    var pickBirth by remember { mutableStateOf(false) }
    var dlg by remember { mutableStateOf<Milestone?>(null) }
    var dlgT by remember { mutableLongStateOf(nowMs()) }
    val done = vm.health.filter { it.type == "tappa" }.associateBy { it.name }
    val solids = vm.health.filter { it.type == "solido" }.sortedByDescending { it.t }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (baby.birth == null) {
                    Text(stringResource(R.string.birth_needed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { pickBirth = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.set_birth)) }
                } else {
                    Text(baby.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(ageText(baby.birth, now), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (baby.birth != null && months >= SOLIDS_FROM_MONTHS) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.solids_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.solids_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (s in solids.take(12)) {
                        Row(Modifier.fillMaxWidth().clickable { onSheet(SheetReq("solido", s)) }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text((s.name ?: "") + if (s.kind == "nuovo") " ★" else "", fontWeight = FontWeight.Medium)
                            Text(dateTimeStr(s.t), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(onClick = { onSheet(SheetReq("solido", null)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_solid)) }
                }
            }
        }

        if (baby.birth != null) {
            Text(stringResource(R.string.milestones), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val visible = MILESTONES.filter { it.from <= months || done.containsKey(it.key) }
            if (visible.isEmpty()) Text(stringResource(R.string.milestones_soon), color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (m in visible) {
                val got = done[m.key]
                Card(Modifier.fillMaxWidth().clickable { dlgT = got?.t ?: nowMs(); dlg = m }, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text((if (got != null) "✓ " else "○ ") + milestoneLabel(m.key), fontWeight = FontWeight.Medium)
                        if (got != null) Text(dateTimeStr(got.t), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(stringResource(R.string.milestones_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (pickBirth) BirthPicker(baby.birth, { vm.setBirth(baby.id, it); pickBirth = false }, { pickBirth = false })

    val m = dlg
    if (m != null) {
        val got = done[m.key]
        AlertDialog(
            onDismissRequest = { dlg = null },
            title = { Text(milestoneLabel(m.key)) },
            text = { TimeAdjuster(stringResource(R.string.milestone_when), dlgT) { dlgT = it } },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveEntry(baby.id, got?.id, mapOf("type" to "tappa", "t" to dlgT, "name" to m.key)) {}
                    dlg = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    if (got != null) TextButton(onClick = { vm.deleteEntry(baby.id, got.id); dlg = null }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { dlg = null }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }
}
