package com.bountyradar.app

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bountyradar.app.data.Program
import com.bountyradar.app.data.ProgramItem
import com.bountyradar.app.ui.BottomBar
import com.bountyradar.app.ui.components.EmptyState
import com.bountyradar.app.ui.components.Metric
import com.bountyradar.app.ui.components.Panel
import com.bountyradar.app.ui.components.PrimaryButton
import com.bountyradar.app.ui.components.ProgramCard
import com.bountyradar.app.ui.components.RadarChip
import com.bountyradar.app.ui.components.RadarLogo
import com.bountyradar.app.ui.components.ScreenTopBar
import com.bountyradar.app.ui.components.SearchField
import com.bountyradar.app.ui.components.SecondaryButton
import com.bountyradar.app.ui.components.SkeletonCard
import com.bountyradar.app.ui.components.Tag
import com.bountyradar.app.ui.screens.FeedHeader
import com.bountyradar.app.ui.screens.FilterButton
import com.bountyradar.app.ui.screens.NewsDetailContent
import com.bountyradar.app.ui.screens.ProgramDetailContent
import com.bountyradar.app.ui.theme.BountyRadarTheme
import com.bountyradar.app.ui.theme.Radar
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bountyradar.app.data.NewsItem
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

/**
 * Renders the real UI components to PNG on the JVM so layout can be checked
 * without a device. Output: app/build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi", application = Application::class)
class ScreenshotTest {

    @get:Rule val rule = createComposeRule()

    /** Freeze the clock: several components animate forever and would never go idle. */
    private fun shoot(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent(content)
        rule.mainClock.advanceTimeBy(900)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun program(
        name: String, platform: String, bounty: Boolean, reward: String,
        scope: List<String>, ageHours: Long, tags: List<String> = emptyList(),
    ) = ProgramItem(
        Program(
            docId = name, platform = platform, name = name, url = "https://example.com",
            bounty = bounty, rewardRange = reward, scope = scope, tags = tags,
            firstSeen = Instant.now().minusSeconds(ageHours * 3600).atOffset(ZoneOffset.UTC).toString(),
        )
    )

    private val samples = listOf(
        program("Slack", "hackerone", true, "$100–$10,000", listOf("*.slack.com", "api.slack.com", "slack.com"), 3),
        program(
            "Axel Springer SE Vulnerability Disclosure Program with a very long name", "intigriti",
            false, "", listOf("axelspringer.com"), 400,
        ),
        program("Raydium", "immunefi", true, "up to $505,000", List(42) { "contract$it" }, 900, listOf("web3")),
    )

    @Composable
    private fun Screen(content: @Composable () -> Unit) {
        // Same wrapper as MainActivity, so default text colour matches the app.
        BountyRadarTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } }
    }

    @Test
    fun feed() = shoot("feed") {
        Screen {
            Column {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeedHeader(newSinceVisit = 12, total = 1801, platforms = 11)
                    Row(
                        Modifier.padding(horizontal = Radar.ScreenPadding),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        SearchField("", {}, "Search programs or scope", Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        FilterButton(2) {}
                    }
                    Row(
                        Modifier.padding(horizontal = Radar.ScreenPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadarChip("New today", true) {}
                        RadarChip("Paid", false) {}
                        RadarChip("Wildcards", false) {}
                    }
                    samples.forEachIndexed { i, item ->
                        ProgramCard(item, bookmarked = i == 0, onClick = {}, onBookmark = {},
                            modifier = Modifier.padding(horizontal = Radar.ScreenPadding))
                    }
                    Box(Modifier.padding(horizontal = Radar.ScreenPadding)) { SkeletonCard() }
                    Spacer(Modifier.height(6.dp))
                }
                BottomBar(isSelected = { it == "feed" }, onSelect = {})
            }
        }
    }

    @Test
    fun parts() = shoot("parts") {
        Screen {
            Column(Modifier.padding(bottom = 20.dp)) {
                ScreenTopBar("Program", onBack = {})
                Column(
                    Modifier.padding(horizontal = Radar.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadarLogo(64.dp); RadarLogo(40.dp); RadarLogo(24.dp)
                    }
                    Panel {
                        Metric("$12,240.50", "Balance", accent = true)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Metric("7", "Private invites", Modifier.weight(1f))
                            Metric("23", "Recent reports", Modifier.weight(1f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tag("New", filled = true); Tag("Updated"); Tag("Wildcard", Radar.Muted)
                        Tag("Exploited", Radar.Danger); Tag("High", Radar.Warn)
                    }
                    PrimaryButton("Open program", onClick = {}, arrow = true)
                    PrimaryButton("Connect HackerOne", onClick = {}, enabled = false)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Refresh", {}, Modifier.weight(1f))
                        SecondaryButton("Disconnect", {}, Modifier.weight(1f), color = Radar.Danger)
                    }
                    EmptyState("Nothing matches", "No program fits this search and these filters.",
                        actionLabel = "Clear filters", onAction = {})
                }
            }
        }
    }

    @Test
    fun detail() = shoot("detail") {
        Screen {
            ProgramDetailContent(
                item = samples[0], bookmarked = true, note = "Check the OAuth flow",
                onToggleBookmark = {}, onSaveNote = {}, onBack = {},
            )
        }
    }

    @Test
    fun article() = shoot("article") {
        Screen {
            NewsDetailContent(
                NewsItem(
                    id = "a1", kind = "zeroday", source = "ZDI upcoming",
                    title = "ZDI-CAN-34578: LiteLLM", url = "https://example.com", date = "2026-10-02",
                    severity = "high",
                    summary = "A CVSS 8.8 vulnerability was reported to the vendor on 2026-10-02 and has no public advisory or CVE yet. " +
                        "Vendors get 120 days before details are published.",
                    tags = listOf("LiteLLM", "CVSS 8.8", "AV:N/AC:L/PR:L"),
                ),
                onBack = {},
            )
        }
    }

    /** Tap a card inside a NavHost wired like the app's, and land on the detail screen. */
    @Test
    fun tapOpensDetail() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Screen {
                val nav = rememberNavController()
                val push = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                NavHost(
                    nav, "feed",
                    enterTransition = { fadeIn(tween(200, easing = FastOutSlowInEasing)) },
                    exitTransition = { fadeOut(tween(120)) },
                    popEnterTransition = { fadeIn(tween(200, easing = FastOutSlowInEasing)) },
                    popExitTransition = { fadeOut(tween(120)) },
                ) {
                    composable("feed") {
                        ProgramCard(samples[0], false, onClick = { nav.navigate("detail/$it") }, onBookmark = {})
                    }
                    composable(
                        "detail/{docId}",
                        enterTransition = { slideInHorizontally(push) { it / 5 } + fadeIn(tween(220)) },
                        popExitTransition = { slideOutHorizontally(push) { it / 5 } + fadeOut(tween(160)) },
                    ) { entry ->
                        val id = entry.arguments?.getString("docId").orEmpty()
                        ProgramDetailContent(
                            samples.firstOrNull { it.docId == id }, false, "", {}, {}, { nav.popBackStack() },
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Slack").performClick()
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithText("Open program").assertExists()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/tap_detail.png")
    }
}
