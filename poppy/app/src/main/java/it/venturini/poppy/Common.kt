package it.venturini.poppy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun medName(name: String?): String = if (name == VITD) stringResource(R.string.vitd) else (name ?: "")

@Composable
fun noiseLabel(id: String?): String = when (id) {
    "rain" -> stringResource(R.string.noise_rain)
    "hairdryer" -> stringResource(R.string.noise_hairdryer)
    "waves" -> stringResource(R.string.noise_waves)
    "fan" -> stringResource(R.string.noise_fan)
    "shush" -> stringResource(R.string.noise_shush)
    else -> stringResource(R.string.noise_other)
}

@Composable
fun milestoneLabel(key: String): String = when (key) {
    "smile" -> stringResource(R.string.ms_smile)
    "follow" -> stringResource(R.string.ms_follow)
    "head" -> stringResource(R.string.ms_head)
    "roll" -> stringResource(R.string.ms_roll)
    "sit" -> stringResource(R.string.ms_sit)
    "tooth" -> stringResource(R.string.ms_tooth)
    "crawl" -> stringResource(R.string.ms_crawl)
    "clap" -> stringResource(R.string.ms_clap)
    "stand" -> stringResource(R.string.ms_stand)
    "wave" -> stringResource(R.string.ms_wave)
    "cruise" -> stringResource(R.string.ms_cruise)
    "step" -> stringResource(R.string.ms_step)
    else -> stringResource(R.string.ms_word)
}

@Composable
fun ageText(birth: Long?, now: Long): String {
    if (birth == null) return ""
    val a = ageParts(birth, now)
    return if (a[0] <= 0 && daysOld(birth, now) < 31) {
        stringResource(R.string.age_days, daysOld(birth, now))
    } else if (a[0] < 24) {
        if (a[1] > 0) stringResource(R.string.age_months_weeks, a[0], a[1]) else stringResource(R.string.age_months, a[0])
    } else {
        stringResource(R.string.age_years, a[0] / 12, a[0] % 12)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthPicker(initial: Long?, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = localToPicker(initial ?: nowMs()))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = st.selectedDateMillis
                if (ms != null) onPick(pickerToLocal(ms))
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    ) { DatePicker(state = st) }
}

@Composable
fun BarChart(title: String, values: List<Float>, summary: String, color: Color) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                val mx = (values.maxOrNull() ?: 0f).coerceAtLeast(0.001f)
                val n = values.size.coerceAtLeast(1)
                val gap = 4.dp.toPx()
                val w = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
                values.forEachIndexed { i, v ->
                    val h = (size.height * (v / mx)).coerceAtLeast(if (v > 0f) 3f else 1.5f)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(i * (w + gap), size.height - h),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
fun LineChart(title: String, points: List<Float>, summary: String, color: Color) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Canvas(Modifier.fillMaxWidth().height(110.dp).padding(6.dp)) {
                if (points.isEmpty()) return@Canvas
                val mn = points.minOrNull() ?: 0f
                val mx = points.maxOrNull() ?: 0f
                val span = (mx - mn).coerceAtLeast(0.001f)
                val n = points.size
                fun px(i: Int): Float = if (n == 1) size.width / 2 else size.width * i / (n - 1)
                fun py(v: Float): Float = size.height - size.height * ((v - mn) / span) * 0.9f - size.height * 0.05f
                val path = Path()
                points.forEachIndexed { i, v ->
                    if (i == 0) path.moveTo(px(i), py(v)) else path.lineTo(px(i), py(v))
                }
                if (n > 1) drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
                points.forEachIndexed { i, v -> drawCircle(color, radius = 4.dp.toPx(), center = Offset(px(i), py(v))) }
            }
        }
    }
}

val Ink = Color(0xFF1E1E2A)
val CRose = Color(0xFFFFD3DE)
val CBlue = Color(0xFFCFE6FF)
val CPurple = Color(0xFFE3D6FF)
val CYellow = Color(0xFFFFF0A8)
val CBrown = Color(0xFFEBD3BF)
val CMint = Color(0xFFCDF2DF)
val COrange = Color(0xFFFFDDBD)
val CIndigo = Color(0xFFD5DBFF)

/** Grande tasto colorato con icona (emoji) e testo. */
@Composable
fun BigTile(emoji: String, label: String, bg: Color, modifier: Modifier = Modifier, height: Dp = 92.dp, onClick: () -> Unit) {
    Box(
        modifier.height(height).clip(RoundedCornerShape(24.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(emoji, fontSize = 32.sp)
            Text(label, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

/** Salva una voce al volo mostrando "Salvato · Annulla". */
@Composable
fun rememberQuick(vm: AppVM, baby: Baby, snack: SnackbarHostState): (Map<String, Any?>) -> Unit {
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.saved)
    val undoText = stringResource(R.string.undo)
    return { data ->
        vm.saveEntry(baby.id, null, data) { id ->
            scope.launch {
                snack.currentSnackbarData?.dismiss()
                val r = snack.showSnackbar(savedText, actionLabel = undoText, duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) vm.deleteEntry(baby.id, id)
            }
        }
    }
}

val ChipShape = RoundedCornerShape(13.dp)

@Composable
fun chipColors() = androidx.compose.material3.FilterChipDefaults.filterChipColors(
    containerColor = pal.bg,
    labelColor = pal.ink,
    selectedContainerColor = pal.ink,
    selectedLabelColor = pal.card
)

@Composable
fun assistColors() = androidx.compose.material3.AssistChipDefaults.assistChipColors(
    containerColor = pal.bg, labelColor = pal.ink
)

/** Tasto rapido come nella webapp: icona a linea in quadrato colorato + etichetta. */
@Composable
fun QuickTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, accent: Color, soft: Color,
    modifier: Modifier = Modifier, sub: String? = null, onClick: () -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(pal.card).clickable(onClick = onClick)
            .padding(top = 14.dp, bottom = 12.dp, start = 4.dp, end = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(soft), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, color = pal.ink)
        if (sub != null) Text(sub, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
fun StatChip(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(pal.card).padding(top = 12.dp, bottom = 10.dp, start = 2.dp, end = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1, color = pal.ink)
        Text(label, color = pal.muted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Pulsante pieno come .btn della webapp. */
@Composable
fun PBtn(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(18.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 17.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1) }
}
