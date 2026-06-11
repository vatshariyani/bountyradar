package com.bountyradar.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bountyradar.app.data.Program

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramListScreen(vm: RadarViewModel) {
    val programs by vm.programs.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New programs (${programs.size})") },
                actions = {
                    IconButton(onClick = { vm.signOut() }) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign out")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                label = { Text("Search name, platform, scope, tag") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(programs, key = { it.docId }) { program ->
                    ProgramCard(program) {
                        if (program.url.isNotBlank()) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(program.url))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramCard(program: Program, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(program.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(program.platform, style = MaterialTheme.typography.labelMedium)
                if (program.bounty) Text("💰 bounty", style = MaterialTheme.typography.labelMedium)
                if (program.rewardRange.isNotBlank())
                    Text(program.rewardRange, style = MaterialTheme.typography.labelMedium)
            }
            if (program.scope.isNotEmpty()) {
                Text(
                    "${program.scope.size} scope item(s): " +
                        program.scope.take(3).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
