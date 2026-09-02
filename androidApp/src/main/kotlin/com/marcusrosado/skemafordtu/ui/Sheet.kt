package com.marcusrosado.skemafordtu.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * The panel every sheet in the app rises out of, which is Material's own
 * modal bottom sheet. The scrim, the slide up and away, the drag down that
 * dismisses, the back gesture and the predictive back animation all come
 * with it, and the corners are the theme's extra large squircle. The jump
 * picker and the calendar's settings are both this with different content,
 * which is what makes them arrive and leave as the same object.
 *
 * The caller composes it when it is wanted and drops it when it is done.
 * Material plays the slide in on its own and calls [onDismiss] only once the
 * slide out has finished, so nothing is ever removed mid movement. The
 * content is handed a close of its own that hides the sheet first and tells
 * the caller afterwards, so a pick made inside lands while the sheet is still
 * sliding off it rather than after a blink.
 *
 * Skips the half open state: both sheets are as tall as their content, and a
 * content sized sheet stopping halfway would hide half of what it is for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch {
            state.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.medium, end = Space.medium, bottom = Space.large),
        ) {
            content(close)
        }
    }
}
