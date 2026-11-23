package it.venturini.poppy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun StatsScreen(vm: AppVM, d: Derived) {
    var days by remember { mutableIntStateOf(7) }
    val oz = vm.oz
    val today = startOfDay(nowMs())
    val day = 86_400_000L
    fun idx(t: Long): Int = ((startOfDay(t) - (today - (days - 1) * day)) / day).toInt()
    val feeds = FloatArray(days)
    val bottle = FloatArray(days)
    val pumped = FloatArray(days)
    val sleepH = FloatArray(days)
    val diapers = FloatArray(days)
    for (it in d.items) {
        val i = idx(it.t)
        if (i !in 0 until days) continue
        when (it.kind) {
            "seno" -> feeds[i] += 1f
            "biberon" -> { feeds[i] += 1f; bottle[i] += mlToUnit(it.entry?.ml ?: 0, oz).toFloat() }
            "tiralatte" -> pumped[i] += mlToUnit(it.entry?.ml ?: 0, oz).toFloat()
            "sonno" -> {
                val e = it.entry
                if (e != null && e.end > e.t) sleepH[i] += (e.end - e.t) / 3_600_000f
            }
            "pannolino" -> diapers[i] += 1f
        }
    }
    fun avg(a: FloatArray): String = fmtNum((a.sum() / days).toDouble())
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in listOf(7, 14, 30)) {
                FilterChip(selected = days == n, onClick = { days = n }, label = { Text(stringResource(R.string.days_n, n)) })
            }
        }
        val avgLbl = stringResource(R.string.avg_per_day)
        BarChart(stringResource(R.string.chart_feeds), feeds.toList(), avgLbl + " " + avg(feeds), MaterialTheme.colorScheme.primary)
        BarChart(stringResource(R.string.chart_bottle, unitLabel(oz)), bottle.toList(), avgLbl + " " + avg(bottle), Color(0xFF3F8FD2))
        BarChart(stringResource(R.string.chart_pumped, unitLabel(oz)), pumped.toList(), avgLbl + " " + avg(pumped), Color(0xFF8E6BC8))
        BarChart(stringResource(R.string.chart_sleep), sleepH.toList(), avgLbl + " " + avg(sleepH), Color(0xFF4F6D9A))
        BarChart(stringResource(R.string.chart_diapers), diapers.toList(), avgLbl + " " + avg(diapers), Color(0xFFD6A03A))
        Text(stringResource(R.string.chart_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
