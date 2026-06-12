package com.bountyradar.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.Recency
import com.bountyradar.app.ui.RewardFilter
import com.bountyradar.app.ui.SortBy
import com.bountyradar.app.ui.theme.platformColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSortSheet(vm: RadarViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filters by vm.filters.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val platforms by vm.platformKeys.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters & Sort", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.fillMaxWidth().weight(1f))
                if (filters.isActive) {
                    TextButton(onClick = { vm.clearFilters() }) { Text("Clear all") }
                }
            }

            Section("Sort by")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortBy.entries.forEach { s ->
                    Chip(s.label, sort == s) { vm.setSort(s) }
                }
            }

            Section("Reward")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RewardFilter.entries.forEach { r ->
                    Chip(r.label, filters.reward == r) { vm.setReward(r) }
                }
            }

            Section("When posted")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Recency.entries.forEach { r ->
                    Chip(r.label, filters.recency == r) { vm.setRecency(r) }
                }
            }

            Section("Type")
            Chip("Web3 / smart contracts only", filters.web3Only) { vm.setWeb3Only(!filters.web3Only) }

            Section("Platforms (${platforms.size})")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                platforms.forEach { p ->
                    Chip(
                        label = p.replaceFirstChar { it.uppercase() },
                        selected = p in filters.platforms,
                        accent = platformColor(p),
                    ) { vm.togglePlatform(p) }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Show results", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(18.dp))
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.height(18.dp)) }
        } else null,
        colors = if (accent != null) {
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = accent.copy(alpha = 0.22f),
                selectedLabelColor = accent,
                selectedLeadingIconColor = accent,
            )
        } else FilterChipDefaults.filterChipColors(),
    )
}
