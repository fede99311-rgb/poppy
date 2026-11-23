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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun HealthScreen(vm: AppVM, onSheet: (SheetReq) -> Unit) {
    val h = vm.health
    val now = nowMs()
    val today = startOfDay(now)
    val day = 86_400_000L
    val vit = h.filter { it.type == "farmaco" && it.name == VITD }
    val meds = h.filter { it.type == "farmaco" && it.name != VITD }.sortedByDescending { it.t }
    val fevers = h.filter { it.type == "febbre" }.sortedByDescending { it.t }
    val growth = h.filter { it.type == "crescita" }.sortedBy { it.t }
    val vitDays = (6 downTo 0).map { n -> vit.any { startOfDay(it.t) == today - n * day } }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Block(stringResource(R.string.vitd)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                vitDays.forEach { ok -> Text(if (ok) "●" else "○", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge) }
            }
            val last = vit.maxByOrNull { it.t }
            Text(
                if (last != null) stringResource(R.string.last_given, dateTimeStr(last.t)) else stringResource(R.string.never_given),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { onSheet(SheetReq("farmaco", Entry("", "farmaco", nowMs(), null, 0, 0, null, 0, null, null, name = VITD))) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.give_vitd)) }
        }

        Block(stringResource(R.string.type_med)) {
            if (meds.isEmpty()) Text(stringResource(R.string.none_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (m in meds.take(15)) {
                Row(Modifier.fillMaxWidth().clickable { onSheet(SheetReq("farmaco", m)) }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(medName(m.name) + (if (!m.dose.isNullOrBlank()) " · " + m.dose else ""), fontWeight = FontWeight.Medium)
                    Text(dateTimeStr(m.t), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = { onSheet(SheetReq("farmaco", null)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_med)) }
        }

        Block(stringResource(R.string.type_fever)) {
            if (fevers.isEmpty()) Text(stringResource(R.string.none_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (f in fevers.take(10)) {
                Row(Modifier.fillMaxWidth().clickable { onSheet(SheetReq("febbre", f)) }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtNum(f.temp) + " °C", fontWeight = FontWeight.Medium)
                    Text(dateTimeStr(f.t), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = { onSheet(SheetReq("febbre", null)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_fever)) }
        }

        Block(stringResource(R.string.type_growth)) {
            val w = growth.filter { it.wG > 0 }
            if (w.size >= 2) LineChart(stringResource(R.string.growth_weight_chart), w.map { it.wG / 1000f }, fmtNum(w.last().wG / 1000.0) + " kg", MaterialTheme.colorScheme.primary)
            val l = growth.filter { it.lMm > 0 }
            if (l.size >= 2) LineChart(stringResource(R.string.growth_length_chart), l.map { it.lMm / 10f }, fmtNum(l.last().lMm / 10.0) + " cm", MaterialTheme.colorScheme.tertiary)
            if (growth.isEmpty()) Text(stringResource(R.string.none_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (g in growth.reversed().take(10)) {
                val parts = listOfNotNull(
                    if (g.wG > 0) fmtNum(g.wG / 1000.0) + " kg" else null,
                    if (g.lMm > 0) fmtNum(g.lMm / 10.0) + " cm" else null,
                    if (g.hMm > 0) fmtNum(g.hMm / 10.0) + " cm ⌀" else null
                )
                Row(Modifier.fillMaxWidth().clickable { onSheet(SheetReq("crescita", g)) }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(parts.joinToString(" · "), fontWeight = FontWeight.Medium)
                    Text(dateTimeStr(g.t), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = { onSheet(SheetReq("crescita", null)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_growth)) }
        }
        Text(stringResource(R.string.health_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
