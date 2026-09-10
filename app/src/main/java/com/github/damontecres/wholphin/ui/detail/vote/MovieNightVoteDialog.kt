package com.github.damontecres.wholphin.ui.detail.vote

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.cards.ItemCardImage
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.enableMarquee
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.util.RankedBallotTally
import com.github.damontecres.wholphin.util.pickRandom
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.extensions.ticks
import java.util.UUID

// The ballot buttons hug their text, but the result screens pair two
// options side by side, so each pair takes a fixed width to match, as
// UserList.kt does. Labels need fillMaxWidth + textAlign to centre
// within it. Without this "Play" is short enough that the default
// CircleShape renders it as an actual circle next to a wider "Cancel".
private val ResultButtonWidth = 100.dp
private val TieBreakerButtonWidth = 200.dp

// Vertical gap between rows, on the ballot and in the tied-films list.
private val RankingRowSpacing = 8.dp

// The same height PlaylistDetails.kt gives every non-audio PlaylistItem.
// Required: unconstrained, a row is as tall as its poster (~150dp+).
private val ItemRowHeight = 80.dp

private sealed interface VotePhase {
    data object Ranking : VotePhase

    data class Result(
        val outcome: RankedBallotTally.Result<UUID>,
    ) : VotePhase
}

/**
 * Movie-night ranked-choice voting, entirely local to this dialog: select
 * a movie to pick it up, then D-pad up/down moves it live (swapping with
 * its neighbor on every press, like a drag with no mouse), select again
 * to drop it in place. Rows are keyed on the movie's own id (LazyColumn's
 * `key` param) so Compose tracks each row by identity rather than list
 * position - meaning focus stays attached to the movie itself as it
 * moves. Ballots are tallied via [RankedBallotTally]; a tie resolves by
 * picking among the tied films, so this always ends on one winner.
 *
 * No "skip the vote" action here on purpose: the playlist screen's own
 * "Random Pick" button already does exactly that, over the same items.
 *
 * [candidates] must not repeat a movie: rows are keyed on movie id, and
 * LazyColumn requires those keys to be unique.
 */
