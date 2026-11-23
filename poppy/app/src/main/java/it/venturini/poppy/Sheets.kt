package it.venturini.poppy

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun TimeAdjuster(label: String, value: Long, onChange: (Long) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var hh by remember(tick) { mutableStateOf(java.util.Calendar.getInstance().apply { timeInMillis = value }.get(java.util.Calendar.HOUR_OF_DAY).toString().padStart(2, '0')) }
    var mm by remember(tick) { mutableStateOf(java.util.Calendar.getInstance().apply { timeInMillis = value }.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')) }
    fun apply(h: Int, m: Int) {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = value }
        c.set(java.util.Calendar.HOUR_OF_DAY, h); c.set(java.util.Calendar.MINUTE, m); c.set(java.util.Calendar.SECOND, 0)
        var t = c.timeInMillis
        if (t > nowMs() + 60_000L) t -= 86_400_000L
        onChange(t)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(dateTimeStr(value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { onChange(nowMs()); tick++ }, label = { Text(stringResource(R.string.now_label)) })
            AssistChip(onClick = { onChange(nowMs() - 5 * 60_000L); tick++ }, label = { Text(stringResource(R.string.ago_5)) })
            AssistChip(onClick = { onChange(nowMs() - 15 * 60_000L); tick++ }, label = { Text(stringResource(R.string.ago_15)) })
            AssistChip(onClick = { onChange(nowMs() - 30 * 60_000L); tick++ }, label = { Text(stringResource(R.string.ago_30)) })
            AssistChip(onClick = { onChange(nowMs() - 60 * 60_000L); tick++ }, label = { Text(stringResource(R.string.ago_60)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = hh, onValueChange = { v ->
                    val f = v.filter { it.isDigit() }.take(2); hh = f
                    val h = f.toIntOrNull(); val m = mm.toIntOrNull()
                    if (h != null && m != null && h in 0..23 && m in 0..59) apply(h, m)
                },
                label = { Text(stringResource(R.string.time_h)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
            )
            Text(":", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = mm, onValueChange = { v ->
                    val f = v.filter { it.isDigit() }.take(2); mm = f
                    val h = hh.toIntOrNull(); val m = f.toIntOrNull()
                    if (h != null && m != null && h in 0..23 && m in 0..59) apply(h, m)
                },
                label = { Text(stringResource(R.string.time_m)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Apre la scheda di inserimento/modifica e offre "Annulla" dopo il salvataggio. */
@Composable
fun SheetHost(vm: AppVM, baby: Baby, snack: SnackbarHostState, req: SheetReq?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.saved)
    val undoText = stringResource(R.string.undo)
    val d = remember(vm.entries) { derive(vm.entries) }
    if (req != null) {
        EntrySheet(
            req = req, vm = vm, babyId = baby.id, lastBottle = d.lastBottle, lastPump = d.lastPump,
            onDismiss = onClose,
            onSaved = { id ->
                onClose()
                scope.launch {
                    val r = snack.showSnackbar(savedText, actionLabel = undoText, duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed && req.entry == null) vm.deleteEntry(baby.id, id)
                }
            }
        )
    }
}

@Composable
private fun SideChips(side: String, both: Boolean, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = side == "sx", onClick = { onPick("sx") }, label = { Text(stringResource(R.string.side_left)) })
        FilterChip(selected = side == "dx", onClick = { onPick("dx") }, label = { Text(stringResource(R.string.side_right)) })
        if (both) FilterChip(selected = side == "both", onClick = { onPick("both") }, label = { Text(stringResource(R.string.side_both)) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(
    req: SheetReq,
    vm: AppVM,
    babyId: String,
    lastBottle: Long?,
    lastPump: Long?,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val e = req.entry
    val kind = req.kind
    val oz = vm.oz
    var t by remember { mutableLongStateOf(e?.t ?: nowMs()) }
    var end by remember {
        mutableLongStateOf(if (e != null && e.end > 0) e.end else (e?.t ?: nowMs()) + 3_600_000L)
    }
    var amount by remember { mutableStateOf(if (e != null && e.ml > 0) fmtNum(mlToUnit(e.ml, oz)) else "") }
    var milk by remember { mutableStateOf(e?.milk ?: "materno") }
    var side by remember { mutableStateOf(e?.side ?: (if (kind == "tiralatte") "both" else "sx")) }
    var mins by remember { mutableStateOf(if (e != null && e.dur > 0) ((e.dur / 60).coerceAtLeast(1)).toString() else "") }
    var note by remember { mutableStateOf(e?.note ?: "") }
    var dKind by remember { mutableStateOf(if (kind == "pannolino") (e?.kind ?: "pipi") else "") }
    var name by remember { mutableStateOf(e?.name ?: "") }
    var dose by remember { mutableStateOf(e?.dose ?: "") }
    var isNew by remember { mutableStateOf(e?.kind == "nuovo") }
    var kg by remember { mutableStateOf(if (e != null && e.wG > 0) fmtNum(e.wG / 1000.0) else "") }
    var cm by remember { mutableStateOf(if (e != null && e.lMm > 0) fmtNum(e.lMm / 10.0) else "") }
    var hcm by remember { mutableStateOf(if (e != null && e.hMm > 0) fmtNum(e.hMm / 10.0) else "") }
    var temp by remember { mutableStateOf(if (e != null && e.temp > 0) fmtNum(e.temp) else "") }
    var bad by remember { mutableStateOf(false) }

    val title = when (kind) {
        "biberon" -> stringResource(R.string.type_bottle)
        "tiralatte" -> stringResource(R.string.type_pump)
        "seno" -> stringResource(R.string.type_breast)
        "pannolino" -> stringResource(R.string.type_diaper)
        "bagno" -> stringResource(R.string.type_bath)
        "farmaco" -> stringResource(R.string.type_med)
        "solido" -> stringResource(R.string.type_solid)
        "crescita" -> stringResource(R.string.type_growth)
        "febbre" -> stringResource(R.string.type_fever)
        else -> stringResource(R.string.type_sleep)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            if (e == null && kind == "biberon" && lastBottle != null) {
                Text(
                    stringResource(R.string.since_last_bottle, hDur(t - lastBottle)),
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold
                )
            }
            if (e == null && kind == "tiralatte" && lastPump != null) {
                Text(
                    stringResource(R.string.since_last_pump, hDur(t - lastPump)),
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold
                )
            }

            TimeAdjuster(
                if (kind == "sonno") stringResource(R.string.sleep_from) else stringResource(R.string.sheet_time),
                t
            ) { t = it }

            if (kind == "sonno") TimeAdjuster(stringResource(R.string.sleep_to), end) { end = it }

            // ----- biberon / tiralatte -----
            if (kind == "biberon" || kind == "tiralatte") {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.take(6); bad = false },
                    label = { Text(stringResource(R.string.amount, unitLabel(oz))) },
                    singleLine = true, isError = bad,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val quick = if (oz) listOf(2.0, 3.0, 4.0, 5.0, 6.0) else listOf(30.0, 60.0, 90.0, 120.0, 150.0)
                    for (q in quick) {
                        AssistChip(onClick = { amount = fmtNum(q); bad = false }, label = { Text(fmtNum(q)) })
                    }
                }
            }
            if (kind == "biberon") {
                Text(stringResource(R.string.milk_type), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = milk == "materno", onClick = { milk = "materno" }, label = { Text(stringResource(R.string.milk_breast)) })
                    FilterChip(selected = milk == "formula", onClick = { milk = "formula" }, label = { Text(stringResource(R.string.milk_formula)) })
                }
            }
            if (kind == "tiralatte") SideChips(side, true) { side = it }

            // ----- seno -----
            if (kind == "seno") {
                SideChips(side, false) { side = it }
                OutlinedTextField(
                    value = mins, onValueChange = { mins = it.filter { c -> c.isDigit() }.take(3); bad = false },
                    label = { Text(stringResource(R.string.duration_min)) },
                    singleLine = true, isError = bad,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ----- pannolino -----
            if (kind == "pannolino") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = dKind == "pipi", onClick = { dKind = "pipi" }, label = { Text(stringResource(R.string.diaper_pee)) })
                    FilterChip(selected = dKind == "cacca", onClick = { dKind = "cacca" }, label = { Text(stringResource(R.string.diaper_poop)) })
                    FilterChip(selected = dKind == "entrambi", onClick = { dKind = "entrambi" }, label = { Text(stringResource(R.string.diaper_both)) })
                }
            }

            // ----- farmaco -----
            if (kind == "farmaco") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = name == VITD, onClick = { name = VITD; bad = false }, label = { Text(stringResource(R.string.vitd)) })
                }
                OutlinedTextField(
                    value = if (name == VITD) stringResource(R.string.vitd) else name,
                    onValueChange = { name = it.take(40); bad = false },
                    label = { Text(stringResource(R.string.med_name)) },
                    singleLine = true, isError = bad, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dose, onValueChange = { dose = it.take(40) },
                    label = { Text(stringResource(R.string.med_dose)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.med_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ----- svezzamento -----
            if (kind == "solido") {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60); bad = false },
                    label = { Text(stringResource(R.string.solid_food)) },
                    singleLine = true, isError = bad, modifier = Modifier.fillMaxWidth()
                )
                FilterChip(selected = isNew, onClick = { isNew = !isNew }, label = { Text(stringResource(R.string.solid_new)) })
            }

            // ----- crescita -----
            if (kind == "crescita") {
                OutlinedTextField(
                    value = kg, onValueChange = { kg = it.take(6); bad = false },
                    label = { Text(stringResource(R.string.growth_weight)) },
                    singleLine = true, isError = bad,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cm, onValueChange = { cm = it.take(6); bad = false },
                    label = { Text(stringResource(R.string.growth_length)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hcm, onValueChange = { hcm = it.take(6); bad = false },
                    label = { Text(stringResource(R.string.growth_head)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ----- febbre -----
            if (kind == "febbre") {
                OutlinedTextField(
                    value = temp, onValueChange = { temp = it.take(5); bad = false },
                    label = { Text(stringResource(R.string.fever_temp)) },
                    singleLine = true, isError = bad,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.fever_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (kind != "sonno" && kind != "crescita") {
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(200) },
                    label = { Text(stringResource(R.string.note)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (bad) Text(stringResource(R.string.invalid_value), color = MaterialTheme.colorScheme.error)

            Button(
                onClick = {
                    val data = mutableMapOf<String, Any?>("type" to kind, "t" to t)
                    var ok = true
                    fun dec(s: String): Double? = s.replace(',', '.').toDoubleOrNull()
                    when (kind) {
                        "biberon", "tiralatte" -> {
                            val v = dec(amount)
                            val ml = if (v != null) unitToMl(v, oz) else 0
                            if (ml < 1 || ml > 2000) ok = false
                            data["ml"] = ml
                            if (kind == "biberon") data["milk"] = milk else data["side"] = side
                        }
                        "seno" -> {
                            val m = mins.toIntOrNull() ?: 0
                            if (m < 1) ok = false
                            data["dur"] = m * 60
                            data["side"] = side
                            data["session"] = e?.session ?: e?.id
                        }
                        "pannolino" -> data["kind"] = dKind
                        "farmaco" -> {
                            if (name.isBlank()) ok = false
                            data["name"] = name.trim()
                            if (dose.isNotBlank()) data["dose"] = dose.trim()
                        }
                        "solido" -> {
                            if (name.isBlank()) ok = false
                            data["name"] = name.trim()
                            if (isNew) data["kind"] = "nuovo"
                        }
                        "crescita" -> {
                            val w = dec(kg)
                            val l = dec(cm)
                            val h = dec(hcm)
                            if (w == null && l == null && h == null) ok = false
                            if (w != null) { if (w < 0.5 || w > 30) ok = false else data["wG"] = Math.round(w * 1000).toInt() }
                            if (l != null) { if (l < 20 || l > 130) ok = false else data["lMm"] = Math.round(l * 10).toInt() }
                            if (h != null) { if (h < 20 || h > 60) ok = false else data["hMm"] = Math.round(h * 10).toInt() }
                        }
                        "febbre" -> {
                            val v = dec(temp)
                            if (v == null || v < 34 || v > 43) ok = false else data["temp"] = v
                        }
                        "bagno" -> {}
                        else -> {
                            if (end <= t) ok = false
                            data["end"] = end
                        }
                    }
                    if (note.isNotBlank() && kind != "sonno" && kind != "crescita") data["note"] = note.trim()
                    if (ok) vm.saveEntry(babyId, e?.id, data, onSaved) else bad = true
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(stringResource(R.string.save)) }

            if (e != null) {
                TextButton(
                    onClick = { vm.deleteEntry(babyId, e.id); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
