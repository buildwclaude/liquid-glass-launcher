package com.wheelgallery.app

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

// The iOS-style picker wheel: three columns (day / month / year), each an
// endlessly looping list that snaps to the row under the center pill and
// ticks the vibration motor as rows pass — like a mechanical dial.

private val ROW_HEIGHT = 44.dp
private const val VISIBLE_ROWS = 5
private const val LOOPS = 1000 // enough repeats to feel infinite

private val MONTHS = listOf(
    "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
    "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
)

@Composable
private fun WheelColumn(
    values: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { ROW_HEIGHT.toPx() }
    val view = LocalView.current

    val total = values.size * LOOPS
    val start = values.size * (LOOPS / 2) + selectedIndex
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    // Which row is currently sitting under the center pill.
    val centered by remember {
        derivedStateOf {
            val extra = if (state.firstVisibleItemScrollOffset > rowHeightPx / 2) 1 else 0
            state.firstVisibleItemIndex + extra
        }
    }

    LaunchedEffect(centered) {
        // The mechanical tick, fired once per row passed.
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    // Report the selection only once the wheel has settled, so the photo
    // grid re-filters exactly once per spin instead of on every tick —
    // that constant re-filtering was the main cause of dropped frames.
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            onSelected(((centered % values.size) + values.size) % values.size)
        }
    }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        modifier = modifier.height(ROW_HEIGHT * VISIBLE_ROWS),
        contentPadding = PaddingValues(vertical = ROW_HEIGHT * ((VISIBLE_ROWS - 1) / 2)),
    ) {
        items(total) { idx ->
            val isCenter = idx == centered
            Box(
                Modifier
                    .height(ROW_HEIGHT)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = values[idx % values.size],
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCenter) Color.White else Color(0xFF888888),
                )
            }
        }
    }
}

@Composable
fun DateWheel(
    day: Int,
    month: Int,
    year: Int,
    years: List<Int>,
    onDayChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    onYearChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val daysInMonth = remember(month, year) { YearMonth.of(year, month).lengthOfMonth() }
    val days = remember(daysInMonth) { (1..daysInMonth).map { "%02d".format(it) } }
    val yearLabels = remember(years) { years.map { it.toString() } }

    Box(modifier.fillMaxWidth()) {
        // the selection pill behind the center row
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(ROW_HEIGHT)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.10f))
        )

        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            key(days.size) {
                WheelColumn(
                    values = days,
                    selectedIndex = (day - 1).coerceIn(0, days.size - 1),
                    onSelected = { onDayChange(it + 1) },
                    modifier = Modifier.weight(1f),
                )
            }
            WheelColumn(
                values = MONTHS,
                selectedIndex = month - 1,
                onSelected = { onMonthChange(it + 1) },
                modifier = Modifier.weight(1f),
            )
            key(yearLabels.size) {
                WheelColumn(
                    values = yearLabels,
                    selectedIndex = years.indexOf(year).coerceAtLeast(0),
                    onSelected = { onYearChange(years[it]) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // fade masks top and bottom, for the "drum" depth effect
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(ROW_HEIGHT * 1.5f)
                .background(
                    Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
                )
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(ROW_HEIGHT * 1.5f)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                )
        )
    }
}
