package com.bountyradar.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.components.Panel
import com.bountyradar.app.ui.components.RadarChip
import com.bountyradar.app.ui.components.RadarLogo
import com.bountyradar.app.ui.components.SecondaryButton
import com.bountyradar.app.ui.components.SectionLabel
import com.bountyradar.app.ui.theme.Radar
import com.bountyradar.app.ui.theme.platformName

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: RadarViewModel, onOpenAccounts: () -> Unit) {
    val auth by vm.authState.collectAsStateWithLifecycle()
    val total by vm.totalCount.collectAsStateWithLifecycle()
    val platforms by vm.platformKeys.collectAsStateWithLifecycle()
    val muted by vm.mutedPlatforms.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = Radar.ScreenPadding, end = Radar.ScreenPadding, top = 14.dp, bottom = 24.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(18.dp))

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadarLogo(44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(auth.email ?: "Signed in", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        "Tracking %,d programs".format(total),
                        style = MaterialTheme.typography.bodySmall, color = Radar.Muted,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // Whole row is the touch target.
        Panel(Modifier.clip(Radar.CardShape).clickable(role = Role.Button, onClick = onOpenAccounts)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Key, null, tint = Radar.Accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("My accounts", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Private invites, reports and earnings from your own platform accounts.",
                        style = MaterialTheme.typography.bodySmall, color = Radar.Muted,
                    )
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Radar.Faint)
            }
        }

        Spacer(Modifier.height(26.dp))
        SectionLabel("Alerts")
        Spacer(Modifier.height(4.dp))
        Text(
            "You get a push when a program launches or changes. Tap a platform to turn its alerts off.",
            style = MaterialTheme.typography.bodySmall, color = Radar.Muted,
        )
        Spacer(Modifier.height(12.dp))
        if (platforms.isEmpty()) {
            Text("Platforms appear here once programs load.", style = MaterialTheme.typography.bodySmall, color = Radar.Faint)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                platforms.forEach { p -> RadarChip(platformName(p), selected = p !in muted) { vm.toggleMuted(p) } }
            }
        }

        Spacer(Modifier.height(32.dp))
        SecondaryButton("Sign out", onClick = { vm.signOut() }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Bounty Radar 0.4", style = MaterialTheme.typography.labelSmall, color = Radar.Faint)
        }
    }
}
