package com.forsyth.novm

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.remember


@Composable
inline fun <T> retain(vararg across: StateDestroyingEvent,
                      key1: Any?,
                      crossinline calculation: @DisallowComposableCalls () -> T): T {
    val activity = LocalActivity.current
    // TODO
    // mark retain (or just use remember directly)
    // w/ @Retain(CONFIG_CHANGE) ?
    // what can be done at build time?
    // if composable is reusable, can't find Activity class
    // at build time -- only runtime.
    // If we can only find it at runtime, do we need a bag of
    // `Any?`s in our generated StateHolder to hold arbitrary states
    // declared in compose context?
    return remember(key1, calculation)
}

@Composable
inline fun <T> retain(vararg across: StateDestroyingEvent,
                      key1: Any?,
                      key2: Any?,
                      crossinline calculation: @DisallowComposableCalls () -> T): T {
    return remember(key1, key2, calculation)
}

@Composable
inline fun <T> retain(vararg across: StateDestroyingEvent,
                      key1: Any?,
                      key2: Any?,
                      key3: Any?,
                      crossinline calculation: @DisallowComposableCalls () -> T): T {
    return remember(key1, key2, key3, calculation)
}

@Composable
inline fun <T> retain(vararg across: StateDestroyingEvent, crossinline calculation: @DisallowComposableCalls () -> T): T {
    return remember(calculation)
}
