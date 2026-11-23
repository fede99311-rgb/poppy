package it.venturini.poppy

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("← " + stringResource(R.string.back)) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/** Sezione "Altro": tutto ciò che non serve ogni minuto in homepage. */
@Composable
fun MoreScreen(vm: AppVM, baby: Baby, billing: Billing, snack: SnackbarHostState, onSheet: (SheetReq) -> Unit) {
    var sub by remember { mutableStateOf("menu") }
    val quick = rememberQuick(vm, baby, snack)
    if (sub != "menu") BackHandler { sub = "menu" }
    when (sub) {
        "health" -> Column(Modifier.fillMaxSize()) {
            SubHeader(stringResource(R.string.m_health)) { sub = "menu" }
            HealthScreen(vm, onSheet)
        }
        "growth" -> Column(Modifier.fillMaxSize()) {
            SubHeader(stringResource(R.string.m_growth)) { sub = "menu" }
            GrowthScreen(vm, baby, onSheet)
        }
        "support" -> Column(Modifier.fillMaxSize()) {
            SubHeader(stringResource(R.string.m_support)) { sub = "menu" }
            SupportScreen(vm, billing)
        }
        "ideas" -> Column(Modifier.fillMaxSize()) {
            SubHeader(stringResource(R.string.m_ideas)) { sub = "menu" }
            IdeasScreen(vm)
        }
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigTile("🛁", stringResource(R.string.q_bath), CBlue, Modifier.weight(1f)) { quick(mapOf("type" to "bagno", "t" to nowMs())) }
                BigTile("☀️", stringResource(R.string.vitd), CYellow, Modifier.weight(1f)) {
                    quick(mapOf("type" to "farmaco", "t" to nowMs(), "name" to VITD))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigTile("🩺", stringResource(R.string.m_health), CMint, Modifier.weight(1f)) { sub = "health" }
                BigTile("🌱", stringResource(R.string.m_growth), COrange, Modifier.weight(1f)) { sub = "growth" }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigTile("💛", stringResource(R.string.m_support), CRose, Modifier.weight(1f)) { sub = "support" }
                BigTile("💡", stringResource(R.string.m_ideas), CPurple, Modifier.weight(1f)) { sub = "ideas" }
            }
        }
    }
}

@Composable
fun SupportScreen(vm: AppVM, billing: Billing) {
    val ctx = LocalContext.current
    var loading by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.support_text))
        Button(
            onClick = {
                val act = ctx.findActivity()
                if (act == null) { vm.message = R.string.ad_unavailable } else {
                    loading = true
                    Ads.show(act,
                        onReward = { loading = false; vm.message = R.string.ad_thanks },
                        onFail = { loading = false; vm.message = R.string.ad_unavailable })
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8456B), contentColor = Color.White)
        ) { Text("▶️  " + stringResource(if (loading) R.string.ad_loading else R.string.support_watch_ad), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Text(stringResource(R.string.support_or_tip), style = MaterialTheme.typography.titleSmall)
        if (billing.tips.isEmpty()) {
            Text(stringResource(R.string.tip_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in billing.tips) {
                    FilledTonalButton(onClick = { billing.buy(p) }, modifier = Modifier.height(52.dp)) {
                        Text("☕ " + (p.oneTimePurchaseOfferDetails?.formattedPrice ?: ""))
                    }
                }
            }
        }
        Text(stringResource(R.string.ads_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun IdeasScreen(vm: AppVM) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.listenIdeas() }
    var text by remember { mutableStateOf("") }
    val me = vm.myUid()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.ideas_intro))
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(200) },
            label = { Text(stringResource(R.string.idea_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { vm.submitIdea(text) { text = "" } },
            enabled = text.trim().length >= 5,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text(stringResource(R.string.idea_send)) }

        if (vm.ideas.isEmpty()) Text(stringResource(R.string.idea_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (i in vm.ideas) {
            val voted = me in i.voters
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = if (i.status == "done") CMint else if (i.status == "planned") CYellow else MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val dark = i.status == "done" || i.status == "planned"
                    Text(i.text, color = if (dark) Ink else MaterialTheme.colorScheme.onSurface)
                    if (i.status == "planned") Text("🛠 " + stringResource(R.string.idea_planned), color = Ink, fontWeight = FontWeight.Bold)
                    if (i.status == "done") Text("✅ " + stringResource(R.string.idea_done), color = Ink, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.idea_goal, i.votes),
                            color = if (dark) Ink else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold
                        )
                        Row {
                            if (i.author == me) TextButton(onClick = { vm.deleteIdea(i) }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                            else TextButton(onClick = {
                                val mail = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:")
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf(ctx.getString(R.string.contact_email)))
                                    putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.idea_report_subject))
                                    putExtra(Intent.EXTRA_TEXT, i.id + "\n" + i.text)
                                }
                                try { ctx.startActivity(mail) } catch (e: Exception) { }
                            }) { Text(stringResource(R.string.idea_report), color = if (dark) Ink else MaterialTheme.colorScheme.onSurfaceVariant) }
                            OutlinedButton(onClick = { vm.voteIdea(i) }, enabled = !voted) {
                                Text(if (voted) "👍 " + stringResource(R.string.idea_voted) else "👍 " + stringResource(R.string.idea_vote))
                            }
                        }
                    }
                }
            }
        }
    }
}
