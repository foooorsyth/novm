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
    private val _nonConfigStateRegistry = NonConfigStateRegistry()
    private val nonConfigRegistryController = NonConfigStateRegistryController.create(this)
    private var nonConfigRegistryState: MutableMap<String, Any?> = mutableMapOf()

    init {
        this.lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
        this.nonConfigRegistryController.performAttach()
    }

    override val nonConfigStateRegistry: NonConfigStateRegistry
        get() = _nonConfigStateRegistry

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    fun performRestore() {
        this.lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        nonConfigRegistryController.performRestore(nonConfigRegistryState)
        nonConfigRegistryState = mutableMapOf() // reset it here?
    }

    override fun onCleared() {
        nonConfigRegistryController.performSave(nonConfigRegistryState)
    }
}