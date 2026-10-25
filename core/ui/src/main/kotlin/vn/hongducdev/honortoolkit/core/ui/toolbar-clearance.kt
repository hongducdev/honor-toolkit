package vn.hongducdev.honortoolkit.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Space a scrolling screen must leave at its end so the last row can rise above the floating toolbar. */
val LocalToolbarClearance = staticCompositionLocalOf<Dp> { 0.dp }
