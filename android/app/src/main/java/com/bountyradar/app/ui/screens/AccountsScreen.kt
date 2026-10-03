package com.bountyradar.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bountyradar.app.data.AccountProgram
import com.bountyradar.app.data.AccountReport
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.components.Pill
import com.bountyradar.app.ui.components.PlatformAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(vm: RadarViewModel, onBack: () -> Unit) {
    val h1 by vm.hackerOne.collectAsStateWithLifecycle()
    val inti by vm.intigriti.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val open: (String) -> Unit = { url ->
        if (url.isNotBlank()) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    LaunchedEffect(Unit) { vm.loadAccounts() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("My accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Add your own API token to see private invites, your reports and earnings. " +
                        "Tokens are encrypted on this phone and only ever sent to the platform that issued them.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }

            // ---------------- HackerOne ----------------
            item {
                AccountCard("hackerone", "HackerOne", h1.connected, h1.label, h1.loading, h1.error,
                    onRefresh = vm::refreshHackerOne, onDisconnect = vm::disconnectHackerOne) {
                    var user by remember { mutableStateOf("") }
                    var token by remember { mutableStateOf("") }
                    Text(
                        "HackerOne → Settings → API Token. Enter your username and the token.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(user, { user = it }, label = { Text("HackerOne username") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(token, { token = it }, label = { Text("API token") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { vm.connectHackerOne(user, token) },
                        enabled = user.isNotBlank() && token.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Connect HackerOne") }
                }
            }
            h1.data?.let { d ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat(d.balance?.let { "$$it" } ?: "—", "balance", Modifier.weight(1f))
                        Stat("${d.programs.count { it.isPrivate }}", "private programs", Modifier.weight(1f))
                        Stat("${d.reports.size}", "recent reports", Modifier.weight(1f))
                    }
                }
                items(d.warnings) { w -> Text(w, color = cs.error, style = MaterialTheme.typography.bodySmall) }
                val privates = d.programs.filter { it.isPrivate }
                if (privates.isNotEmpty()) {
                    item { SectionTitle("Private invites (${privates.size})") }
                    items(privates, key = { "h1p-" + it.handle }) { p -> ProgramRow(p, open) }
                }
                if (d.reports.isNotEmpty()) {
                    item { SectionTitle("My reports") }
                    items(d.reports, key = { "h1r-" + it.url }) { r -> ReportRow(r, open) }
                }
                if (d.earnings.isNotEmpty()) {
                    item { SectionTitle("Earnings") }
                    items(d.earnings.size) { i ->
                        val e = d.earnings[i]
                        RowCard {
                            Text("$${e.amount}", fontWeight = FontWeight.Bold, color = cs.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(e.program.ifBlank { "—" }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(e.date, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }

            // ---------------- Intigriti ----------------
            item {
                AccountCard("intigriti", "Intigriti", inti.connected, inti.label, inti.loading, inti.error,
                    onRefresh = vm::refreshIntigriti, onDisconnect = vm::disconnectIntigriti) {
                    var token by remember { mutableStateOf("") }
                    Text(
                        "Intigriti → Profile → Personal access tokens. Paste a researcher API token.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(token, { token = it }, label = { Text("Personal access token") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { vm.connectIntigriti(token) },
                        enabled = token.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Connect Intigriti") }
                }
            }
            inti.data?.let { d ->
                val privates = d.programs.filter { it.isPrivate }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("${d.programs.size}", "programs visible", Modifier.weight(1f))
                        Stat("${privates.size}", "non-public", Modifier.weight(1f))
                    }
                }
                if (privates.isNotEmpty()) {
                    item { SectionTitle("Invite-only / restricted (${privates.size})") }
                    items(privates, key = { "inp-" + it.handle + it.url }) { p -> ProgramRow(p, open) }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    platform: String, title: String, connected: Boolean, label: String,
    loading: Boolean, error: String?,
    onRefresh: () -> Unit, onDisconnect: () -> Unit,
    connectForm: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = cs.surface, tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformAvatar(platform, 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (connected) "Connected · $label" else "Not connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (connected) cs.primary else cs.onSurfaceVariant,
                    )
                }
                if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            if (connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onRefresh, enabled = !loading) { Text("Refresh") }
                    TextButton(onClick = onDisconnect) { Text("Disconnect") }
                }
            } else {
                connectForm()
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceVariant.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun RowCard(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun ProgramRow(p: AccountProgram, open: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    RowCard(onClick = { open(p.url) }) {
        Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        if (p.bounty) Pill("bounty", cs.primary, filled = true) else Pill("VDP", cs.onSurfaceVariant)
    }
}

@Composable
private fun ReportRow(r: AccountReport, open: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    RowCard(onClick = { open(r.url) }) {
        Column(Modifier.weight(1f)) {
            Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(r.program, r.date).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Pill(r.state.ifBlank { "—" }, cs.secondary)
    }
}