@Composable
fun MovieNightVoteDialog(
    candidates: List<BaseItem>,
    onDismissRequest: () -> Unit,
    onWinnerSelected: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var ballot by remember { mutableStateOf(candidates) }
    var ballots by remember { mutableStateOf(emptyList<List<UUID>>()) }
    var voterNumber by remember { mutableStateOf(1) }
    var phase by remember { mutableStateOf<VotePhase>(VotePhase.Ranking) }
    var grabbedId by remember { mutableStateOf<UUID?>(null) }
    val submitButtonFocusRequester = remember { FocusRequester() }
    val rankingListState = rememberLazyListState()

    // LazyColumn only auto-scrolls to a focused item on a focus CHANGE, and
    // a grabbed row stays focused throughout its move, so it would otherwise
    // travel out of view. Follow it explicitly.
    LaunchedEffect(ballot, grabbedId) {
        val id = grabbedId ?: return@LaunchedEffect
        val index = ballot.indexOfFirst { it.id == id }
        if (index != -1) {
            rankingListState.animateScrollToItem(index)
        }
    }

    fun byId(id: UUID): BaseItem? = candidates.firstOrNull { it.id == id }

    fun onSelectRow(index: Int) {
        val clickedId = ballot[index].id
        grabbedId = if (grabbedId == clickedId) null else clickedId
    }

    fun moveGrabbed(delta: Int): Boolean {
        val id = grabbedId ?: return false
        val fromIndex = ballot.indexOfFirst { it.id == id }
        val toIndex = fromIndex + delta
        if (fromIndex == -1 || toIndex !in ballot.indices) return false
        ballot =
            ballot.toMutableList().apply {
                val moving = removeAt(fromIndex)
                add(toIndex, moving)
            }
        return true
    }

    fun submitBallot() {
        ballots = ballots + listOf(ballot.map { it.id })
        ballot = candidates
        voterNumber += 1
        grabbedId = null
    }

    // Sized as a fraction of the screen so it tracks the real playlist
    // screen's proportions at any resolution. usePlatformDefaultWidth must
    // be false or the window caps the width before fillMaxWidth is measured.
    BasicDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // Chrome is kept tight on purpose: the list shows a whole number
            // of 80dp rows, so dp spent here can cost a whole film.
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                modifier
                    .fillMaxWidth(0.65f)
                    // Only the ballot claims a fixed share of the screen, so
                    // its list has room to grow into. Bounding the whole
                    // dialog rather than just the list is what keeps the
                    // buttons on screen, and it stays below 1f to clear
                    // overscan. The result screens are short and wrap their
                    // content instead, rather than leaving a tall empty gap
                    // under the buttons.
                    .ifElse(phase is VotePhase.Ranking, Modifier.fillMaxHeight(0.9f))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            when (val currentPhase = phase) {
                is VotePhase.Ranking -> {
                    // Whose turn it is and what select will do, on one line.
                    // State-dependent on purpose: the wording is what tells
                    // you whether you are picking a film up or putting it down.
                    Text(
                        text =
                            if (grabbedId == null) {
                                stringResource(R.string.movie_night_vote_prompt_grab, voterNumber)
                            } else {
                                stringResource(R.string.movie_night_vote_prompt_drop, voterNumber)
                            },
                        style = MaterialTheme.typography.titleMedium,
                        // Needs the full width to left-align within, since the
                        // Column centres its children. Keep the wording short:
                        // a wrap here costs a film off the bottom.
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(
                        state = rankingListState,
                        verticalArrangement = Arrangement.spacedBy(RankingRowSpacing),
                        // Takes the space left after the prompt and buttons,
                        // rather than a fixed height that could overflow them.
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        itemsIndexed(ballot, key = { _, item -> item.id }) { index, item ->
                            RankingRow(
                                item = item,
                                position = index + 1,
                                grabbed = grabbedId == item.id,
                                onClick = { onSelectRow(index) },
                                onMove = ::moveGrabbed,
                                downFocusRequester =
                                    if (index == ballot.lastIndex) submitButtonFocusRequester else null,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { submitBallot() },
                            modifier = Modifier.focusRequester(submitButtonFocusRequester),
                        ) {
                            Text(text = stringResource(R.string.movie_night_vote_submit_ballot))
                        }
                        if (ballots.isNotEmpty()) {
                            Button(
                                onClick = {
                                    phase = VotePhase.Result(RankedBallotTally.tally(ballots))
                                },
                            ) {
                                Text(text = stringResource(R.string.movie_night_vote_finish))
                            }
                        }
                    }
                }

                is VotePhase.Result -> {
                    val winnerId = currentPhase.outcome.winner
                    val winner = winnerId?.let(::byId)
                    if (winner != null) {
                        Text(
                            text = stringResource(R.string.movie_night_vote_winner),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        // Shown as the same row as on the ballot. Selecting it
                        // plays it, so the row is not a dead focus stop.
                        BallotItem(
                            item = winner,
                            onClick = { onWinnerSelected(winner) },
                            modifier = Modifier.height(ItemRowHeight),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onWinnerSelected(winner) },
                                modifier = Modifier.width(ResultButtonWidth),
                            ) {
                                Text(
                                    text = stringResource(R.string.play),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Button(
                                onClick = onDismissRequest,
                                modifier = Modifier.width(ResultButtonWidth),
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    } else {
                        val tied = currentPhase.outcome.tiedWinners.mapNotNull(::byId)
                        Text(
                            text = stringResource(R.string.movie_night_vote_tied),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        // One row per tied film. Selecting one settles the tie
                        // in its favour; the random button is for when nobody
                        // wants to be the one who chose.
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(RankingRowSpacing),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            items(tied, key = { it.id }) { item ->
                                BallotItem(
                                    item = item,
                                    onClick = { onWinnerSelected(item) },
                                    modifier = Modifier.height(ItemRowHeight),
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { tied.pickRandom()?.let(onWinnerSelected) },
                                modifier = Modifier.width(TieBreakerButtonWidth),
                            ) {
                                Text(
                                    text = stringResource(R.string.movie_night_vote_pick_randomly),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Button(
                                onClick = onDismissRequest,
                                modifier = Modifier.width(TieBreakerButtonWidth),
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Fixed width for the position-number column so numbers line up across
// rows regardless of digit count (e.g. "1." vs "10.").
private val RankingPositionColumnWidth = 32.dp

// Reserved whether or not the row is currently grabbed, so picking a
// movie up doesn't shift the whole row sideways.
private val RankingGrabIndicatorWidth = 24.dp

/**
 * One rankable movie on the ballot.
 *
 * The position number is drawn here rather than inside [BallotItem]:
 * these numbers mark fixed places that films move between, so they must
 * stay put while the rows reorder.
 */
@Composable
private fun RankingRow(
    item: BaseItem,
    position: Int,
    grabbed: Boolean,
    onClick: () -> Unit,
    onMove: (delta: Int) -> Boolean,
    modifier: Modifier = Modifier,
    downFocusRequester: FocusRequester? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .height(ItemRowHeight),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.width(RankingGrabIndicatorWidth),
        ) {
            if (grabbed) {
                Text(text = stringResource(R.string.fa_check), fontFamily = FontAwesome)
            }
        }
        Text(
            text = "$position.",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(RankingPositionColumnWidth),
        )
        BallotItem(
            item = item,
            onClick = onClick,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .let { rowModifier ->
                        if (downFocusRequester != null) {
                            rowModifier.focusProperties { down = downFocusRequester }
                        } else {
                            rowModifier
                        }
                    }.onKeyEvent { event ->
                        if (!grabbed || event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionUp -> {
                                    onMove(-1)
                                    true
                                }

                                Key.DirectionDown -> {
                                    onMove(1)
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        }
                    },
        )
    }
}

// Matches the image width PlaylistItem uses, so a film looks the same on
// the ballot as on the playlist it came from.
private val BallotImageWidth = 160.dp

/**
 * One film as shown on the ballot and the result screens: image, title,
 * year and runtime.
 *
 * A plain [ListItem] rather than the playlist's own row, which is an
 * editing row with move and context-menu controls a ballot has no use for.
 * The image is the shared [ItemCardImage], set up the way the playlist row
 * sets it up. [modifier] lands on the focusable item itself, so callers can
 * attach focus and key handling to it directly.
 */
@Composable
private fun BallotItem(
    item: BaseItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val density = LocalDensity.current
    val resources = LocalResources.current
    val imageWidthPx = remember(density) { with(density) { BallotImageWidth.roundToPx() } }
    // Rows are wider than tall, so prefer the landscape thumb when there is one.
    val imageType =
        remember(item) {
            if (ImageType.THUMB in item.data.imageTags.orEmpty()) {
                ImageType.THUMB
            } else {
                ImageType.PRIMARY
            }
        }
    val runtime =
        remember(item, resources) {
            item
                .data
                .runTimeTicks
                ?.ticks
                ?.roundMinutes
                ?.let { resources.formatDuration(it) }
        }
    ListItem(
        selected = false,
        onClick = onClick,
        interactionSource = interactionSource,
        headlineContent = {
            Text(
                text = item.title ?: "",
                modifier = Modifier.enableMarquee(focused),
            )
        },
        supportingContent = {
            Text(
                text = item.subtitle ?: "",
                modifier = Modifier.enableMarquee(focused),
            )
        },
        trailingContent = { runtime?.let { Text(text = it) } },
        leadingContent = {
            ItemCardImage(
                item = item,
                name = item.name,
                imageType = imageType,
                showOverlay = true,
                favorite = item.data.userData?.isFavorite ?: false,
                watched = item.data.userData?.played ?: false,
                unwatchedCount = item.data.userData?.unplayedItemCount ?: -1,
                watchedPercent = 0.0,
                numberOfVersions = item.data.mediaSourceCount ?: 0,
                modifier = Modifier.width(BallotImageWidth),
                useFallbackText = false,
                fillWidth = imageWidthPx,
            )
        },
        modifier = modifier,
    )
}
