package com.forsyth.novm

import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Mirrors [SavedStateRegistryOwner], but for non config state
 */
interface NonConfigStateRegistryOwner : LifecycleOwner {
    val nonConfigStateRegistry: NonConfigStateRegistry
}

@JvmName("get")
fun View.findViewTreeNonConfigStateRegistryOwner(): NonConfigStateRegistryOwner? {
    return generateSequence(this) { view ->
        view.parent as? View
    }.mapNotNull { view ->
        view.getTag(com.forsyth.novm.runtime.R.id.view_tree_non_config_state_registry_owner) as? NonConfigStateRegistryOwner
    }.firstOrNull()
}

@JvmName("set")
fun View.setViewTreeNonConfigStateRegistryOwner(owner: NonConfigStateRegistryOwner?) {
    setTag(com.forsyth.novm.runtime.R.id.view_tree_non_config_state_registry_owner, owner)
}