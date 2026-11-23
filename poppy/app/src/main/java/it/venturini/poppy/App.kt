package it.venturini.poppy

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun App(vm: AppVM, billing: Billing, activity: Activity, onLock: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            vm.user == null -> LoginScreen(vm, activity)
            vm.lockEnabled && vm.locked -> LockScreen(onLock)
            !vm.babiesLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            vm.babies.isEmpty() -> OnboardingScreen(vm)
            else -> MainHost(vm, billing)
        }
    }
}

@Composable
fun LockScreen(onLock: () -> Unit) {
    LaunchedEffect(Unit) { onLock() }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Logo(96)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onLock) { Text(stringResource(R.string.unlock)) }
    }
}

@Composable
fun Logo(size: Int = 96) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size((size * 0.95f).dp))
    }
}

@Composable
fun LoginScreen(vm: AppVM, activity: Activity) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val privacyUrl = stringResource(R.string.privacy_url)
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Logo(112)
        Spacer(Modifier.height(20.dp))
        Text("Poppy", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.sign_in_title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.sign_in_sub),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        if (error) {
            Text(stringResource(R.string.sign_in_error), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = {
                busy = true; error = false
                scope.launch {
                    try {
                        vm.signIn(activity, activity.getString(R.string.default_web_client_id))
                    } catch (e: Exception) {
                        error = true
                    }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(stringResource(R.string.sign_in_google)) }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { uri.openUri(privacyUrl) }) { Text(stringResource(R.string.privacy)) }
    }
}

@Composable
fun OnboardingScreen(vm: AppVM) {
    var name by remember { mutableStateOf("") }
    var birth by remember { mutableStateOf<Long?>(null) }
    var pick by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.onb_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(40) },
            label = { Text(stringResource(R.string.baby_name)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { pick = true }, modifier = Modifier.fillMaxWidth()) {
            Text(birth?.let { dayStr(it) } ?: stringResource(R.string.birth_optional))
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.createBaby(name, birth) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text(stringResource(R.string.onb_create)) }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.onb_or), modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.onb_join_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = code, onValueChange = { code = it.uppercase().take(8); bad = false },
            label = { Text(stringResource(R.string.onb_code)) },
            singleLine = true, isError = bad, modifier = Modifier.fillMaxWidth(),
            supportingText = { if (bad) Text(stringResource(R.string.join_error)) }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { vm.joinWithCode(code) { ok -> if (!ok) bad = true } },
            enabled = code.length >= 6,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text(stringResource(R.string.onb_join)) }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        TextButton(onClick = { vm.signOut() }) { Text(stringResource(R.string.sign_out)) }
    }
    if (pick) BirthPicker(birth, { birth = it; pick = false }, { pick = false })
}

@Composable
fun MainHost(vm: AppVM, billing: Billing) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf("home") }
    var settings by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<SheetReq?>(null) }
    val snack = remember { SnackbarHostState() }
    val msg = vm.message
    val msgText = if (msg != null) stringResource(msg) else null
    LaunchedEffect(msg) {
        if (msgText != null) {
            snack.showSnackbar(msgText)
            vm.message = null
        }
    }
    val baby = vm.babies.firstOrNull { it.id == vm.selectedId } ?: vm.babies.first()
    val d = remember(vm.entries) { derive(vm.entries) }

    // promemoria, widget e preferenze per il receiver
    LaunchedEffect(baby.id, baby.name, d.lastFeed, d.nextSide, vm.remFeedH, vm.remVitD, vm.vitdHour) {
        vm.saveBabyPrefs(baby.id, baby.name, d.lastFeed, d.nextSide)
        Reminders.scheduleFeed(ctx, d.lastFeed, vm.remFeedH)
        Reminders.scheduleVitD(ctx, vm.remVitD, vm.vitdHour)
        PoppyWidget.refresh(ctx)
    }
    // avvio da widget
    val ps = vm.pendingStart
    LaunchedEffect(ps) { if (ps != null) { tab = "home"; settings = false } }

    // nuova sezione sbloccata
    val months = ageMonths(baby.birth, nowMs())
    val unlockedText = stringResource(R.string.unlocked_solids)
    LaunchedEffect(months, baby.id) {
        if (months >= SOLIDS_FROM_MONTHS && !vm.seen("solids_" + baby.id)) {
            vm.markSeen("solids_" + baby.id)
            snack.showSnackbar(unlockedText)
        }
    }

    if (settings) BackHandler { settings = false }
    else if (tab != "home") BackHandler { tab = "home" }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            if (!settings) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        TextButton(onClick = { if (vm.babies.size > 1) menu = true }) {
                            Column {
                                Text(baby.name + if (vm.babies.size > 1) " ▾" else "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                if (baby.birth != null) Text(ageText(baby.birth, nowMs()), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            for (b in vm.babies) DropdownMenuItem(text = { Text(b.name) }, onClick = { menu = false; vm.selectBaby(b.id) })
                        }
                    }
                    TextButton(onClick = { settings = true }) { Text("⚙ " + stringResource(R.string.settings)) }
                }
            }
        },
        bottomBar = {
            if (!settings) NavigationBar {
                val items = listOf(
                    Triple("home", "🏠", R.string.tab_today), Triple("stats", "📊", R.string.tab_charts),
                    Triple("more", "✨", R.string.tab_more)
                )
                for ((k, ic, lbl) in items) {
                    NavigationBarItem(
                        selected = tab == k, onClick = { tab = k },
                        icon = { Text(ic, fontSize = 22.sp) }, label = { Text(stringResource(lbl)) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (settings) SettingsScreen(vm, billing, baby, d) { settings = false }
            else when (tab) {
                "stats" -> StatsScreen(vm, d)
                "more" -> MoreScreen(vm, baby, billing, snack) { sheet = it }
                else -> HomeScreen(vm, baby, d, snack)
            }
        }
    }
    SheetHost(vm, baby, snack, sheet) { sheet = null }
}
