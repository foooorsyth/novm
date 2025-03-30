package com.forsyth.novm

import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Mirrors [SavedStateRegistryOwner], but for non config state
 */
interface NonConfigStateRegistryOwner : LifecycleOwner {
    val nonConfigStateRegistry: NonConfigStateRegistry
}