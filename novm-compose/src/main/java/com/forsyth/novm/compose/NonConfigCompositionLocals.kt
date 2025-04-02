package com.forsyth.novm.compose

import androidx.compose.runtime.staticCompositionLocalOf
import com.forsyth.novm.NonConfigStateRegistryOwner

val LocalNonConfigStateRegistry = staticCompositionLocalOf<ComposeNonConfigStateRegistry?> { null }
val LocalNonConfigStateRegistryOwner = staticCompositionLocalOf<NonConfigStateRegistryOwner> {
    error("CompositionLocal LocalSavedStateRegistryOwner not present")
}