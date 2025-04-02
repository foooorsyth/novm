package com.forsyth.novm

import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle

/**
 * Mirrors [androidx.savedstate.SavedStateRegistryController], but for non config state
 */
class NonConfigStateRegistryController private constructor(private val owner: NonConfigStateRegistryOwner) {

    private var attached = false

    @MainThread
    fun performAttach() {
        val lifecycle = owner.lifecycle
        check(lifecycle.currentState == Lifecycle.State.INITIALIZED) {
            ("Restarter must be created only during owner's initialization stage")
        }
        lifecycle.addObserver(NonConfigRecreator(owner))
        owner.nonConfigStateRegistry.performAttach(lifecycle)
        attached = true
    }

    @MainThread
    fun performRestore(nonConfigState: MutableMap<String, MutableMap<String, Any?>?>?) {
        if (!attached) {
            performAttach()
        }
        val lifecycle = owner.lifecycle
        check(!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            ("performRestore cannot be called when owner is ${lifecycle.currentState}")
        }
        owner.nonConfigStateRegistry.performRestore(nonConfigState)
    }

    @MainThread
    fun performSave(outState: MutableMap<String, MutableMap<String, Any?>?>) {
        owner.nonConfigStateRegistry.performSave(outState)
    }

    companion object {
        @JvmStatic
        fun create(owner: NonConfigStateRegistryOwner): NonConfigStateRegistryController {
            return NonConfigStateRegistryController(owner)
        }
    }
}