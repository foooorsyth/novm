package com.forsyth.novm

import android.annotation.SuppressLint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel

// TODO investigate nav3
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
    private var lifecycleRegistry = LifecycleRegistry(this)
    private var _nonConfigStateRegistry = NonConfigStateRegistry()
    private var nonConfigRegistryController = NonConfigStateRegistryController.create(this)
    private var nonConfigRegistryState: MutableMap<String, Any?>? = null
    private var ignoreFirstLifecycleOnCreate = true

    override val nonConfigStateRegistry: NonConfigStateRegistry
        get() = _nonConfigStateRegistry

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    /*
        TODO constraints
        unlike an activity, this viewmodel is only created once

        registry can only be restored once. attempts to restore after already restoring will fail
        registry must be attached before CREATE
        registry must be restored before consumption happens (which happens on line 101 in RetainCompsose when
        DisposableNonConfigStateRegistryCompose is initialized
     */

    fun simulateConfigDidChange() {
        if (ignoreFirstLifecycleOnCreate) {
            // TODO
            // This is a hack because the lifecycle effect will always fire,
            // even when we've first initialized the VM and we don't want it to
            // We still need to consider mutiple retained variables (right now we have just 1)
            ignoreFirstLifecycleOnCreate = false
            return
        }
        lifecycleRegistry = LifecycleRegistry(this)
        _nonConfigStateRegistry = NonConfigStateRegistry()
        nonConfigRegistryController = NonConfigStateRegistryController.create(this)
        // nonConfigRegistryState does NOT get set to null
        // it would've been kept via retainedState.nonConfigRegistryState
        // all of the others, though, need to be reset as if the VM
        // object were being totally recreated
        initImpl()
    }

    init {
        initImpl()
    }

    private fun initImpl() {
        this.lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
        this.nonConfigRegistryController.performAttach()
        nonConfigRegistryController.performRestore(nonConfigRegistryState)
        // AFTER we restore, we can reset our held state
        // sh.nonConfigRegistryState is always null in onRCNCI
        // it is then set to an empty map before performSave
        nonConfigRegistryState = null
        this.lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun simulateConfigWillChange() {
        if (nonConfigRegistryState == null) {
            nonConfigRegistryState = mutableMapOf()
        }
        nonConfigRegistryController.performSave(nonConfigRegistryState!!)
        // TODO something does get saved here but it's the default init() value (1), not 5
    }


    override fun onCleared() {
        // canary for vm leak
        this.lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}