package com.forsyth.novm

import android.annotation.SuppressLint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel

/**
 * This only exists to deal with NavBackStackEntry, which is final
 * We could get cute and modify NBSE's bytecode, but probably not worth the hassle or build perf hit
 * Instead, we just use NBSE's existing ViewModelStore
 *
 * Giving a ViewModel a Lifecycle is blasphemous. We do it here because it's a requirement of the
 * NonConfigRegistry
 *
 * Using a ViewModel to defeat the ViewModel is also blasphemous. Bytecode modding is more fun
 * but we'll take the easier and safer path.
 */
class NonConfigViewModel : ViewModel(), NonConfigStateRegistryOwner {
    @SuppressLint("StaticFieldLeak") // lifecycleowner ref (this) is weakref
    private val lifecycleRegistry = LifecycleRegistry(this)
    private var _nonConfigStateRegistry = NonConfigStateRegistry()
    private val nonConfigRegistryController = NonConfigStateRegistryController.create(this)
    private var nonConfigRegistryState: MutableMap<String, Any?> = mutableMapOf()

    /*
        TODO constraints
        unlike an activity, this viewmodel is only created once

        registry can only be restored once. attempts to restore after already restoring will fail
        registry must be attached before CREATE
        registry must be restored before consumption happens (which happens on line 101 in RetainCompsose when
        DisposableNonConfigStateRegistryCompose is initialized
     */

    init {
        this.lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
        this.nonConfigRegistryController.performAttach()
        nonConfigRegistryController.performRestore(nonConfigRegistryState)
        this.lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override val nonConfigStateRegistry: NonConfigStateRegistry
        get() = _nonConfigStateRegistry

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    fun performSave() {
        _nonConfigStateRegistry = NonConfigStateRegistry()
        nonConfigRegistryState = mutableMapOf()
        nonConfigRegistryController.performSave(nonConfigRegistryState)
    }

    override fun onCleared() {
        // canary for vm leak
        this.lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}