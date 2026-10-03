package com.bountyradar.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bountyradar.app.ui.MIN_REWARD_STEPS
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.Recency
import com.bountyradar.app.ui.RewardFilter
import com.bountyradar.app.ui.ScopeType
import com.bountyradar.app.ui.SortBy
import com.bountyradar.app.ui.theme.Radar
import com.bountyradar.app.ui.theme.platformName

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSortSheet(vm: RadarViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filters by vm.filters.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val platforms by vm.platformKeys.collectAsStateWithLifecycle()
    val results by vm.programs.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Radar.Surface,
        contentColor = Radar.Text,
        scrimColor = MaterialTheme.colorScheme.scrim,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = Radar.ScreenPadding, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Filter and sort", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                if (filters.isActive) {
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .clip(Radar.PillShape)
                            .clickable(role = Role.Button) { vm.clearFilters() }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Clear all", style = MaterialTheme.typography.labelLarge, color = Radar.Accent)
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }

            // Options scroll; the result button below stays put.
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Radar.ScreenPadding),
            ) {
                Group("Sort by") {
                    SortBy.entries.forEach { s -> RadarChip(s.label, sort == s) { vm.setSort(s) } }
                }
                Group("First seen") {
                    Recency.entries.forEach { r -> RadarChip(r.label, filters.recency == r) { vm.setRecency(r) } }
                    RadarChip("Recently updated", filters.updatedOnly) { vm.setUpdatedOnly(!filters.updatedOnly) }
                }
                Group("Reward") {
                    RewardFilter.entries.forEach { r -> RadarChip(r.label, filters.reward == r) { vm.setReward(r) } }
                }
                Group("Top reward at least") {
                    MIN_REWARD_STEPS.forEach { step ->
                        RadarChip(if (step == 0L) "Any" else "%,d+".format(step), filters.minReward == step) {
                            vm.setMinReward(step)
                        }
                    }
                }
                Group("In scope") {
                    ScopeType.entries.forEach { t -> RadarChip(t.label, t in filters.scopeTypes) { vm.toggleScopeType(t) } }
                    RadarChip("Web3 contracts", filters.web3Only) { vm.setWeb3Only(!filters.web3Only) }
                }
                Group("Platforms") {
                    platforms.forEach { p -> RadarChip(platformName(p), p in filters.platforms) { vm.togglePlatform(p) } }
                }
                Spacer(Modifier.height(20.dp))
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Radar.ScreenPadding, vertical = 12.dp)
            ) {
                PrimaryButton("Show %,d programs".format(results.size), onClick = onDismiss)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, chips: @Composable () -> Unit) {
    Spacer(Modifier.height(20.dp))
    SectionLabel(title)
    Spacer(Modifier.height(10.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { chips() }
}
