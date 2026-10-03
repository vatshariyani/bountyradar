package com.bountyradar.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bountyradar.app.data.AccountProgram
import com.bountyradar.app.data.AccountReport
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.components.Metric
import com.bountyradar.app.ui.components.Panel
import com.bountyradar.app.ui.components.PlatformAvatar
import com.bountyradar.app.ui.components.PrimaryButton
import com.bountyradar.app.ui.components.ScreenTopBar
import com.bountyradar.app.ui.components.SecondaryButton
import com.bountyradar.app.ui.components.SectionLabel
import com.bountyradar.app.ui.components.Tag
import com.bountyradar.app.ui.components.radarFieldColors
import com.bountyradar.app.ui.theme.GeistMono
import com.bountyradar.app.ui.theme.Radar

@Composable
fun AccountsScreen(vm: RadarViewModel, onBack: () -> Unit) {
    val h1 by vm.hackerOne.collectAsStateWithLifecycle()
    val inti by vm.intigriti.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val open: (String) -> Unit = { url ->
        if (url.isNotBlank()) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    LaunchedEffect(Unit) { vm.loadAccounts() }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar("My accounts", onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Radar.ScreenPadding, 4.dp, Radar.ScreenPadding, 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Add your own API token to see private invites, reports and earnings. " +
                        "Tokens are encrypted on this phone and only sent to the platform that issued them.",
                    style = MaterialTheme.typography.bodySmall, color = Radar.Muted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            // ---------------- HackerOne ----------------
            item {
                AccountPanel(
                    "hackerone", "HackerOne", h1.connected, h1.label, h1.loading, h1.error,
                    onRefresh = vm::refreshHackerOne, onDisconnect = vm::disconnectHackerOne,
                ) {
                    var user by remember { mutableStateOf("") }
                    var token by remember { mutableStateOf("") }
                    Text(
                        "Create a token under HackerOne Settings, API Token.",
                        style = MaterialTheme.typography.bodySmall, color = Radar.Muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    Field(user, { user = it }, "HackerOne username")
                    Spacer(Modifier.height(10.dp))
                    Field(token, { token = it }, "API token", secret = true)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        "Connect HackerOne", onClick = { vm.connectHackerOne(user, token) },
                        enabled = user.isNotBlank() && token.isNotBlank(),
                    )
                }
            }
            h1.data?.let { d ->
                val privates = d.programs.filter { it.isPrivate }
                item {
                    Panel {
                        // Balance gets the full width: money strings are long.
                        Metric(d.balance?.let { "$$it" } ?: "Not available", "Balance", accent = true)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Metric("${privates.size}", "Private invites", Modifier.weight(1f))
                            Metric("${d.reports.size}", "Recent reports", Modifier.weight(1f))
                        }
                    }
                }
                items(d.warnings) { w -> Text(w, color = Radar.Danger, style = MaterialTheme.typography.bodySmall) }
                if (privates.isNotEmpty()) {
                    item { SectionLabel("Private invites", Modifier.padding(top = 10.dp)) }
                    items(privates, key = { "h1p-" + it.handle }) { p -> ProgramRow(p, open) }
                }
                if (d.reports.isNotEmpty()) {
                    item { SectionLabel("My reports", Modifier.padding(top = 10.dp)) }
                    items(d.reports, key = { "h1r-" + it.url }) { r -> ReportRow(r, open) }
                }
                if (d.earnings.isNotEmpty()) {
                    item { SectionLabel("Earnings", Modifier.padding(top = 10.dp)) }
                    items(d.earnings.size) { i ->
                        val e = d.earnings[i]
                        ListRow {
                            Text(
                                "$${e.amount}", fontFamily = GeistMono, fontWeight = FontWeight.Medium,
                                fontSize = 15.sp, color = Radar.Accent,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                e.program.ifBlank { "Unknown program" }, Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(e.date, style = MaterialTheme.typography.labelSmall, color = Radar.Muted)
                        }
                    }
                }
            }

            // ---------------- Intigriti ----------------
            item {
                Spacer(Modifier.height(8.dp))
                AccountPanel(
                    "intigriti", "Intigriti", inti.connected, inti.label, inti.loading, inti.error,
                    onRefresh = vm::refreshIntigriti, onDisconnect = vm::disconnectIntigriti,
                ) {
                    var token by remember { mutableStateOf("") }
                    Text(
                        "Create a researcher token under Intigriti Profile, Personal access tokens.",
                        style = MaterialTheme.typography.bodySmall, color = Radar.Muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    Field(token, { token = it }, "Personal access token", secret = true)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        "Connect Intigriti", onClick = { vm.connectIntigriti(token) },
                        enabled = token.isNotBlank(),
                    )
                }
            }
            inti.data?.let { d ->
                val privates = d.programs.filter { it.isPrivate }
                item {
                    Panel {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Metric("${d.programs.size}", "Programs visible", Modifier.weight(1f))
                            Metric("${privates.size}", "Not public", Modifier.weight(1f), accent = true)
                        }
                    }
                }
                if (privates.isNotEmpty()) {
                    item { SectionLabel("Invite-only and restricted", Modifier.padding(top = 10.dp)) }
                    items(privates, key = { "inp-" + it.handle + it.url }) { p -> ProgramRow(p, open) }
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, secret: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = Radar.InnerShape, colors = radarFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AccountPanel(
    platform: String, title: String, connected: Boolean, label: String,
    loading: Boolean, error: String?,
    onRefresh: () -> Unit, onDisconnect: () -> Unit,
    connectForm: @Composable ColumnScope.() -> Unit,
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlatformAvatar(platform, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (connected) "Connected as $label" else "Not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connected) Radar.Accent else Radar.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Radar.Accent, strokeWidth = 2.dp)
        }
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = Radar.Danger, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(14.dp))
        if (connected) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Refresh", onRefresh, Modifier.weight(1f), enabled = !loading)
                SecondaryButton("Disconnect", onDisconnect, Modifier.weight(1f), color = Radar.Danger)
            }
        } else {
            connectForm()
        }
    }
}

@Composable
private fun ListRow(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Radar.InnerShape)
            .background(Radar.Surface)
            .border(1.dp, Radar.Line, Radar.InnerShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ProgramRow(p: AccountProgram, open: (String) -> Unit) {
    ListRow(onClick = { open(p.url) }) {
        Text(
            p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        if (p.bounty) Tag("Bounty") else Tag("VDP", Radar.Muted)
    }
}

@Composable
private fun ReportRow(r: AccountReport, open: (String) -> Unit) {
    ListRow(onClick = { open(r.url) }) {
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(r.program, r.date).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = Radar.Muted,
            )
        }
        Spacer(Modifier.width(10.dp))
        Tag(r.state.ifBlank { "Unknown" }.replaceFirstChar { it.uppercase() }, Radar.Muted)
    }
}
