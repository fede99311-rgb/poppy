package it.venturini.poppy

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun SettingsScreen(vm: AppVM, billing: Billing, baby: Baby, d: Derived, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val privacyUrl = stringResource(R.string.privacy_url)
    var name by remember(baby.id, baby.name) { mutableStateOf(baby.name) }
    var newBaby by remember { mutableStateOf("") }
    var confirmBaby by remember { mutableStateOf(false) }
    var confirmAccount by remember { mutableStateOf(false) }
    var relogin by remember { mutableStateOf(false) }
    var pickBirth by remember { mutableStateOf(false) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askNotif() {
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) {
            try {
                val txt = ctx.contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() } ?: ""
                vm.importJson(baby.id, txt) { n -> vm.message = if (n < 0) R.string.restore_bad else R.string.restore_ok }
            } catch (e: Exception) {
                vm.message = R.string.error_generic
            }
        }
    }
    fun share(f: java.io.File, mime: String) {
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, u)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, null))
    }
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← " + stringResource(R.string.back)) }
        }
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Section(stringResource(R.string.baby_section)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) },
                label = { Text(stringResource(R.string.baby_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            FilledTonalButton(
                onClick = { vm.renameBaby(baby.id, name) },
                enabled = name.isNotBlank() && name.trim() != baby.name
            ) { Text(stringResource(R.string.save)) }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = newBaby, onValueChange = { newBaby = it.take(40) },
                label = { Text(stringResource(R.string.add_baby)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = { vm.createBaby(newBaby); newBaby = "" },
                enabled = newBaby.isNotBlank()
            ) { Text(stringResource(R.string.onb_create)) }
        }

        Section(stringResource(R.string.family)) {
            Text(stringResource(R.string.members, baby.members.size))
            Text(
                stringResource(R.string.invite_code_label, baby.inviteCode),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, ctx.getString(R.string.share_msg, baby.name, baby.inviteCode))
                    }
                    ctx.startActivity(Intent.createChooser(send, null))
                }) { Text(stringResource(R.string.share_code)) }
                OutlinedButton(onClick = { vm.regenerateCode(baby) }) { Text(stringResource(R.string.new_code)) }
            }
        }

        Section(stringResource(R.string.birth_section)) {
            Text(baby.birth?.let { dayStr(it) } ?: stringResource(R.string.birth_needed), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { pickBirth = true }) { Text(stringResource(R.string.set_birth)) }
        }

        Section(stringResource(R.string.appearance)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vm.themeMode == 0, onClick = { vm.setTheme(0) }, label = { Text(stringResource(R.string.theme_system)) })
                FilterChip(selected = vm.themeMode == 1, onClick = { vm.setTheme(1) }, label = { Text(stringResource(R.string.theme_light)) })
                FilterChip(selected = vm.themeMode == 2, onClick = { vm.setTheme(2) }, label = { Text(stringResource(R.string.theme_dark)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vm.hourMode == 0, onClick = { vm.setHours(0) }, label = { Text(stringResource(R.string.hours_auto)) })
                FilterChip(selected = vm.hourMode == 1, onClick = { vm.setHours(1) }, label = { Text("24 h") })
                FilterChip(selected = vm.hourMode == 2, onClick = { vm.setHours(2) }, label = { Text("12 h") })
            }
        }

        Section(stringResource(R.string.nursing_section)) {
            Text(stringResource(R.string.min_dur_label), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (v in listOf(3, 30, 60)) FilterChip(selected = vm.minDur == v, onClick = { vm.setMinDur(v) }, label = { Text("$v s") })
            }
            Text(stringResource(R.string.fixed_side_label), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vm.fixedSide == "auto", onClick = { vm.setFixedSide("auto") }, label = { Text(stringResource(R.string.side_auto)) })
                FilterChip(selected = vm.fixedSide == "sx", onClick = { vm.setFixedSide("sx") }, label = { Text(stringResource(R.string.side_left)) })
                FilterChip(selected = vm.fixedSide == "dx", onClick = { vm.setFixedSide("dx") }, label = { Text(stringResource(R.string.side_right)) })
            }
        }

        Section(stringResource(R.string.reminders)) {
            Text(stringResource(R.string.rem_feed_label), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (hh in listOf(0, 2, 3, 4)) {
                    FilterChip(
                        selected = vm.remFeedH == hh,
                        onClick = { vm.setRemFeed(hh); if (hh > 0) askNotif() },
                        label = { Text(if (hh == 0) stringResource(R.string.off) else "$hh h") }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = vm.remVitD,
                    onClick = { vm.setRemVitD(!vm.remVitD, vm.vitdHour); if (!vm.remVitD) askNotif() },
                    label = { Text(stringResource(R.string.rem_vitd_label)) }
                )
                if (vm.remVitD) for (hh in listOf(8, 9, 12, 20)) {
                    FilterChip(selected = vm.vitdHour == hh, onClick = { vm.setRemVitD(true, hh) }, label = { Text("$hh:00") })
                }
            }
            Text(stringResource(R.string.rem_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Section(stringResource(R.string.security)) {
            val canBio = remember {
                BiometricManager.from(ctx).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                ) == BiometricManager.BIOMETRIC_SUCCESS
            }
            FilterChip(
                selected = vm.lockEnabled,
                onClick = { if (canBio) vm.setLock(!vm.lockEnabled) else vm.message = R.string.lock_unavailable },
                label = { Text(stringResource(R.string.lock_label)) }
            )
        }

        Section(stringResource(R.string.data_section)) {
            OutlinedButton(onClick = {
                scope.launch {
                    try { share(buildReport(ctx, baby, vm.entries, vm.health, 30, vm.oz), "application/pdf") }
                    catch (e: Exception) { vm.message = R.string.error_generic }
                }
            }) { Text(stringResource(R.string.report_pdf)) }
            OutlinedButton(onClick = {
                scope.launch {
                    try { share(vm.exportJson(ctx.cacheDir, baby), "application/json") }
                    catch (e: Exception) { vm.message = R.string.error_generic }
                }
            }) { Text(stringResource(R.string.backup_json)) }
            OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                Text(stringResource(R.string.restore_json))
            }
        }

        Section(stringResource(R.string.units)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !vm.oz, onClick = { vm.setOz(false) }, label = { Text("ml") })
                FilterChip(selected = vm.oz, onClick = { vm.setOz(true) }, label = { Text("oz") })
            }
            OutlinedButton(onClick = {
                scope.launch {
                    try {
                        val f = vm.exportCsv(ctx.cacheDir, baby)
                        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, u)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(send, null))
                    } catch (e: Exception) {
                        vm.message = R.string.error_generic
                    }
                }
            }) { Text(stringResource(R.string.export_csv)) }
        }

        Section(stringResource(R.string.support)) {
            Text(stringResource(R.string.support_text))
            if (billing.tips.isEmpty()) {
                Text(stringResource(R.string.tip_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in billing.tips) {
                        FilledTonalButton(onClick = { billing.buy(p) }) {
                            Text("☕ " + (p.oneTimePurchaseOfferDetails?.formattedPrice ?: ""))
                        }
                    }
                }
            }
        }

        Section(stringResource(R.string.account)) {
            Text(vm.user?.email ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { vm.signOut() }) { Text(stringResource(R.string.sign_out)) }
            TextButton(onClick = { uri.openUri(privacyUrl) }) { Text(stringResource(R.string.privacy)) }
            TextButton(onClick = { confirmBaby = true }) {
                Text(stringResource(R.string.delete_baby), color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = { confirmAccount = true }) {
                Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error)
            }
            if (relogin) Text(stringResource(R.string.relogin), color = MaterialTheme.colorScheme.error)
        }

        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.version, version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (confirmBaby) {
        AlertDialog(
            onDismissRequest = { confirmBaby = false },
            title = { Text(stringResource(R.string.delete_baby)) },
            text = { Text(stringResource(R.string.delete_baby_confirm, baby.name)) },
            confirmButton = {
                TextButton(onClick = { confirmBaby = false; vm.deleteBaby(baby) { onBack() } }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmBaby = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (confirmAccount) {
        AlertDialog(
            onDismissRequest = { confirmAccount = false },
            title = { Text(stringResource(R.string.delete_account)) },
            text = { Text(stringResource(R.string.delete_account_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmAccount = false
                    vm.deleteAccount { r ->
                        if (r == "relogin") relogin = true
                        else if (r != null) vm.message = R.string.error_generic
                    }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmAccount = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (pickBirth) BirthPicker(baby.birth, { vm.setBirth(baby.id, it); pickBirth = false }, { pickBirth = false })
}
